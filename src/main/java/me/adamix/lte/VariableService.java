package me.adamix.lte;

import io.papermc.paper.persistence.PersistentDataContainerView;
import me.adamix.lte.definition.variable.ComputedFunction;
import me.adamix.lte.definition.variable.PdcValueType;
import me.adamix.lte.definition.variable.VariableDefinition;
import me.adamix.lte.definition.variable.VariableValue;
import me.adamix.lte.registry.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

public class VariableService {
    private final LTEPlugin plugin;
    private final Registry<VariableDefinition> variableRegistry;
    private final Map<String, ComputedFunction> computedFunctions;

    public VariableService(
            @NotNull LTEPlugin plugin,
            @NotNull Registry<VariableDefinition> variableRegistry,
            @NotNull Map<String, ComputedFunction> computedFunctions
    ) {
        this.plugin = plugin;
        this.variableRegistry = variableRegistry;
        this.computedFunctions = computedFunctions;
    }

    public @NotNull Optional<VariableValue> getRawValue(
            @NotNull String name,
            @NotNull ItemStack itemStack
    ) {
        VariableDefinition definition = variableRegistry.get(name)
                .orElseThrow(() -> new NoSuchElementException("No variable definition registered with name " + name));

        return switch (definition.source()) {
            case VariableDefinition.Source.Pdc(String key, PdcValueType type, VariableValue defaultValue) -> {
                var pdc = itemStack.getPersistentDataContainer();
                NamespacedKey nsKey = new NamespacedKey(plugin, "variable_" + key);

                if (!pdc.has(nsKey)) {
                    yield Optional.ofNullable(defaultValue);
                }
                yield Optional.of(readTyped(pdc, nsKey, type));
            }
            case VariableDefinition.Source.Computed(String fn, VariableValue defaultValue) -> {
                ComputedFunction function = computedFunctions.get(fn);
                if (function == null) {
                    throw new NoSuchElementException("No computed function registered with name " + fn);
                }
                Optional<VariableValue> result = function.apply(itemStack);
                yield result.isPresent() ? result : Optional.ofNullable(defaultValue);
            }
        };
    }

    public @NotNull Optional<String> getDisplayValue(
            @NotNull String name,
            @NotNull ItemStack itemStack
    ) {
        Optional<VariableValue> raw = getRawValue(name, itemStack);
        if (raw.isEmpty()) return Optional.empty();

        String stringified = VariableValue.stringify(raw.get());

        VariableDefinition definition = variableRegistry.get(name).orElseThrow();
        if (definition.lookup() != null) {
            return definition.lookup().get(stringified);
        }
        return Optional.of(stringified);
    }

    public void setValue(
            @NotNull ItemStack itemStack,
            @NotNull String name,
            @NotNull VariableValue value
    ) {
        VariableDefinition definition = variableRegistry.get(name)
                .orElseThrow(() -> new NoSuchElementException("No variable definition registered with name " + name));

        if (!(definition.source() instanceof VariableDefinition.Source.Pdc pdcSource)) {
            throw new IllegalArgumentException("Variable '" + name + "' is not PDC-backed and cannot be set directly");
        }

        if (!matchesType(value, pdcSource.type())) {
            throw new IllegalArgumentException(
                    "Variable '" + name + "' expects " + pdcSource.type() + " but got " + value.getClass().getSimpleName());
        }

        NamespacedKey namespacedKey = new NamespacedKey(plugin, "variable_" + pdcSource.key());
        itemStack.editMeta(meta -> writeTyped(meta.getPersistentDataContainer(), namespacedKey, value));
    }

    private @NotNull VariableValue readTyped(
            @NotNull PersistentDataContainerView pdc,
            @NotNull NamespacedKey key,
            @NotNull PdcValueType type
    ) {
        return switch (type) {
            case INT -> {
                Integer v = pdc.get(key, PersistentDataType.INTEGER);
                if (v == null) throw missingValue(key, type);
                yield new VariableValue.IntValue(v);
            }
            case DOUBLE -> {
                Double v = pdc.get(key, PersistentDataType.DOUBLE);
                if (v == null) throw missingValue(key, type);
                yield new VariableValue.DoubleValue(v);
            }
            case STRING -> {
                String v = pdc.get(key, PersistentDataType.STRING);
                if (v == null) throw missingValue(key, type);
                yield new VariableValue.StringValue(v);
            }
            case BOOLEAN -> {
                Boolean v = pdc.get(key, PersistentDataType.BOOLEAN);
                if (v == null) throw missingValue(key, type);
                yield new VariableValue.BooleanValue(v);
            }
        };
    }

    private static @NotNull IllegalStateException missingValue(@NotNull NamespacedKey key, @NotNull PdcValueType type) {
        return new IllegalStateException(
                "PDC key '" + key + "' has no " + type + " value, even though pdc.has(key) was true. "
                        + "Likely stored under a different PersistentDataType than declared");
    }

    private void writeTyped(
            @NotNull PersistentDataContainer pdc,
            @NotNull NamespacedKey key,
            @NotNull VariableValue value
    ) {
        switch (value) {
            case VariableValue.IntValue(int v) -> pdc.set(key, PersistentDataType.INTEGER, v);
            case VariableValue.DoubleValue(double v) -> pdc.set(key, PersistentDataType.DOUBLE, v);
            case VariableValue.StringValue(String v) -> pdc.set(key, PersistentDataType.STRING, v);
            case VariableValue.BooleanValue(boolean v) -> pdc.set(key, PersistentDataType.BOOLEAN, v);
        }
    }

    private static boolean matchesType(@NotNull VariableValue value, @NotNull PdcValueType type) {
        return switch (type) {
            case INT -> value instanceof VariableValue.IntValue;
            case DOUBLE -> value instanceof VariableValue.DoubleValue;
            case STRING -> value instanceof VariableValue.StringValue;
            case BOOLEAN -> value instanceof VariableValue.BooleanValue;
        };
    }

    public static @NotNull VariableValue parseTyped(@NotNull String raw, @NotNull PdcValueType type) {
        return switch (type) {
            case INT -> new VariableValue.IntValue(Integer.parseInt(raw));
            case DOUBLE -> new VariableValue.DoubleValue(Double.parseDouble(raw));
            case STRING -> new VariableValue.StringValue(raw);
            case BOOLEAN -> {
                if (!raw.equals("true") && !raw.equals("false")) {
                    throw new IllegalArgumentException("'" + raw + "' is not a valid boolean");
                }
                yield new VariableValue.BooleanValue(Boolean.parseBoolean(raw));
            }
        };
    }
}