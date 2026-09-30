package me.adamix.lte.api;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ResolvedGroup(
        @NotNull List<Entry> entries
) {
    public record Entry(@NotNull String value, @NotNull TagResolver resolver) {
    }

    public ResolvedGroup {
        entries = List.copyOf(entries);
    }

    public static @NotNull ResolvedGroup empty() {
        return new ResolvedGroup(List.of());
    }

    public @NotNull List<TagResolver> elements() {
        return entries.stream().map(Entry::resolver).toList();
    }

    public @NotNull List<String> values() {
        return entries.stream().map(Entry::value).toList();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}