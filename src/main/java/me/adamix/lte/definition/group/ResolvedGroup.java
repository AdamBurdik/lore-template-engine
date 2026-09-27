package me.adamix.lte.definition.group;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ResolvedGroup(
        @NotNull List<TagResolver> elements
) {
    public ResolvedGroup {
        elements = List.copyOf(elements);
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }
}