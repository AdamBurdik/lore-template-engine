package me.adamix.lte;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.attribute.AttributeModifierDisplay;
import lombok.RequiredArgsConstructor;
import me.adamix.lte.api.ResolvedGroup;
import me.adamix.lte.api.exception.DefinitionNotFoundException;
import me.adamix.lte.api.exception.NotPdcBackedException;
import me.adamix.lte.definition.catalog.CatalogDefinition;
import me.adamix.lte.definition.group.GroupDefinition;
import me.adamix.lte.registry.Registry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

@SuppressWarnings("UnstableApiUsage")
@RequiredArgsConstructor
public class GroupService {
    private final LTEPlugin plugin;
    private final Registry<GroupDefinition> groupRegistry;
    private final Registry<CatalogDefinition> catalogRegistry;

    // separator used to encode a List<String> into a single PDC string value
    private static final String LIST_SEPARATOR = "\u0001";

    public @NotNull List<String> get(
            @NotNull String name,
            @NotNull ItemStack itemStack
    ) {
        var definitionOpt = groupRegistry.get(name);
        if (definitionOpt.isEmpty()) {
            throw new NoSuchElementException("No group definition registered with name " + name);
        }

        var definition = definitionOpt.get();

        return (switch (definition.source()) {
            case GroupDefinition.Source.Builtin(String builtin, String slot) ->
                    resolveBuiltin(builtin, slot, itemStack);
            case GroupDefinition.Source.PDC pdc -> resolvePdc(pdc, itemStack, true);
        }).values();
    }
    
    public @NotNull ResolvedGroup resolve(
            @NotNull String name,
            @NotNull ItemStack itemStack
    ) {
        var definitionOpt = groupRegistry.get(name);
        if (definitionOpt.isEmpty()) {
            throw new NoSuchElementException("No group definition registered with name " + name);
        }

        var definition = definitionOpt.get();

        return switch (definition.source()) {
            case GroupDefinition.Source.Builtin(String builtin, String slot) ->
                    resolveBuiltin(builtin, slot, itemStack);
            case GroupDefinition.Source.PDC pdc -> resolvePdc(pdc, itemStack, false);
        };
    }

    private @NotNull ResolvedGroup resolveBuiltin(
            @NotNull String builtin,
            @Nullable String slot,
            @NotNull ItemStack itemStack
    ) {
        return switch (builtin) {
            case "enchantments" -> resolveEnchantments(itemStack);
            case "attributes" -> resolveAttributes(itemStack, slot);
            default -> throw new NoSuchElementException("Unknown builtin group: " + builtin);
        };
    }

    private @NotNull ResolvedGroup resolveEnchantments(@NotNull ItemStack itemStack) {
        List<ResolvedGroup.Entry> entries = new ArrayList<>();

        for (Map.Entry<Enchantment, Integer> entry : itemStack.getEnchantments().entrySet()) {
            Enchantment enchantment = entry.getKey();
            int level = entry.getValue();

            TagResolver resolver = TagResolver.resolver(
                    Placeholder.component("ench_name", enchantment.description()),
                    Placeholder.parsed("ench_level", String.valueOf(level)),
                    Placeholder.parsed("ench_level_roman", toRoman(level))
            );

            // value: namespaced key, e.g. "minecraft:sharpness"
            entries.add(new ResolvedGroup.Entry(enchantment.key().asString(), resolver));
        }

        return new ResolvedGroup(entries);
    }

    private @NotNull ResolvedGroup resolveAttributes(@NotNull ItemStack itemStack, @Nullable String slot) {
        ItemAttributeModifiers modifiers = itemStack.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) {
            return ResolvedGroup.empty();
        }

        List<ResolvedGroup.Entry> entries = new ArrayList<>();

        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (slot != null && !entry.getGroup().toString().equals(slot)) {
                continue;
            }

            Attribute attribute = entry.attribute();
            AttributeModifier modifier = entry.modifier();

            Component attrName = switch (entry.display()) {
                case AttributeModifierDisplay.OverrideText override -> override.text();
                default -> Component.translatable(attribute.translationKey());
            };

            double amount = modifier.getAmount();
            double base = attribute == Attribute.ATTACK_DAMAGE ? 1.0 : attribute.getDefaultValue();
            double total = switch (modifier.getOperation()) {
                case ADD_NUMBER -> base + amount;
                case ADD_SCALAR, MULTIPLY_SCALAR_1 -> base * (1.0 + amount);
            };
            String attrValue = formatValue(total);

            String attrSlot = modifier.getSlotGroup().toString();
            String attrColor = total < 0 ? "<red>" : "<dark_green>";

            TagResolver resolver = TagResolver.resolver(
                    Placeholder.component("attr_name", attrName),
                    Placeholder.parsed("attr_value", attrValue),
                    Placeholder.parsed("attr_slot", attrSlot),
                    Placeholder.parsed("attr_color", attrColor)
            );

            entries.add(new ResolvedGroup.Entry(attrValue, resolver));
        }

        return new ResolvedGroup(entries);
    }

    private static @NotNull String formatValue(double value) {
        DecimalFormat df = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return df.format(value);
    }

    private @NotNull ResolvedGroup resolvePdc(
            @NotNull GroupDefinition.Source.PDC pdc,
            @NotNull ItemStack itemStack,
            boolean rawValues
    ) {
        var container = itemStack.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "group_" + pdc.key());

        String raw = container.get(key, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return ResolvedGroup.empty();
        }
        
        String[] split = raw.split(LIST_SEPARATOR);
        
        List<String> values = new ArrayList<>(split.length);
        
        // Map the values to catalog entries
        String catalogName = pdc.catalog();
        if (!rawValues && catalogName != null) {
            CatalogDefinition catalog = catalogRegistry.get(catalogName)
                    .orElseThrow(() -> new DefinitionNotFoundException("Unknown catalog: " + pdc.catalog()));

            String defaultValueKey = pdc.defaultValue();
            CatalogDefinition.Entry defaultEntry = (defaultValueKey != null)
                    ? catalog.entries().get(defaultValueKey)
                    : null;
            
            for (String value : split) {
                CatalogDefinition.Entry mapped = catalog.entries().getOrDefault(value, defaultEntry);
                
                if (mapped == null) continue;

                values.add(mapped.display());
            }
        } else {
            values.addAll(Arrays.asList(split));
        }

        List<ResolvedGroup.Entry> entries = new ArrayList<>();
        for (String value : values) {
            entries.add(new ResolvedGroup.Entry(value, Placeholder.parsed(pdc.valueName(), value)));
        }
        
        return new ResolvedGroup(entries);
    }

    public void set(@NotNull ItemStack itemStack, @NotNull String name, @NotNull List<String> values) {
        var definitionOpt = groupRegistry.get(name);
        if (definitionOpt.isEmpty()) {
            throw new DefinitionNotFoundException("Unknown group: " + name);
        }

        var source = definitionOpt.get().source();
        if (!(source instanceof GroupDefinition.Source.PDC pdcSource)) {
            throw new NotPdcBackedException("Group '" + name + "' is not PDC-backed and cannot be set");
        }

        itemStack.editMeta(meta -> {
            var container = meta.getPersistentDataContainer();
            container.set(new NamespacedKey(plugin, "group_" + pdcSource.key()), PersistentDataType.STRING, encodeList(values));
        });
    }

    public static @NotNull String encodeList(@NotNull List<String> values) {
        return String.join(LIST_SEPARATOR, values);
    }

    private static @NotNull String toRoman(int level) {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (level < 0 || level >= numerals.length) return String.valueOf(level);
        return numerals[level];
    }
}