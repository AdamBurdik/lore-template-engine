package me.adamix.lte.definition.template;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record LoreTemplateDefinition(
        String id,
        List<TemplateElement> elements,
        Optional<String> extendsId,
        Optional<String> tooltipStyle,
        Map<String, List<TemplateElement>> sections
        ) {
    public LoreTemplateDefinition {
        elements = List.copyOf(elements);
    }
}
