package me.adamix.lte.definition.template;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record LoreTemplateDefinition(
        @NotNull String id,
        @NotNull List<TemplateElement> elements,
        @Nullable String extendsId,
        @Nullable String tooltipStyle,
        @NotNull Map<String, List<TemplateElement>> sections
        ) {
    public LoreTemplateDefinition {
        elements = List.copyOf(elements);
    }
}
