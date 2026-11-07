package me.adamix.lte.definition.variable;

import me.adamix.kidl.diagnostic.FileSpan;
import org.jetbrains.annotations.Nullable;

public record VariableDefinition(
        String name,
        Source source,
        @Nullable String transformer,
        FileSpan span,
        @Nullable FileSpan transformerSpan
) {
    public sealed interface Source {
        record Pdc(
                String key,
                PdcValueType type,
                @Nullable VariableValue defaultValue
        ) implements Source {}

        record Computed(
                String fn,
                @Nullable VariableValue defaultValue
        ) implements Source {}
    }
}