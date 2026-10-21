package me.adamix.lte;

import me.adamix.lte.definition.transformer.TransformerDefinition;
import me.adamix.lte.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.Optional;

public class TransformerService {
    private final Registry<TransformerDefinition> registry;

    public TransformerService(@NotNull Registry<TransformerDefinition> registry) {
        this.registry = registry;
    }

    public @NotNull Optional<TransformerDefinition> get(@NotNull String name) {
        return registry.get(name);
    }

    public @NotNull Optional<TransformerDefinition.Entry> resolve(
            @NotNull String name,
            @Nullable String input
    ) {
        TransformerDefinition definition = registry.get(name)
                .orElseThrow(() -> new NoSuchElementException("No transformer registered with name " + name));

        TransformerDefinition.Entry entry = input == null ? null : definition.entries().get(input);
        if (entry == null) {
            entry = definition.fallback();
        }

        return Optional.ofNullable(entry);
    }
}
