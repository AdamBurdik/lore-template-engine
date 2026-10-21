package me.adamix.lte.definition.transformer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

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
            @Nullable String color,
            @Nullable String icon,
            @Nullable String label
    ) {
        public @NotNull Map<String, String> fields() {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("color", color == null ? "" : color);
            fields.put("icon", icon == null ? "" : icon);
            fields.put("label", label == null ? "" : label);
            return Map.copyOf(fields);
        }
    }
}
