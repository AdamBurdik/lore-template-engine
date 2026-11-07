package me.adamix.lte.definition.group;

import org.jetbrains.annotations.Nullable;

public record GroupDefinition(
        String name,
        Source source,
        @Nullable TransformerBinding transformer
) {
    public GroupDefinition(String name, Source source) {
        this(name, source, null);
    }

    public sealed interface Source {
        record Builtin(String name, @Nullable String slot) implements Source {
            public Builtin(String name) {
                this(name, null);
            }
        }
        record PDC(
                String key,
                String valueName
        ) implements Source {}
    }
}
