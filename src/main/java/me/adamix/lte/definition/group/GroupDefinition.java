package me.adamix.lte.definition.group;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record GroupDefinition(
        @NotNull String name,
        @NotNull Source source
) {
    public sealed interface Source {
        record Builtin(@NotNull String name, @Nullable String slot) implements Source {
            public Builtin(@NotNull String name) {
                this(name, null);
            }
        }
        record PDC(
                @NotNull String key,
                @NotNull String valueName
        ) implements Source {}
    }
}
