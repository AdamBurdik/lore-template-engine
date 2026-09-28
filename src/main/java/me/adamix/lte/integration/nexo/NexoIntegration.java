package me.adamix.lte.integration.nexo;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import com.nexomc.nexo.api.events.NexoMechanicsRegisteredEvent;
import com.nexomc.nexo.items.ItemBuilder;
import com.nexomc.nexo.items.UpdateCallback;
import com.nexomc.nexo.mechanics.MechanicsManager;
import lombok.RequiredArgsConstructor;
import me.adamix.lte.LTEPlugin;
import me.adamix.lte.api.LoreTemplateAPI;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class NexoIntegration implements Listener {
    private final LTEPlugin plugin;
    private final LoreTemplateAPI api;
    private LTEMechanicFactory factory;

    public void enable() {
        ConfigurationSection blank = new YamlConfiguration();
        factory = new LTEMechanicFactory(blank);
        MechanicsManager.INSTANCE.registerMechanicFactory(factory, true);
        plugin.getLogger().info("Nexo integration enabled");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onMechanicsRegistered(@NotNull NexoMechanicsRegisteredEvent event) {
        factory.clear();
    }

    @EventHandler
    public void onItemsLoaded(NexoItemsLoadedEvent event) {
        for (var entry : factory.getMechanics().entrySet()) {
            String id = entry.getKey();
            LTEMechanic mechanic = entry.getValue();

            ItemBuilder builder = NexoItems.itemFromId(id);
            if (builder == null) continue;

            builder.build();

            ItemStack finalStack = builder.getFinalItemStack();
            if (finalStack != null) {
                mechanic.applyTo(api, finalStack);
            }

            ItemStack temp = builder.build();
            builder.lore(temp.lore());

            Key key = id.contains(":") ? Key.key(id) : Key.key("nexo", id);
            NexoItems.registerUpdateCallback(key, new UpdateCallback() {
                @Override
                public ItemStack preUpdate(ItemStack itemStack) {
                    return itemStack;
                }

                @Override
                public @NotNull ItemStack postUpdate(String itemId, ItemStack itemStack, ItemStack pre) {
                    LTEMechanic m = factory.getMechanic(itemId);
                    if (m != null) m.applyTo(api, itemStack);
                    return itemStack;
                }
            });
        }
    }
}
