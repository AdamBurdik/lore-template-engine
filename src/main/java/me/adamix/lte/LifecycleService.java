package me.adamix.lte;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class LifecycleService {
    private final TemplateService templateService;

    public void rebuildPlayer(@NotNull Player player) {
        for (ItemStack item : inventoryContents(player)) {
            if (item == null || item.getType().isAir()) continue;
            if (!hasTemplateTag(item)) continue;

            templateService.rebuild(item);
        }

        player.updateInventory();
    }

    public void rebuildAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            rebuildPlayer(player);
        }
    }

    private boolean hasTemplateTag(@NotNull ItemStack item) {
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(templateService.pdcKey, PersistentDataType.STRING);
    }

    private @NotNull List<ItemStack> inventoryContents(@NotNull Player player) {
        PlayerInventory inventory = player.getInventory();

        List<ItemStack> items = new ArrayList<>();
        addNonNull(items, inventory.getContents());
        addNonNull(items, inventory.getArmorContents());
        ItemStack offHand = inventory.getItemInOffHand();
        if (!offHand.getType().isAir()) {
            items.add(offHand);
        }

        return items;
    }

    private void addNonNull(@NotNull List<ItemStack> target, @Nullable ItemStack[] source) {
        if (source == null) return;
        for (ItemStack item : source) {
            if (item == null || item.getType().isAir()) continue;
            target.add(item);
        }
    }
}