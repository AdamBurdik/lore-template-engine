package me.adamix.lte.api;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.List;
import java.util.Map;

public record ResolvedGroup(
        List<Entry> entries
) {
    public record Entry(
            String value,
            Map<String, String> fields,
            TagResolver resolver
    ) {
        public Entry {
            fields = Map.copyOf(fields);
        }
    }

    public ResolvedGroup {
        entries = List.copyOf(entries);
    }

    public static ResolvedGroup empty() {
        return new ResolvedGroup(List.of());
    }

    public List<TagResolver> elements() {
        return entries.stream().map(Entry::resolver).toList();
    }

    public List<String> values() {
        return entries.stream().map(Entry::value).toList();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}