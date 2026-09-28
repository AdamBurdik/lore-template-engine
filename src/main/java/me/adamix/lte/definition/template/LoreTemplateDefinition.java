package me.adamix.lte.definition.template;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record LoreTemplateDefinition(
        @NotNull String id,
        @NotNull List<TemplateElement> elements,
        @Nullable String extendsId,
        @Nullable String tooltipStyle
) {
    public LoreTemplateDefinition {
        elements = List.copyOf(elements);
    }
}
