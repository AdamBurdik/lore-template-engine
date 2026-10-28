package me.adamix.lte.definition.template;

import me.adamix.kidl.Cardinality;
import me.adamix.kidl.Kidl;
import me.adamix.kidl.config.DiagnosticsConfig;
import me.adamix.kidl.diagnostic.Diagnostic;
import me.adamix.kidl.diagnostic.FileSpan;
import me.adamix.kidl.error.DiagnosticError;
import me.adamix.kidl.render.AnsiRenderer;
import me.adamix.kidl.render.DiagnosticsRenderer;
import me.adamix.kidl.validator.Context;
import me.adamix.kidl.view.ChildKind;
import me.adamix.kidl.view.TypedCursor;
import me.adamix.lte.LteErrors;
import me.adamix.lte.api.exception.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class TemplateParser {
    private final DiagnosticsConfig diagnosticsConfig;
    private final DiagnosticsRenderer renderer;
    private final Kidl kidl;

    public TemplateParser() {
        this.diagnosticsConfig = DiagnosticsConfig.builder().build();
        this.renderer = new AnsiRenderer(diagnosticsConfig);
        this.kidl = Kidl.builder()
                .diagnosticsConfig(diagnosticsConfig)
                .renderer((diagnostic, sources) -> null)
                .standardExceptionTranslation()
                .build();
    }

    enum TemplateChild implements ChildKind {
        BLANK("blank", Cardinality.MANY),
        TEXT("text", Cardinality.MANY),
        VAR("var", Cardinality.MANY),
        VARIABLE("variable", Cardinality.MANY),
        GROUP("group", Cardinality.MANY),
        insert("insert", Cardinality.MANY),
        SECTION("section", Cardinality.MANY);

        private final String id;
        private final Cardinality cardinality;

        TemplateChild(String id, Cardinality cardinality) {
            this.id = id;
            this.cardinality = cardinality;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Cardinality cardinality() {
            return cardinality;
        }
    }

    public @NotNull List<LoreTemplateDefinition> parseFile(@NotNull Path path) throws IOException {
        List<LoreTemplateDefinition> all = new LinkedList<>();
        Map<String, FileSpan> spans = new HashMap<>();
        AtomicReference<Context> contextRef = new AtomicReference<>();

        var result = kidl.load(path, (id, ctx, cursor) -> {
            contextRef.set(ctx);

            if (!id.equals("template")) return;

            String templateId = cursor.arguments().nextString();
            if (templateId == null) return;
            Optional<String> extendsId = cursor.properties().optString("extends");
            Optional<String> tooltipStyle = cursor.properties().optString("tooltip-style");

            List<TemplateElement> templateElements = new LinkedList<>();
            Map<String, List<TemplateElement>> sections = new LinkedHashMap<>();

            for (TypedCursor<TemplateChild> child : cursor.children(TemplateChild.class)) {
                templateElements.addAll(parse(child, sections, ctx));
            }

            if (extendsId.isEmpty() && containsInsert(templateElements)) {
                report(
                        ctx,
                        LteErrors.INSERT_WITHOUT_EXTENDS,
                        "template `" + templateId + "` uses `insert` but does not extend another template",
                        cursor.span(),
                        "insert requires an extended template"
                );
                return;
            }

            FileSpan previous = spans.putIfAbsent(templateId, cursor.span());
            if (previous != null) {
                report(
                        ctx,
                        LteErrors.DUPLICATE_TEMPLATE,
                        "template `" + templateId + "` is defined more than once",
                        cursor.span(),
                        "redefined here"
                );
                return;
            }

            var definition = new LoreTemplateDefinition(
                    templateId,
                    templateElements,
                    extendsId,
                    tooltipStyle,
                    sections
            );

            all.add(definition);
        });

        Context context = contextRef.get();
        List<LoreTemplateDefinition> resolved = context == null
                ? List.copyOf(all)
                : resolveExtensions(all, context, spans);

        renderer.sout(
                result.getDiagnostics(),
                Map.of(path.toString(), Files.readString(path).lines().toList())
        );

        if (result.hasErrors()) {
            throw new ConfigurationException("Template configuration contains errors");
        }

        return resolved;
    }

    private @NotNull List<LoreTemplateDefinition> resolveExtensions(
            @NotNull List<LoreTemplateDefinition> templates,
            @NotNull Context ctx,
            @NotNull Map<String, FileSpan> spans
    ) {
        Map<String, LoreTemplateDefinition> byId = new HashMap<>();
        for (LoreTemplateDefinition template : templates) {
            byId.put(template.id(), template);
        }

        Map<String, Resolved> resolved = new HashMap<>();
        Set<String> visiting = new HashSet<>();
        Set<String> failed = new HashSet<>();

        List<LoreTemplateDefinition> result = new ArrayList<>();
        for (LoreTemplateDefinition template : templates) {
            FileSpan span = spans.get(template.id());
            if (span == null) continue;

            Resolved r = resolve(template.id(), byId, resolved, visiting, failed, ctx, spans, span);
            if (r == null) continue;

            result.add(new LoreTemplateDefinition(
                    template.id(),
                    r.elements(),
                    Optional.empty(),
                    r.tooltipStyle(),
                    r.sections()
            ));
        }

        return result;
    }

    private static boolean containsInsert(@NotNull List<TemplateElement> elements) {
        return elements.stream().anyMatch(e -> e instanceof TemplateElement.Insert);
    }

    private @Nullable Resolved resolve(
            @NotNull String id,
            @NotNull Map<String, LoreTemplateDefinition> byId,
            @NotNull Map<String, Resolved> resolved,
            @NotNull Set<String> visiting,
            @NotNull Set<String> failed,
            @NotNull Context ctx,
            @NotNull Map<String, FileSpan> spans,
            @NotNull FileSpan origin
    ) {
        Resolved cached = resolved.get(id);
        if (cached != null) {
            return cached;
        }
        if (failed.contains(id)) {
            return null;
        }

        FileSpan span = spans.getOrDefault(id, origin);

        if (!visiting.add(id)) {
            report(
                    ctx,
                    LteErrors.CIRCULAR_EXTENDS,
                    "circular template extension involving `" + id + "`",
                    span,
                    "circular extension"
            );
            failed.add(id);
            return null;
        }

        try {
            LoreTemplateDefinition template = byId.get(id);
            if (template == null) {
                report(
                        ctx,
                        LteErrors.UNKNOWN_TEMPLATE,
                        "unknown template `" + id + "`",
                        span,
                        "no such template"
                );
                failed.add(id);
                return null;
            }

            Optional<String> tooltipStyle = template.tooltipStyle();   // child's own value wins
            Map<String, List<TemplateElement>> combinedSections = new HashMap<>(template.sections());
            List<TemplateElement> finalElements;

            if (template.extendsId().isPresent()) {
                String parentId = template.extendsId().get();
                Resolved parent = resolve(parentId, byId, resolved, visiting, failed, ctx, spans, span);
                if (parent == null) {
                    failed.add(id);
                    return null;
                }

                if (tooltipStyle.isEmpty()) {
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
                                report(
                                        ctx,
                                        LteErrors.UNKNOWN_SECTION,
                                        "template `" + id + "` inserts unknown section `" + sectionName +
                                                "` from `" + parentId + "`",
                                        span,
                                        "no such section"
                                );
                                failed.add(id);
                                return null;
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

            Resolved out = new Resolved(List.copyOf(finalElements), tooltipStyle, Map.copyOf(combinedSections));
            resolved.put(id, out);
            return out;
        } finally {
            visiting.remove(id);
        }
    }

    private static void report(
            @NotNull Context ctx,
            @NotNull DiagnosticError error,
            @NotNull String message,
            @NotNull FileSpan span,
            @NotNull String label
    ) {
        ctx.addDiagnostic(Diagnostic.of(error, message, span, label));
    }


    private @NotNull List<TemplateElement> parse(
            @NotNull TypedCursor<TemplateChild> cursor,
            @NotNull Map<String, List<TemplateElement>> sections,
            @NotNull Context ctx
    ) {
        return switch (cursor.kind()) {
            case BLANK -> List.of(new TemplateElement.Blank(
                    cursor.properties().optString("collapse-if-empty")
            ));
            case TEXT -> List.of(new TemplateElement.Text(
                    String.join("", cursor.arguments().nextStrings()),
                    cursor.properties().optString("collapse-if-empty"),
                    cursor.span()
            ));
            case VAR, VARIABLE -> {
                String name = cursor.arguments().getString(0);
                if (name == null) yield List.of();

                yield List.of(new TemplateElement.Variable(
                        name,
                        cursor.properties().optString("display"),
                        cursor.properties().optString("prefix"),
                        cursor.properties().optString("suffix"),
                        cursor.span()
                ));
            }
            case GROUP -> {
                String name = cursor.arguments().getString(0);
                if (name == null) yield List.of();

                String each = cursor.properties().getString("each");
                if (each == null) yield List.of();

                String emptyName = cursor.properties().optString("empty").orElse("skip");
                TemplateElement.Group.Empty empty = switch (emptyName.toLowerCase(Locale.ROOT)) {
                    case "skip" -> TemplateElement.Group.Empty.SKIP;
                    case "keep" -> TemplateElement.Group.Empty.KEEP;
                    default -> {
                        report(
                                ctx,
                                Kidl.UNKNOWN,
                                "unknown group empty mode `" + emptyName + "`",
                                cursor.span(),
                                "expected `skip` or `keep`"
                        );
                        yield TemplateElement.Group.Empty.SKIP;
                    }
                };

                yield List.of(new TemplateElement.Group(
                        name,
                        each,
                        empty,
                        cursor.properties().getBoolean("inline", false),
                        cursor.properties().optString("joiner"),
                        cursor.span()
                ));
            }
            case insert -> {
                String name = cursor.arguments().getString(0);
                if (name == null) yield List.of();

                yield List.of(new TemplateElement.Insert(name));
            }
            case SECTION -> {
                String name = cursor.arguments().getString(0);
                if (name == null) yield List.of();

                List<TemplateElement> sectionElements = new ArrayList<>();
                for (TypedCursor<TemplateChild> inner : cursor.children(TemplateChild.class)) {
                    sectionElements.addAll(parse(inner, sections, ctx));
                }

                if (sections.putIfAbsent(name, List.copyOf(sectionElements)) != null) {
                    report(
                            ctx,
                            LteErrors.DUPLICATE_SECTION,
                            "section `" + name + "` is defined more than once",
                            cursor.span(),
                            "redefined here"
                    );
                    yield List.of();
                }

                yield sectionElements;
            }
        };
    }

    private record Resolved(
            @NotNull List<TemplateElement> elements,
            @NotNull Optional<String> tooltipStyle,
            @NotNull Map<String, List<TemplateElement>> sections
    ) {
    }
}
