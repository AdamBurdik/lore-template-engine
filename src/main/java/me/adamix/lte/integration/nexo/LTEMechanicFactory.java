package me.adamix.lte.integration.nexo;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class LTEMechanicFactory extends MechanicFactory {
    private final Map<String, LTEMechanic> byItem = new HashMap<>();

    public LTEMechanicFactory(
            @NotNull ConfigurationSection section
    ) {
        super("lte");
    }

    @Override
    public Mechanic parse(ConfigurationSection section) {
        LTEMechanic mechanic = new LTEMechanic(this, section);
        String itemID = mechanic.getItemID();

        byItem.put(itemID, mechanic);

        return mechanic;
    }

    @Override
    public LTEMechanic getMechanic(@Nullable String itemID) {
        if (itemID == null) return null;
        return byItem.get(itemID);
    }

    @Override
    public LTEMechanic getMechanic(@Nullable ItemStack item) {
        String id = NexoItems.idFromItem(item);
        return id == null ? null : byItem.get(id);
    }

    public void clear() {
        byItem.clear();
    }

    public Set<String> getItemIds() {
        return byItem.keySet();
    }

    public Map<String, LTEMechanic> getMechanics() {
        return byItem;
    }
}