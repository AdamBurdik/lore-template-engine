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
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
public class TemplateService {
    private final LTEPlugin plugin;
    private final Registry<LoreTemplateDefinition> registry;
    private final VariableService variableService;
    private final GroupService groupService;
    private final LoreRenderer renderer;

    public final NamespacedKey pdcKey;

    public TemplateService(
            @NotNull LTEPlugin plugin,
            @NotNull Registry<LoreTemplateDefinition> registry,
            @NotNull VariableService variableService,
            @NotNull GroupService groupService
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.variableService = variableService;
        this.groupService = groupService;
        this.pdcKey = new NamespacedKey(plugin, "template_id");
        this.renderer = new LoreRenderer(variableService, groupService);
    }
    
    public @NotNull Optional<String> getTemplateId(@NotNull ItemStack itemStack) {
        return Optional.ofNullable(itemStack.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING));
    }

    public void rebuild(@NotNull ItemStack itemStack) {
        var pdc = itemStack.getPersistentDataContainer();
        String templateId = pdc.get(pdcKey, PersistentDataType.STRING);
        if (templateId == null) return;

        apply(templateId, itemStack);
    }
    
    public @NotNull List<Component> render(@NotNull ItemStack itemStack, @NotNull String templateId) {
        var opt = registry.get(templateId);
        if (opt.isEmpty()) {
            return Collections.emptyList();
        }

        LoreTemplateDefinition template = opt.get();
        return renderer.render(template, itemStack);
    }

    public void apply(
            @NotNull String templateId,
            @NotNull ItemStack itemStack
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

        String style = template.tooltipStyle();
        if (style != null) {
            itemStack.setData(DataComponentTypes.TOOLTIP_STYLE, Key.key(style));
        }
    }
    
    public void clear(@NotNull ItemStack itemStack) {
        throw new NotImplementedException("TemplateService#clear not implemented yet");
    }
}
