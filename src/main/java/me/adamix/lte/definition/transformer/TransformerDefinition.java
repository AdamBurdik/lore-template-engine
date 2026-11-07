package me.adamix.lte.definition.transformer;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public record TransformerDefinition(
        String name,
        Map<String, Entry> entries,
        @Nullable Entry fallback
) {
    public TransformerDefinition {
        entries = Map.copyOf(entries);
    }

    public record Entry(
            String id,
            Map<String, String> fields
    ) {
        public Optional<String> field(String key) {
            return Optional.ofNullable(fields.get(key));
        }
    }
}
