package me.adamix.lte.definition.catalog;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public record CatalogDefinition(
        @NotNull String name,
        @NotNull Map<String, Entry> entries
) {
    public record Entry(
            @NotNull String id,
            @NotNull String display
    ) {}
}
