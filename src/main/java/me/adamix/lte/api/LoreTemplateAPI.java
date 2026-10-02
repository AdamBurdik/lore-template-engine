package me.adamix.lte.api;

import me.adamix.lte.definition.variable.VariableValue;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

public interface LoreTemplateAPI {
    boolean hasTemplate(@NotNull String templateId);
    @NotNull Set<String> templateIds();
    @NotNull Optional<String> getTemplateId(@NotNull ItemStack itemStack);

    /**
     * Builds lore for the item if the template were applied.
     * Does not modify the item
     */
    @NotNull List<Component> render(@NotNull ItemStack item, @NotNull String templateId);

    /**
     * Applies the lore and other metadata at the item.
     * Noop when template does not exist
     */
    void apply(@NotNull ItemStack item, @NotNull String templateId);

    /**
     * Rerenders item lore using current template
     * Noop if no template set.
     */
    void rebuild(@NotNull ItemStack item);

    /**
     * Removes template lore and all metadata
     */
    void clear(@NotNull ItemStack item);

    @NotNull Optional<VariableValue> getVariable(@NotNull ItemStack item, @NotNull String variable);

    default @NotNull OptionalInt getIntVariable(@NotNull ItemStack item, @NotNull String variable) {
        Optional<VariableValue> value = getVariable(item, variable);
        if (value.isEmpty()) return OptionalInt.empty();

        if (!(value.get() instanceof VariableValue.IntValue(int v))) {
            throw new IllegalStateException(
                    "Variable '" + variable + "' is not an int (actual: " + value.get().getClass().getSimpleName() + ")");
        }
        return OptionalInt.of(v);
    }

    default @NotNull OptionalDouble getDoubleVariable(@NotNull ItemStack item, @NotNull String variable) {
        Optional<VariableValue> value = getVariable(item, variable);
        if (value.isEmpty()) return OptionalDouble.empty();

        if (!(value.get() instanceof VariableValue.DoubleValue(double v))) {
            throw new IllegalStateException(
                    "Variable '" + variable + "' is not a double (actual: " + value.get().getClass().getSimpleName() + ")");
        }
        return OptionalDouble.of(v);
    }

    default @NotNull Optional<String> getStringVariable(@NotNull ItemStack item, @NotNull String variable) {
        Optional<VariableValue> value = getVariable(item, variable);
        if (value.isEmpty()) return Optional.empty();

        if (!(value.get() instanceof VariableValue.StringValue(String v))) {
            throw new IllegalStateException(
                    "Variable '" + variable + "' is not a string (actual: " + value.get().getClass().getSimpleName() + ")");
        }
        return Optional.of(v);
    }

    default @NotNull Optional<Boolean> getBooleanVariable(@NotNull ItemStack item, @NotNull String variable) {
        Optional<VariableValue> value = getVariable(item, variable);
        if (value.isEmpty()) return Optional.empty();

        if (!(value.get() instanceof VariableValue.BooleanValue(boolean v))) {
            throw new IllegalStateException(
                    "Variable '" + variable + "' is not a boolean (actual: " + value.get().getClass().getSimpleName() + ")");
        }
        return Optional.of(v);
    }

    @NotNull List<String> getGroup(@NotNull ItemStack item, @NotNull String group);
    
    /**
     *  Creates item editor for editing item template, variables or groups
     */
    @NotNull ItemEditor edit(@NotNull ItemStack item);

    interface ItemEditor {
        @NotNull ItemEditor template(@NotNull String templateId);
        @NotNull ItemEditor variable(@NotNull String variable, @NotNull VariableValue value);
        @NotNull ItemEditor group(@NotNull String group, @NotNull List<String> values);
        void apply(); // writes PDC, then rebuilds once
    }

    /**
     * Rebuilds all items in player's inventory if they contain PDC tag 
     */
    void rebuildPlayer(@NotNull Player player);
}
