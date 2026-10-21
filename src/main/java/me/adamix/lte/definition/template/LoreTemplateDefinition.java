package me.adamix.lte.definition.template;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record LoreTemplateDefinition(
        @NotNull String id,
        @NotNull List<TemplateElement> elements,
        @NotNull Optional<String> extendsId,
        @NotNull Optional<String> tooltipStyle,
        @NotNull Map<String, List<TemplateElement>> sections
        ) {
    public LoreTemplateDefinition {
        elements = List.copyOf(elements);
    }
}
