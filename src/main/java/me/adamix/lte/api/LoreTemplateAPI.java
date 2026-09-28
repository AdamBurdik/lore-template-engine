package me.adamix.lte.api;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
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

    @NotNull Optional<String> getVariable(@NotNull ItemStack item, @NotNull String variable);

    /**
     *  Creates item editor for editing item template, variables or groups
     */
    @NotNull ItemEditor edit(@NotNull ItemStack item);

    interface ItemEditor {
        @NotNull ItemEditor template(@NotNull String templateId);
        @NotNull ItemEditor variable(@NotNull String variable, @NotNull String value);
        @NotNull ItemEditor group(@NotNull String group, @NotNull List<String> values);
        void apply(); // writes PDC, then rebuilds once
    }

    /**
     * Rebuilds all items in player's inventory if they contain PDC tag 
     */
    void rebuildPlayer(@NotNull Player player);
}
