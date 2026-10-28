package me.adamix.lte.definition.transformer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record TransformerDefinition(
        @NotNull String name,
        @NotNull Map<String, Entry> entries,
        @Nullable Entry fallback
) {
    public TransformerDefinition {
        entries = Map.copyOf(entries);
    }

    public record Entry(
            @NotNull String id,
            @NotNull Map<String, String> fields
    ) {
        public @NotNull Optional<String> field(@NotNull String key) {
            return fields.get(key).describeConstable();
        }
    }
}
