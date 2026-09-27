package me.adamix.lte.definition.variable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public record VariableDefinition(
        @NotNull String name,
        @NotNull Source source,
        @Nullable String defaultValue,
        @Nullable Lookup lookup
) {
    public sealed interface Source {
        record PDC(@NotNull String key) implements Source {
        }

        record Computed(@NotNull String function) implements Source {
        }
    }
    
    public record Lookup(
            @NotNull Map<String, String> map,
            @Nullable String fallback
    ) {
        public Lookup {
            map = Map.copyOf(map);
        }

        public @NotNull Optional<String> get(@NotNull String value) {
            return Optional.ofNullable(map.getOrDefault(value, fallback));
        }
    }
}
