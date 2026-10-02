package me.adamix.lte.definition.variable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record VariableDefinition(
        @NotNull String name,
        @NotNull Source source,
        @Nullable Lookup lookup
) {
    public sealed interface Source {
        record Pdc(
                @NotNull String key,
                @NotNull PdcValueType type,
                @Nullable VariableValue defaultValue
        ) implements Source {}

        record Computed(
                @NotNull String fn,
                @Nullable VariableValue defaultValue
        ) implements Source {}
    }
}