package me.adamix.lte.definition.variable;

import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public enum PdcValueType {
    INT(PersistentDataType.INTEGER),
    DOUBLE(PersistentDataType.DOUBLE),
    STRING(PersistentDataType.STRING),
    BOOLEAN(PersistentDataType.BOOLEAN);

    private final PersistentDataType<?, ?> pdcType;

    PdcValueType(@NotNull PersistentDataType<?, ?> pdcType) {
        this.pdcType = pdcType;
    }

    public @NotNull PersistentDataType<?, ?> pdcType() {
        return pdcType;
    }
}