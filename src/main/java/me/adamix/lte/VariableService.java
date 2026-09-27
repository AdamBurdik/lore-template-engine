package me.adamix.lte;

import lombok.RequiredArgsConstructor;
import me.adamix.lte.definition.variable.VariableDefinition;
import me.adamix.lte.registry.VariableRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;
import java.util.Optional;

@RequiredArgsConstructor
public class VariableService {
    private final LTEPlugin plugin;
    private final VariableRegistry variableRegistry;

    public @NotNull Optional<String> getValue(
            @NotNull String name,
            @NotNull ItemStack itemStack
    ) {
        var definition = variableRegistry.get(name)
                .orElseThrow(() -> new NoSuchElementException("No variable definition registered with name " + name));

        String value = switch (definition.source()) {
            case VariableDefinition.Source.Computed _ -> null;
            case VariableDefinition.Source.PDC(String key) -> {
                var pdc = itemStack.getPersistentDataContainer();

                NamespacedKey namespacedKey = new NamespacedKey(plugin, "variable_" + key);

                if (!pdc.has(namespacedKey)) {
                    yield null;
                }

                yield pdc.get(namespacedKey, PersistentDataType.STRING);
            }
        };

        if (value == null) {
            if (definition.defaultValue() == null) {
                return Optional.empty();
            }
            value = definition.defaultValue();
        }

        if (definition.lookup() != null) {
            return definition.lookup().get(value);
        }

        return Optional.of(value);
    }
}
