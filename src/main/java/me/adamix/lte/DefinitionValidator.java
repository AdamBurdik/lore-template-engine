package me.adamix.lte;

import me.adamix.kidl.diagnostic.Diagnostic;
import me.adamix.kidl.diagnostic.FileSpan;
import me.adamix.kidl.error.DiagnosticError;
import me.adamix.kidl.validator.Context;
import me.adamix.lte.definition.group.GroupDefinition;
import me.adamix.lte.definition.template.LoreTemplateDefinition;
import me.adamix.lte.definition.template.TemplateElement;
import me.adamix.lte.definition.transformer.TransformerDefinition;
import me.adamix.lte.definition.variable.VariableDefinition;
import me.adamix.lte.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DefinitionValidator {
    private static final Map<String, Set<String>> BUILTIN_STRING_FIELDS = Map.of(
            "enchantments", Set.of("ench_id", "ench_level", "ench_level_roman"),
            "attributes", Set.of("attr_value", "attr_slot", "attr_color")
    );
    private static final Map<String, Set<String>> BUILTIN_FIELDS = Map.of(
            "enchantments", Set.of("ench_id", "ench_level", "ench_level_roman", "ench_name"),
            "attributes", Set.of("attr_value", "attr_slot", "attr_color", "attr_name")
    );

    private DefinitionValidator() {
    }

    public static @NotNull List<Diagnostic> validate(
            @NotNull Registry<TransformerDefinition> transformers,
            @NotNull Registry<VariableDefinition> variables,
            @NotNull Registry<GroupDefinition> groups,
            @NotNull Registry<LoreTemplateDefinition> templates
    ) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Context context = new Context(diagnostics);

        for (String name : variables.keySet()) {
            VariableDefinition definition = variables.get(name).orElseThrow();
            if (definition.transformer() != null && transformers.get(definition.transformer()).isEmpty()) {
                report(
                        context,
                        LteErrors.UNKNOWN_TRANSFORMER,
                        "variable `" + name + "` references unknown transformer `" +
                                definition.transformer() + "`",
                        definition.transformerSpan() != null
                                ? definition.transformerSpan()
                                : definition.span(),
                        "no such transformer",
                        "define it in transformers.kdl"
                );
            }
        }

        for (String name : groups.keySet()) {
            GroupDefinition definition = groups.get(name).orElseThrow();
            if (definition.transformer() == null) continue;

            String transformerName = definition.transformer().name();
            FileSpan transformerSpan = definition.transformer().span();
            if (transformers.get(transformerName).isEmpty()) {
                report(
                        context,
                        LteErrors.UNKNOWN_TRANSFORMER,
                        "group `" + name + "` references unknown transformer `" + transformerName + "`",
                        transformerSpan,
                        "no such transformer",
                        "define it in transformers.kdl"
                );
            }

            switch (definition.source()) {
                case GroupDefinition.Source.Builtin builtin -> {
                    String key = definition.transformer().key();
                    Set<String> allowed = BUILTIN_STRING_FIELDS.getOrDefault(builtin.name(), Set.of());
                    if (key == null) {
                        report(
                                context,
                                LteErrors.INVALID_BUILTIN_TRANSFORMER_FIELD,
                                "group `" + name + "` uses a builtin transformer but has no `key`",
                                transformerSpan,
                                "missing `key`",
                                "add key=\"...\" to the `transformer` child"
                        );
                    } else if (!allowed.contains(key)) {
                        report(
                                context,
                                LteErrors.INVALID_BUILTIN_TRANSFORMER_FIELD,
                                "group `" + name + "` transformer key `" + key +
                                        "` is not a string field exposed by builtin `" + builtin.name() + "`",
                                transformerSpan,
                                "not a string field exposed by this builtin",
                                allowed.isEmpty()
                                        ? "this builtin does not expose transformer fields"
                                        : "use one of " + allowed
                        );
                    }
                }
                case GroupDefinition.Source.PDC ignored -> {
                    if (definition.transformer().key() != null) {
                        report(
                                context,
                                LteErrors.GROUP_TRANSFORMER_KEY_UNEXPECTED,
                                "group `" + name + "` uses a PDC transformer with an unexpected `key`",
                                transformerSpan,
                                "remove `key`; PDC groups transform their stored values directly"
                        );
                    }
                }
            }
        }

        Set<String> transformerFields = transformerFields(transformers);

        for (String templateId : templates.keySet()) {
            LoreTemplateDefinition template = templates.get(templateId).orElseThrow();
            for (TemplateElement element : template.elements()) {
                validateElement(
                        templateId,
                        element,
                        transformers,
                        variables,
                        groups,
                        transformerFields,
                        context
                );
            }
        }

        return List.copyOf(diagnostics);
    }

    private static @NotNull Set<String> transformerFields(
            @NotNull Registry<TransformerDefinition> transformers
    ) {
        Set<String> fields = new LinkedHashSet<>();
        for (String name : transformers.keySet()) {
            TransformerDefinition definition = transformers.get(name).orElseThrow();
            for (TransformerDefinition.Entry entry : definition.entries().values()) {
                fields.addAll(entry.fields().keySet());
            }
            if (definition.fallback() != null) {
                fields.addAll(definition.fallback().fields().keySet());
            }
        }
        return fields;
    }

    private static @NotNull Set<String> transformerFields(
            @NotNull Registry<TransformerDefinition> transformers,
            @NotNull String name
    ) {
        TransformerDefinition definition = transformers.get(name).orElse(null);
        if (definition == null) return Set.of();

        Set<String> fields = new LinkedHashSet<>();
        for (TransformerDefinition.Entry entry : definition.entries().values()) {
            fields.addAll(entry.fields().keySet());
        }
        if (definition.fallback() != null) {
            fields.addAll(definition.fallback().fields().keySet());
        }
        return fields;
    }

    private static void validateElement(
            @NotNull String templateId,
            @NotNull TemplateElement element,
            @NotNull Registry<TransformerDefinition> transformers,
            @NotNull Registry<VariableDefinition> variables,
            @NotNull Registry<GroupDefinition> groups,
            @NotNull Set<String> transformerFields,
            @NotNull Context context
    ) {
        switch (element) {
            case TemplateElement.Variable variable -> {
                VariableDefinition definition = variables.get(variable.name()).orElse(null);
                if (definition == null) {
                    report(
                            context,
                            LteErrors.UNKNOWN_VARIABLE,
                            "template `" + templateId + "` references unknown variable `" +
                                    variable.name() + "`",
                            variable.span(),
                            "no such variable",
                            "define it in variables.kdl"
                    );
                    return;
                }

                boolean hasTransformer = definition.transformer() != null;
                if (hasTransformer && variable.display().isEmpty()) {
                    report(
                            context,
                            LteErrors.MISSING_TRANSFORMER_DISPLAY,
                            "template `" + templateId + "` variable `" + variable.name() +
                                    "` uses a transformer but has no `display`",
                            variable.span(),
                            "transformer output requires a `display`",
                            "add display=\"...\""
                    );
                }

                checkTransformerFields(
                        context,
                        templateId,
                        "variable `" + variable.name() + "` display",
                        variable.display().orElse(""),
                        variable.span(),
                        definition.transformer(),
                        transformers,
                        transformerFields,
                        Set.of()
                );
            }
            case TemplateElement.Group group -> {
                GroupDefinition definition = groups.get(group.name()).orElse(null);
                if (definition == null) {
                    report(
                            context,
                            LteErrors.UNKNOWN_GROUP,
                            "template `" + templateId + "` references unknown group `" +
                                    group.name() + "`",
                            group.span(),
                            "no such group",
                            "define it in groups.kdl"
                    );
                    return;
                }

                Set<String> builtinFields = definition.source() instanceof GroupDefinition.Source.Builtin builtin
                        ? BUILTIN_FIELDS.getOrDefault(builtin.name(), Set.of())
                        : Set.of();

                checkTransformerFields(
                        context,
                        templateId,
                        "group `" + group.name() + "`",
                        group.each(),
                        group.span(),
                        definition.transformer() != null ? definition.transformer().name() : null,
                        transformers,
                        transformerFields,
                        builtinFields
                );
            }
            case TemplateElement.Text text -> checkTransformerFields(
                    context,
                    templateId,
                    "text",
                    text.value(),
                    text.span(),
                    null,
                    transformers,
                    transformerFields,
                    Set.of()
            );
            case TemplateElement.Blank ignored -> {
            }
            case TemplateElement.Insert ignored -> {
            }
        }
    }

    private static void checkTransformerFields(
            @NotNull Context context,
            @NotNull String templateId,
            @NotNull String description,
            @NotNull String template,
            @NotNull FileSpan span,
            @Nullable String transformerName,
            @NotNull Registry<TransformerDefinition> transformers,
            @NotNull Set<String> knownTransformerFields,
            @NotNull Set<String> builtinFields
    ) {
        Set<String> referenced = TemplateFields.referencedFields(template);
        referenced.removeAll(builtinFields);
        referenced.retainAll(knownTransformerFields);
        if (referenced.isEmpty()) return;

        if (transformerName == null) {
            report(
                    context,
                    LteErrors.TRANSFORMER_FIELD_WITHOUT_TRANSFORMER,
                    "template `" + templateId + "` " + description +
                            " uses transformer field(s) " + referenced + " without a transformer",
                    span,
                    "no transformer is bound here",
                    "bind a transformer or remove the field(s)"
            );
            return;
        }

        if (transformers.get(transformerName).isEmpty()) {
            return;
        }

        Set<String> provided = transformerFields(transformers, transformerName);
        referenced.removeAll(provided);
        if (!referenced.isEmpty()) {
            report(
                    context,
                    LteErrors.UNKNOWN_TRANSFORMER_FIELD,
                    "template `" + templateId + "` " + description +
                            " uses field(s) " + referenced + " not provided by transformer `" +
                            transformerName + "`",
                    span,
                    "transformer does not provide this field",
                    "add it to transformers.kdl or remove it"
            );
        }
    }

    private static void report(
            @NotNull Context context,
            @NotNull DiagnosticError error,
            @NotNull String message,
            @NotNull FileSpan span,
            @NotNull String label,
            @NotNull String help
    ) {
        context.addDiagnostic(Diagnostic.of(error, message, span, label, help));
    }

    private static void report(
            @NotNull Context context,
            @NotNull DiagnosticError error,
            @NotNull String message,
            @NotNull FileSpan span,
            @NotNull String label
    ) {
        context.addDiagnostic(Diagnostic.of(error, message, span, label));
    }
}
