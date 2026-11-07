package me.adamix.lte;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import me.adamix.lte.definition.template.LoreTemplateDefinition;
import me.adamix.lte.registry.Registry;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.NotImplementedException;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
public class TemplateService {
    private final Registry<LoreTemplateDefinition> registry;
    private final LoreRenderer renderer;

    public final NamespacedKey pdcKey;

    public TemplateService(
            LTEPlugin plugin,
            Registry<LoreTemplateDefinition> registry,
            VariableService variableService,
            GroupService groupService
    ) {
        this.registry = registry;
        this.pdcKey = new NamespacedKey(plugin, "template_id");
        this.renderer = new LoreRenderer(variableService, groupService);
    }
    
    public Optional<String> getTemplateId(ItemStack itemStack) {
        return Optional.ofNullable(itemStack.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING));
    }

    public void rebuild(ItemStack itemStack) {
        var pdc = itemStack.getPersistentDataContainer();
        String templateId = pdc.get(pdcKey, PersistentDataType.STRING);
        if (templateId == null) return;

        apply(templateId, itemStack);
    }
    
    public List<Component> render(ItemStack itemStack, String templateId) {
        var opt = registry.get(templateId);
        if (opt.isEmpty()) {
            return Collections.emptyList();
        }

        LoreTemplateDefinition template = opt.get();
        return renderer.render(template, itemStack);
    }

    public void apply(
            String templateId,
            ItemStack itemStack
    ) {
        var opt = registry.get(templateId);
        if (opt.isEmpty()) {
            throw new NoSuchElementException("No template registered with id " + templateId);
        }

        LoreTemplateDefinition template = opt.get();
        var lore = renderer.render(template, itemStack);
        itemStack.lore(lore);

        itemStack.editMeta(meta -> {
            var pdc = meta.getPersistentDataContainer();
            pdc.set(pdcKey, PersistentDataType.STRING, templateId);
        });

        itemStack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .hiddenComponents(Set.of(
                        DataComponentTypes.ATTRIBUTE_MODIFIERS,
                        DataComponentTypes.ENCHANTMENTS,
                        DataComponentTypes.UNBREAKABLE,
                        DataComponentTypes.STORED_ENCHANTMENTS,
                        DataComponentTypes.TRIM,
                        DataComponentTypes.DYED_COLOR,
                        DataComponentTypes.JUKEBOX_PLAYABLE
                ))
                .build()
        );

        String style = template.tooltipStyle().orElse(null);
        if (style != null) {
            itemStack.setData(DataComponentTypes.TOOLTIP_STYLE, Key.key(style));
        } else {
            // Removes the tooltip in case its set.
            itemStack.unsetData(DataComponentTypes.TOOLTIP_STYLE);
        }
    }
    
    public void clear(ItemStack itemStack) {
        throw new NotImplementedException("TemplateService#clear not implemented yet");
    }
}
