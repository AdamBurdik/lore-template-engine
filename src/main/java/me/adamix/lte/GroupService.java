package me.adamix.lte;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.attribute.AttributeModifierDisplay;
import me.adamix.lte.api.ResolvedGroup;
import me.adamix.lte.api.exception.DefinitionNotFoundException;
import me.adamix.lte.api.exception.NotPdcBackedException;
import me.adamix.lte.definition.catalog.CatalogDefinition;
import me.adamix.lte.definition.group.GroupDefinition;
import me.adamix.lte.registry.Registry;
import me.adamix.lte.types.NullableString;
import me.adamix.lte.types.NullableStringType;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

@SuppressWarnings("UnstableApiUsage")
public class GroupService {
    private final LTEPlugin plugin;
    private final Registry<GroupDefinition> groupRegistry;
    private final Registry<CatalogDefinition> catalogRegistry;
    private final PersistentDataType<?, List<NullableString>> listType;

    public GroupService(
            LTEPlugin plugin,
            Registry<GroupDefinition> groupRegistry,
            Registry<CatalogDefinition> catalogRegistry
    ) {
        this.plugin = plugin;
        this.groupRegistry = groupRegistry;
        this.catalogRegistry = catalogRegistry;
        this.listType = PersistentDataType.LIST.listTypeFrom(new NullableStringType(plugin));
    }

    public @NotNull List<@Nullable String> get(@NotNull String name, @NotNull ItemStack itemStack) {
        var definition = groupRegistry.get(name)
                .orElseThrow(() -> new NoSuchElementException("No group definition registered with name " + name));

        return switch (definition.source()) {
            case GroupDefinition.Source.Builtin(String builtin, String slot) ->
                    resolveBuiltin(builtin, slot, itemStack).values();
            case GroupDefinition.Source.PDC pdc -> readValues(pdc, itemStack);
        };
    }

    public @NotNull ResolvedGroup resolve(@NotNull String name, @NotNull ItemStack itemStack) {
        var definition = groupRegistry.get(name)
                .orElseThrow(() -> new NoSuchElementException("No group definition registered with name " + name));

        return switch (definition.source()) {
            case GroupDefinition.Source.Builtin(String builtin, String slot) ->
                    resolveBuiltin(builtin, slot, itemStack);
            case GroupDefinition.Source.PDC pdc -> resolvePdc(pdc, itemStack);
        };
    }

    private @NotNull NamespacedKey keyFor(GroupDefinition.Source.PDC pdc) {
        return new NamespacedKey(plugin, "group_" + pdc.key());
    }

    private @NotNull List<@Nullable String> readValues(
            @NotNull GroupDefinition.Source.PDC pdc,
            @NotNull ItemStack itemStack
    ) {
        var container = itemStack.getPersistentDataContainer();
        List<NullableString> stored = container.get(keyFor(pdc), listType);
        if (stored == null) return List.of();

        List<@Nullable String> values = new ArrayList<>(stored.size());
        for (NullableString s : stored) {
            values.add(s.value());
        }
        return values;
    }

    private @NotNull ResolvedGroup resolvePdc(
            @NotNull GroupDefinition.Source.PDC pdc,
            @NotNull ItemStack itemStack
    ) {
        List<@Nullable String> values = readValues(pdc, itemStack);
        if (values.isEmpty()) return ResolvedGroup.empty();

        String catalogName = pdc.catalog();
        CatalogDefinition catalog = null;
        CatalogDefinition.Entry defaultEntry = null;
        if (catalogName != null) {
            catalog = catalogRegistry.get(catalogName)
                    .orElseThrow(() -> new DefinitionNotFoundException("Unknown catalog: " + catalogName));
            String defaultKey = pdc.defaultValue();
            defaultEntry = defaultKey != null ? catalog.entries().get(defaultKey) : null;
        }

        List<ResolvedGroup.Entry> entries = new ArrayList<>(values.size());
        for (String value : values) {
            String shown = value;
            if (catalog != null) {
                CatalogDefinition.Entry mapped = (value == null)
                        ? defaultEntry
                        : catalog.entries().getOrDefault(value, defaultEntry);
                if (mapped == null) continue;
                shown = mapped.display();
            }
            entries.add(new ResolvedGroup.Entry(
                    shown,
                    Placeholder.parsed(pdc.valueName(), shown == null ? "" : shown)
            ));
        }
        return new ResolvedGroup(entries);
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

    public void set(@NotNull ItemStack itemStack, @NotNull String name, @NotNull List<@Nullable String> values) {
        var definition = groupRegistry.get(name)
                .orElseThrow(() -> new DefinitionNotFoundException("Unknown group: " + name));

        if (!(definition.source() instanceof GroupDefinition.Source.PDC pdc)) {
            throw new NotPdcBackedException("Group '" + name + "' is not PDC-backed and cannot be set");
        }

        List<NullableString> wrapped = new ArrayList<>(values.size());
        for (String v : values) wrapped.add(new NullableString(v));

        itemStack.editMeta(meta ->
                meta.getPersistentDataContainer().set(keyFor(pdc), listType, wrapped));
    }

    private static @NotNull String formatValue(double value) {
        DecimalFormat df = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return df.format(value);
    }

    private static @NotNull String toRoman(int level) {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (level < 0 || level >= numerals.length) return String.valueOf(level);
        return numerals[level];
    }
}