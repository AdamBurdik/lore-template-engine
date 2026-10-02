package me.adamix.lte.definition.variable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public record Lookup(
        @NotNull Map<String, String> entries,
        @Nullable String fallback
) {
    public @NotNull Optional<String> get(@NotNull String rawStringified) {
        String mapped = entries.get(rawStringified);
        if (mapped != null) return Optional.of(mapped);
        return Optional.ofNullable(fallback);
    }
}