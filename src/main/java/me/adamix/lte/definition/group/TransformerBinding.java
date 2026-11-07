package me.adamix.lte.definition.group;

import me.adamix.kidl.diagnostic.FileSpan;
import org.jetbrains.annotations.Nullable;

public record TransformerBinding(
        String name,
        @Nullable String key,
        FileSpan span
) {
}
