package me.adamix.lte.definition.template;

import dev.kdl.KdlDocument;
import dev.kdl.KdlNode;
import dev.kdl.parse.KdlParseException;
import dev.kdl.parse.KdlParser;
import me.adamix.lte.exception.TemplateParsingException;
import org.jetbrains.annotations.NotNull;

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

            List<TemplateElement> elements = new ArrayList<>();
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

            for (KdlNode child : node.children()) {
                elements.add(kdlToElement(child));
            }

            templates.add(new LoreTemplateDefinition(id, elements, extendsId));
        }

        return resolveExtensions(templates);
    }

    private @NotNull List<LoreTemplateDefinition> resolveExtensions(@NotNull List<LoreTemplateDefinition> templates) throws TemplateParsingException {
        Map<String, LoreTemplateDefinition> byId = new HashMap<>();
        for (LoreTemplateDefinition template : templates) {
            byId.put(template.id(), template);
        }

        Map<String, List<TemplateElement>> resolved = new HashMap<>();
        Set<String> visiting = new HashSet<>();

        List<LoreTemplateDefinition> result = new ArrayList<>();
        for (LoreTemplateDefinition template : templates) {
            List<TemplateElement> elements = collectElements(template.id(), byId, resolved, visiting);
            result.add(new LoreTemplateDefinition(template.id(), elements, null));
        }

        return result;
    }

    private @NotNull List<TemplateElement> collectElements(
            @NotNull String id,
            @NotNull Map<String, LoreTemplateDefinition> byId,
            @NotNull Map<String, List<TemplateElement>> resolved,
            @NotNull Set<String> visiting
    ) throws TemplateParsingException {
        List<TemplateElement> cached = resolved.get(id);
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

        List<TemplateElement> elements = new ArrayList<>();
        if (template.extendsId() != null) {
            LoreTemplateDefinition parent = byId.get(template.extendsId());
            if (parent == null) {
                visiting.remove(id);
                throw new TemplateParsingException(
                        "Template '" + id + "' extends unknown template '" + template.extendsId() + "'"
                );
            }
            elements.addAll(collectElements(parent.id(), byId, resolved, visiting));
        }
        elements.addAll(template.elements());

        visiting.remove(id);
        resolved.put(id, List.copyOf(elements));
        return elements;
    }

    public @NotNull TemplateElement kdlToElement(@NotNull KdlNode node) throws TemplateParsingException {
        String name = node.name();
        return switch (name) {
            case "blank" -> {
                var collapseIfEmpty = node.getProperty("collapse-if-empty");
                if (collapseIfEmpty.isEmpty()) {
                    yield new TemplateElement.Blank(null);
                }

                if (!collapseIfEmpty.get().isString()) {
                    throw new TemplateParsingException("Blank property 'collapse-if-empty' must be string");
                }

                yield new TemplateElement.Blank((String) collapseIfEmpty.get().value());
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
                    yield new TemplateElement.Text((String) argument.value(), null);
                }

                if (!collapseIfEmpty.get().isString()) {
                    throw new TemplateParsingException("Blank property 'collapse-if-empty' must be string");
                }

                yield new TemplateElement.Text((String) argument.value(), (String) collapseIfEmpty.get().value());
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
                String prefix;
                if (prefixValue.isEmpty()) {
                    prefix = null;
                } else {
                    if (!prefixValue.get().isString()) {
                        throw new TemplateParsingException("Variable prefix must be string");
                    }
                    prefix = (String) prefixValue.get().value();
                }

                var suffixValue = node.getProperty("suffix"); // was "prefix" — bug
                String suffix;
                if (suffixValue.isEmpty()) {
                    suffix = null;
                } else {
                    if (!suffixValue.get().isString()) {
                        throw new TemplateParsingException("Variable suffix must be string");
                    }
                    suffix = (String) suffixValue.get().value();
                }

                yield new TemplateElement.Variable(varName, prefix, suffix);
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
                String emptyRaw = "skip"; // default per format spec
                if (emptyValue.isPresent()) {
                    if (!emptyValue.get().isString()) {
                        throw new TemplateParsingException("Group 'empty' property must be string");
                    }
                    emptyRaw = (String) emptyValue.get().value();
                }

                TemplateElement.Group.Empty empty = switch (emptyRaw) {
                    case "skip" -> new TemplateElement.Group.Empty.Skip();
                    // add Keep / Placeholder cases here once those variants exist
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

                yield new TemplateElement.Group(groupName, each, empty, inline, joiner);
            }
            default -> throw new TemplateParsingException("Unknown template element: " + name);
        };
    }
}
