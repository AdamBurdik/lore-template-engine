package me.adamix.lte;

import me.adamix.lte.definition.group.GroupDefinition;
import me.adamix.lte.definition.template.LoreTemplateDefinition;
import me.adamix.lte.definition.template.TemplateElement;
import me.adamix.lte.definition.transformer.TransformerDefinition;
import me.adamix.lte.definition.variable.VariableDefinition;
import me.adamix.lte.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DefinitionValidator {
    private static final Set<String> TRANSFORMER_FIELDS = Set.of("color", "icon", "label");
    private static final Map<String, Set<String>> BUILTIN_STRING_FIELDS = Map.of(
            "enchantments", Set.of("ench_id", "ench_level", "ench_level_roman"),
            "attributes", Set.of("attr_value", "attr_slot", "attr_color")
    );

    private DefinitionValidator() {
    }

    public static void validate(
            @NotNull Registry<TransformerDefinition> transformers,
            @NotNull Registry<VariableDefinition> variables,
            @NotNull Registry<GroupDefinition> groups,
            @NotNull Registry<LoreTemplateDefinition> templates
    ) {
        List<String> errors = new ArrayList<>();

        for (String name : variables.keySet()) {
            VariableDefinition definition = variables.get(name).orElseThrow();
            if (definition.transformer() != null && transformers.get(definition.transformer()).isEmpty()) {
                errors.add("variable `" + name + "` references unknown transformer `" +
                        definition.transformer() + "`");
            }
        }

        for (String name : groups.keySet()) {
            GroupDefinition definition = groups.get(name).orElseThrow();
            if (definition.transformer() == null) continue;

            String transformerName = definition.transformer().name();
            if (transformers.get(transformerName).isEmpty()) {
                errors.add("group `" + name + "` references unknown transformer `" + transformerName + "`");
            }

            switch (definition.source()) {
                case GroupDefinition.Source.Builtin builtin -> {
                    String key = definition.transformer().key();
                    if (key == null) {
                        errors.add("group `" + name + "` uses a builtin transformer but has no `key`");
                    } else if (!BUILTIN_STRING_FIELDS.getOrDefault(builtin.name(), Set.of()).contains(key)) {
                        errors.add("group `" + name + "` transformer key `" + key +
                                "` is not a string field exposed by builtin `" + builtin.name() + "`");
                    }
                }
                case GroupDefinition.Source.PDC pdc -> {
                    if (definition.transformer().key() != null) {
                        errors.add("group `" + name + "` uses a PDC transformer with an unexpected `key`");
                    }
                }
            }
        }

        for (String templateId : templates.keySet()) {
            LoreTemplateDefinition template = templates.get(templateId).orElseThrow();
            for (TemplateElement element : template.elements()) {
                validateElement(templateId, element, variables, groups, errors);
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Definition validation failed:\n - " + String.join("\n - ", errors)
            );
        }
    }

    private static void validateElement(
            @NotNull String templateId,
            @NotNull TemplateElement element,
            @NotNull Registry<VariableDefinition> variables,
            @NotNull Registry<GroupDefinition> groups,
            @NotNull List<String> errors
    ) {
        switch (element) {
            case TemplateElement.Variable variable -> {
                VariableDefinition definition = variables.get(variable.name()).orElse(null);
                if (definition == null) {
                    errors.add("template `" + templateId + "` references unknown variable `" +
                            variable.name() + "`");
                    return;
                }

                boolean hasTransformer = definition.transformer() != null;
                if (hasTransformer && variable.display().isEmpty()) {
                    errors.add("template `" + templateId + "` variable `" + variable.name() +
                            "` uses a transformer but has no `display`");
                }

                checkTransformerFields(
                        templateId,
                        "variable `" + variable.name() + "` display",
                        variable.display().orElse(""),
                        hasTransformer,
                        errors
                );
            }
            case TemplateElement.Group group -> {
                GroupDefinition definition = groups.get(group.name()).orElse(null);
                if (definition == null) {
                    errors.add("template `" + templateId + "` references unknown group `" +
                            group.name() + "`");
                    return;
                }

                checkTransformerFields(
                        templateId,
                        "group `" + group.name() + "`",
                        group.each(),
                        definition.transformer() != null,
                        errors
                );
            }
            case TemplateElement.Text text -> checkTransformerFields(
                    templateId,
                    "text",
                    text.value(),
                    false,
                    errors
            );
            case TemplateElement.Blank ignored -> {
            }
            case TemplateElement.Insert ignored -> {
            }
        }
    }

    private static void checkTransformerFields(
            @NotNull String templateId,
            @NotNull String context,
            @NotNull String template,
            boolean hasTransformer,
            @NotNull List<String> errors
    ) {
        if (hasTransformer) return;

        Set<String> referenced = TemplateFields.referencedFields(template);
        referenced.retainAll(TRANSFORMER_FIELDS);
        if (!referenced.isEmpty()) {
            errors.add("template `" + templateId + "` " + context +
                    " uses transformer field(s) " + referenced + " without a transformer");
        }
    }
}
