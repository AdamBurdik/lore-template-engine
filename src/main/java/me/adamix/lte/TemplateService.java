package me.adamix.lte;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import me.adamix.lte.definition.group.ResolvedGroup;
import me.adamix.lte.definition.template.LoreTemplateDefinition;
import me.adamix.lte.definition.template.TemplateElement;
import me.adamix.lte.registry.TemplateRegistry;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.minecraft.util.datafix.fixes.ItemStackTagFix;
import org.apache.commons.lang3.NotImplementedException;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.legacy.reroute.NotInBukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
public class TemplateService {
    private final LTEPlugin plugin;
    private final TemplateRegistry registry;
    private final VariableService variableService;
    private final GroupService groupService;

    public final NamespacedKey pdcKey;

    public TemplateService(
            @NotNull LTEPlugin plugin,
            @NotNull TemplateRegistry registry,
            @NotNull VariableService variableService,
            @NotNull GroupService groupService
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.variableService = variableService;
        this.groupService = groupService;
        this.pdcKey = new NamespacedKey(plugin, "template_id");
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
        return parse(template, itemStack);
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
        var lore = parse(template, itemStack);
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

    private @NotNull List<Component> parse(
            @NotNull LoreTemplateDefinition template,
            @NotNull ItemStack itemStack
    ) {
        List<Component> lore = new ArrayList<>();

        for (TemplateElement element : template.elements()) {
            switch (element) {
                case TemplateElement.Blank(@Nullable String collapseIfEmpty) -> {
                    if (collapseIfEmpty != null) {
                        ResolvedGroup resolved = groupService.resolve(collapseIfEmpty, itemStack);
                        if (resolved.isEmpty()) {
                            break;
                        }
                    }

                    lore.add(Component.text(""));
                }
                case TemplateElement.Text(String value, @Nullable String collapseIfEmpty) -> {
                    if (collapseIfEmpty != null) {
                        ResolvedGroup resolved = groupService.resolve(collapseIfEmpty, itemStack);
                        if (resolved.isEmpty()) {
                            break;
                        }
                    }

                    lore.add(
                            LTEPlugin.MINI_MESSAGE.deserialize("<!italic><white>" + value)
                    );
                }
                case TemplateElement.Variable(String name, String prefix, String suffix) -> {
                    var variableValue = variableService.getValue(name, itemStack);
                    if (variableValue.isEmpty()) {
                        break;
                    }

                    if (prefix == null) prefix = "";
                    if (suffix == null) suffix = "";
                    String finalValue = prefix + variableValue.get() + suffix;

                    lore.add(
                            LTEPlugin.MINI_MESSAGE.deserialize("<!italic><white>" + finalValue)
                    );
                }
                case TemplateElement.Group(
                        @NotNull String name,
                        @NotNull String each,
                        @NotNull TemplateElement.Group.Empty empty,
                        boolean inline,
                        @Nullable String joiner
                ) -> {
                    ResolvedGroup resolved = groupService.resolve(name, itemStack);

                    if (resolved.isEmpty()) {
                        switch (empty) {
                            case TemplateElement.Group.Empty.Skip _ -> {
                            }
                        }
                        continue;
                    }

                    if (inline) {
                        String sep = joiner != null ? joiner : "";
                        List<Component> rendered = new ArrayList<>();
                        for (TagResolver resolver : resolved.elements()) {
                            rendered.add(LTEPlugin.MINI_MESSAGE.deserialize("<!italic><white>" + each, resolver));
                        }
                        Component joined = Component.join(
                                JoinConfiguration.separator(
                                        LTEPlugin.MINI_MESSAGE.deserialize(sep)
                                ),
                                rendered
                        );
                        lore.add(joined);
                    } else {
                        for (TagResolver resolver : resolved.elements()) {
                            lore.add(
                                    LTEPlugin.MINI_MESSAGE.deserialize("<!italic><white>" + each, resolver)
                            );
                        }
                    }
                }
            }

        }

        return lore;
    }
    
    public void clear(@NotNull ItemStack itemStack) {
        throw new NotImplementedException("TemplateService#clear not implemented yet");
    }
}
