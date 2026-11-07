package me.adamix.lte.types;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class NullableStringType implements PersistentDataType<PersistentDataContainer, NullableString> {
    private final NamespacedKey presentKey;
    private final NamespacedKey valueKey;

    public NullableStringType(Plugin plugin) {
        this.presentKey = new NamespacedKey(plugin, "present");
        this.valueKey = new NamespacedKey(plugin, "value");
    }

    @Override
    public Class<PersistentDataContainer> getPrimitiveType() {
        return PersistentDataContainer.class;
    }

    @Override
    public Class<NullableString> getComplexType() {
        return NullableString.class;
    }

    @Override
    public PersistentDataContainer toPrimitive(
            NullableString complex,
            PersistentDataAdapterContext context
    ) {
        PersistentDataContainer container = context.newPersistentDataContainer();
        boolean present = complex.value() != null;
        container.set(presentKey, PersistentDataType.BOOLEAN, present);
        if (present) {
            container.set(valueKey, PersistentDataType.STRING, complex.value());
        }
        return container;
    }

    @Override
    public NullableString fromPrimitive(
            PersistentDataContainer primitive,
            PersistentDataAdapterContext context
    ) {
        Boolean present = primitive.get(presentKey, PersistentDataType.BOOLEAN);
        if (present == null || !present) {
            return new NullableString(null);
        }
        return new NullableString(primitive.get(valueKey, PersistentDataType.STRING));
    }
}