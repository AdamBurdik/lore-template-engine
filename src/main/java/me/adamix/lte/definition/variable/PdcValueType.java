package me.adamix.lte.definition.variable;

import org.bukkit.persistence.PersistentDataType;

@SuppressWarnings("ImmutableEnumChecker")
public enum PdcValueType {
    INT(PersistentDataType.INTEGER),
    DOUBLE(PersistentDataType.DOUBLE),
    STRING(PersistentDataType.STRING),
    BOOLEAN(PersistentDataType.BOOLEAN);

    private final PersistentDataType<?, ?> pdcType;

    PdcValueType(PersistentDataType<?, ?> pdcType) {
        this.pdcType = pdcType;
    }

    public PersistentDataType<?, ?> pdcType() {
        return pdcType;
    }
}