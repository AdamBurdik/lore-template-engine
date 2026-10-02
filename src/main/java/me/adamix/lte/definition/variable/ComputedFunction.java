package me.adamix.lte.definition.variable;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.function.Function;

@FunctionalInterface
public interface ComputedFunction extends Function<ItemStack, Optional<VariableValue>> {
}