package me.adamix.lte.listener;

import lombok.RequiredArgsConstructor;
import me.adamix.lte.LTEPlugin;
import me.adamix.lte.TemplateService;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@RequiredArgsConstructor
public class ItemListener implements Listener {
    private final LTEPlugin plugin;
    private final TemplateService templateService;

    @EventHandler(ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        var itemStack = event.getItem();

        for (Map.Entry<Enchantment, Integer> entry : event.getEnchantsToAdd().entrySet()) {
            itemStack.addUnsafeEnchantment(entry.getKey(), entry.getValue());
        }

        templateService.rebuild(itemStack);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null) return;

        ItemStack[] matrix = event.getInventory().getMatrix();
        // This really useless check is forced by spotbugs.
        if (matrix != null) {
            ItemStack templatedIngredient = findTemplatedIngredient(matrix);
            if (templatedIngredient != null) {
                copyLoreData(templatedIngredient, result);
            }
        }
        ItemStack templatedIngredient = findTemplatedIngredient(matrix);
        if (templatedIngredient != null) {
            copyLoreData(templatedIngredient, result);
        }

        templateService.rebuild(result);
        event.getInventory().setResult(result);
    }

    private @Nullable ItemStack findTemplatedIngredient(@Nullable ItemStack @NotNull [] matrix) {
        for (ItemStack ingredient : matrix) {
            if (ingredient == null) continue;
            if (ingredient.getType().isAir()) continue;
            if (!ingredient.hasItemMeta()) continue;

            var pdc = ingredient.getItemMeta().getPersistentDataContainer();
            if (pdc.has(templateService.pdcKey)) {
                return ingredient;
            }
        }
        return null;
    }

    private void copyLoreData(@NotNull ItemStack from, @NotNull ItemStack to) {
        if (!from.hasItemMeta()) return;

        var fromPdc = from.getItemMeta().getPersistentDataContainer();

        to.editMeta(meta -> {
            var toPdc = meta.getPersistentDataContainer();
            for (NamespacedKey key : fromPdc.getKeys()) {
                if (!key.getNamespace().equals(plugin.getName().toLowerCase())) continue;

                String value = fromPdc.get(key, PersistentDataType.STRING);
                if (value != null) {
                    toPdc.set(key, PersistentDataType.STRING, value);
                }
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null) return;

        templateService.rebuild(result);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null) return;

        templateService.rebuild(result);
        event.setResult(result);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        ItemStack result = event.getResult();
        if (result == null) return;

        templateService.rebuild(result);
        event.setResult(result);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        if (result == null) return;

        templateService.rebuild(result);
        event.setResult(result);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerItemMend(PlayerItemMendEvent event) {
        templateService.rebuild(event.getItem());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        for (ItemStack result : event.getContents()) {
            if (result != null && !result.getType().isAir()) {
                templateService.rebuild(result);
            }
        }
    }
}