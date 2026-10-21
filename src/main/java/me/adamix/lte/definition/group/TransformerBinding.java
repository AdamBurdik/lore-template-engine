package me.adamix.lte.definition.group;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record TransformerBinding(
        @NotNull String name,
        @Nullable String key
) {
}
