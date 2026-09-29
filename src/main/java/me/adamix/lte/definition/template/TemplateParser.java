package me.adamix.lte.definition.template;

import dev.kdl.KdlDocument;
import dev.kdl.KdlNode;
import dev.kdl.parse.KdlParseException;
import dev.kdl.parse.KdlParser;
import me.adamix.lte.api.exception.TemplateParsingException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TemplateParser {
    public @NotNull List<LoreTemplateDefinition> parseFile(@NotNull Path path) throws KdlParseException, IOException, TemplateParsingException {
        var parser = KdlParser.v2();
        var document = parser.parse(path);
        return kdlToTemplates(document);
    }

    public @NotNull List<LoreTemplateDefinition> kdlToTemplates(@NotNull KdlDocument document) throws TemplateParsingException {
        List<LoreTemplateDefinition> templates = new ArrayList<>();

        for (KdlNode node : document.nodes()) {
            String name = node.name();
            if (!name.equals("template")) continue;

            if (node.arguments().isEmpty()) {
                throw new TemplateParsingException("Template requires at least one argument to act as an ID");
            }
            var argument = node.arguments().getFirst();
            if (!argument.isString()) {
                throw new TemplateParsingException("Template id must be string");
            }
            String id = (String) argument.value();

            String extendsId = null;
            var extendsProperty = node.getProperty("extends");
            if (extendsProperty.isPresent()) {
                if (!extendsProperty.get().isString()) {
                    throw new TemplateParsingException("Template 'extends' property must be a string");
                }
                extendsId = (String) extendsProperty.get().value();
            }

            String tooltipStyle = null;
            var styleProperty = node.getProperty("tooltip-style");
            if (styleProperty.isPresent()) {
                if (!styleProperty.get().isString()) {
                    throw new TemplateParsingException("Template 'tooltip-style' property must be a string");
                }
                tooltipStyle = (String) styleProperty.get().value();
            }

            Map<String, List<TemplateElement>> sections = new HashMap<>();
            List<TemplateElement> elements = new ArrayList<>();
            for (KdlNode child : node.children()) {
                elements.addAll(kdlToElement(child, sections));
            }

            if (extendsId == null && containsInsert(elements)) {
                throw new TemplateParsingException("Template '" + id + "' uses 'insert' but does not extend anything");
            }

            templates.add(new LoreTemplateDefinition(id, elements, extendsId, tooltipStyle, sections));
        }

        return resolveExtensions(templates);
    }

    private static boolean containsInsert(@NotNull List<TemplateElement> elements) {
        return elements.stream().anyMatch(e -> e instanceof TemplateElement.Insert);
    }

    private @NotNull List<LoreTemplateDefinition> resolveExtensions(@NotNull List<LoreTemplateDefinition> templates) throws TemplateParsingException {
        Map<String, LoreTemplateDefinition> byId = new HashMap<>();
        for (LoreTemplateDefinition template : templates) {
            byId.put(template.id(), template);
        }

        Map<String, Resolved> resolved = new HashMap<>();
        Set<String> visiting = new HashSet<>();

        List<LoreTemplateDefinition> result = new ArrayList<>();
        for (LoreTemplateDefinition template : templates) {
            Resolved r = resolve(template.id(), byId, resolved, visiting);
            result.add(new LoreTemplateDefinition(template.id(), r.elements(), null, r.tooltipStyle(), r.sections()));
        }

        return result;
    }

    private @NotNull Resolved resolve(
            @NotNull String id,
            @NotNull Map<String, LoreTemplateDefinition> byId,
            @NotNull Map<String, Resolved> resolved,
            @NotNull Set<String> visiting
    ) throws TemplateParsingException {
        Resolved cached = resolved.get(id);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(id)) {
            throw new TemplateParsingException("Circular template extension involving '" + id + "'");
        }

        LoreTemplateDefinition template = byId.get(id);
        if (template == null) {
            visiting.remove(id);
            throw new TemplateParsingException("Unknown template '" + id + "'");
        }

        String tooltipStyle = template.tooltipStyle();   // child's own value wins
        Map<String, List<TemplateElement>> combinedSections = new HashMap<>(template.sections());
        List<TemplateElement> finalElements;

        if (template.extendsId() != null) {
            if (!byId.containsKey(template.extendsId())) {
                visiting.remove(id);
                throw new TemplateParsingException(
                        "Template '" + id + "' extends unknown template '" + template.extendsId() + "'"
                );
            }

            Resolved parent = resolve(template.extendsId(), byId, resolved, visiting);

            if (tooltipStyle == null) {
                tooltipStyle = parent.tooltipStyle();
            }

            for (var entry : parent.sections().entrySet()) {
                combinedSections.putIfAbsent(entry.getKey(), entry.getValue());
            }

            if (containsInsert(template.elements())) {
                finalElements = new ArrayList<>();
                for (TemplateElement element : template.elements()) {
                    if (element instanceof TemplateElement.Insert(String sectionName)) {
                        List<TemplateElement> sectionContent = parent.sections().get(sectionName);
                        if (sectionContent == null) {
                            visiting.remove(id);
                            throw new TemplateParsingException(
                                    "Template '" + id + "' inserts unknown section '" + sectionName +
                                            "' from '" + template.extendsId() + "'"
                            );
                        }
                        finalElements.addAll(sectionContent);
                    } else {
                        finalElements.add(element);
                    }
                }
            } else {
                finalElements = new ArrayList<>(parent.elements());
                finalElements.addAll(template.elements());
            }
        } else {
            finalElements = new ArrayList<>(template.elements());
        }

        visiting.remove(id);
        Resolved out = new Resolved(List.copyOf(finalElements), tooltipStyle, Map.copyOf(combinedSections));
        resolved.put(id, out);
        return out;
    }

    public @NotNull List<TemplateElement> kdlToElement(
            @NotNull KdlNode node,
            @NotNull Map<String, List<TemplateElement>> sections
    ) throws TemplateParsingException {
        String name = node.name();
        return switch (name) {
            case "blank" -> {
                var collapseIfEmpty = node.getProperty("collapse-if-empty");
                if (collapseIfEmpty.isEmpty()) {
                    yield List.of(new TemplateElement.Blank(null));
                }
                if (!collapseIfEmpty.get().isString()) {
                    throw new TemplateParsingException("Blank property 'collapse-if-empty' must be string");
                }
                yield List.of(new TemplateElement.Blank((String) collapseIfEmpty.get().value()));
            }
            case "text" -> {
                if (node.arguments().isEmpty()) {
                    throw new TemplateParsingException("Text element requires string argument");
                }
                var argument = node.arguments().getFirst();
                if (!argument.isString()) {
                    throw new TemplateParsingException("Text element requires string argument");
                }
                var collapseIfEmpty = node.getProperty("collapse-if-empty");
                if (collapseIfEmpty.isEmpty()) {
                    yield List.of(new TemplateElement.Text((String) argument.value(), null));
                }
                if (!collapseIfEmpty.get().isString()) {
                    throw new TemplateParsingException("Text property 'collapse-if-empty' must be string");
                }
                yield List.of(new TemplateElement.Text((String) argument.value(), (String) collapseIfEmpty.get().value()));
            }
            case "var", "variable" -> {
                if (node.arguments().isEmpty()) {
                    throw new TemplateParsingException("Variable element requires string argument");
                }
                var argument = node.arguments().getFirst();
                if (!argument.isString()) {
                    throw new TemplateParsingException("Variable element requires string argument");
                }
                String varName = (String) argument.value();

                var prefixValue = node.getProperty("prefix");
                String prefix = null;
                if (prefixValue.isPresent()) {
                    if (!prefixValue.get().isString()) {
                        throw new TemplateParsingException("Variable prefix must be string");
                    }
                    prefix = (String) prefixValue.get().value();
                }

                var suffixValue = node.getProperty("suffix");
                String suffix = null;
                if (suffixValue.isPresent()) {
                    if (!suffixValue.get().isString()) {
                        throw new TemplateParsingException("Variable suffix must be string");
                    }
                    suffix = (String) suffixValue.get().value();
                }

                yield List.of(new TemplateElement.Variable(varName, prefix, suffix));
            }
            case "group" -> {
                if (node.arguments().isEmpty()) {
                    throw new TemplateParsingException("Group element requires string argument");
                }
                var argument = node.arguments().getFirst();
                if (!argument.isString()) {
                    throw new TemplateParsingException("Group element requires string argument");
                }
                String groupName = (String) argument.value();

                var eachValue = node.getProperty("each");
                if (eachValue.isEmpty() || !eachValue.get().isString()) {
                    throw new TemplateParsingException("Group element requires string 'each' property");
                }
                String each = (String) eachValue.get().value();

                var emptyValue = node.getProperty("empty");
                String emptyRaw = "skip";
                if (emptyValue.isPresent()) {
                    if (!emptyValue.get().isString()) {
                        throw new TemplateParsingException("Group 'empty' property must be string");
                    }
                    emptyRaw = (String) emptyValue.get().value();
                }
                TemplateElement.Group.Empty empty = switch (emptyRaw) {
                    case "skip" -> new TemplateElement.Group.Empty.Skip();
                    default -> throw new TemplateParsingException("Unknown group empty mode: " + emptyRaw);
                };

                var inlineValue = node.getProperty("inline");
                boolean inline = false;
                if (inlineValue.isPresent()) {
                    if (!inlineValue.get().isBoolean()) {
                        throw new TemplateParsingException("Group 'inline' property must be boolean");
                    }
                    inline = (boolean) inlineValue.get().value();
                }

                var joinerValue = node.getProperty("joiner");
                String joiner = null;
                if (joinerValue.isPresent()) {
                    if (!joinerValue.get().isString()) {
                        throw new TemplateParsingException("Group 'joiner' property must be string");
                    }
                    joiner = (String) joinerValue.get().value();
                }

                yield List.of(new TemplateElement.Group(groupName, each, empty, inline, joiner));
            }
            case "section" -> {
                if (node.arguments().isEmpty()) {
                    throw new TemplateParsingException("Section element requires string argument");
                }
                var argument = node.arguments().getFirst();
                if (!argument.isString()) {
                    throw new TemplateParsingException("Section element requires string argument");
                }
                String sectionName = (String) argument.value();

                List<TemplateElement> sectionElements = new ArrayList<>();
                for (KdlNode child : node.children()) {
                    sectionElements.addAll(kdlToElement(child, sections));
                }

                if (sections.containsKey(sectionName)) {
                    throw new TemplateParsingException("Duplicate section '" + sectionName + "' in template");
                }
                sections.put(sectionName, List.copyOf(sectionElements));

                yield sectionElements;
            }
            case "insert" -> {
                if (node.arguments().isEmpty()) {
                    throw new TemplateParsingException("Insert element requires string argument");
                }
                var argument = node.arguments().getFirst();
                if (!argument.isString()) {
                    throw new TemplateParsingException("Insert element requires string argument");
                }
                yield List.of(new TemplateElement.Insert((String) argument.value()));
            }
            default -> throw new TemplateParsingException("Unknown template element: " + name);
        };
    }

    private record Resolved(
            @NotNull List<TemplateElement> elements,
            @Nullable String tooltipStyle,
            @NotNull Map<String, List<TemplateElement>> sections
    ) {
    }
}