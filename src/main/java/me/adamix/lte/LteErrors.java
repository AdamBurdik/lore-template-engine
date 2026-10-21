package me.adamix.lte;

import me.adamix.kidl.error.DiagnosticError;
import me.adamix.kidl.error.Severity;
import org.jetbrains.annotations.NotNull;

public final class LteErrors {
    private LteErrors() {}

    public static final DiagnosticError
            UNKNOWN_TEMPLATE = error("UNKNOWN_TEMPLATE"),
            UNKNOWN_SECTION = error("UNKNOWN_SECTION"),
            CIRCULAR_EXTENDS = error("CIRCULAR_EXTENDS"),
            DUPLICATE_TEMPLATE = error("DUPLICATE_TEMPLATE"),
            DUPLICATE_SECTION = error("DUPLICATE_SECTION"),
            INSERT_WITHOUT_EXTENDS = error("INSERT_WITHOUT_EXTENDS"),
            DUPLICATE_GROUP = error("DUPLICATE_GROUP"),
            DUPLICATE_VARIABLE = error("DUPLICATE_VARIABLE"),
            DUPLICATE_TRANSFORMER = error("DUPLICATE_TRANSFORMER"),
            DUPLICATE_TRANSFORMER_ENTRY = error("DUPLICATE_TRANSFORMER_ENTRY"),
            DUPLICATE_TRANSFORMER_FALLBACK = error("DUPLICATE_TRANSFORMER_FALLBACK"),
            GROUP_SOURCE_CONFLICT = error("GROUP_SOURCE_CONFLICT"),
            GROUP_MISSING_SOURCE = error("GROUP_MISSING_SOURCE"),
            GROUP_MISSING_VALUE_NAME = error("GROUP_MISSING_VALUE_NAME"),
            GROUP_UNKNOWN_SLOT = error("GROUP_UNKNOWN_SLOT"),
            GROUP_UNKNOWN_SOURCE = error("GROUP_UNKNOWN_SOURCE"),
            GROUP_TRANSFORMER_KEY_REQUIRED = error("GROUP_TRANSFORMER_KEY_REQUIRED"),
            GROUP_TRANSFORMER_KEY_UNEXPECTED = error("GROUP_TRANSFORMER_KEY_UNEXPECTED"),
            VARIABLE_UNKNOWN_SOURCE = error("VARIABLE_UNKNOWN_SOURCE"),
            VARIABLE_UNKNOWN_TYPE = error("VARIABLE_UNKNOWN_TYPE");

    private static DiagnosticError error(@NotNull String path) {
        return new DiagnosticError("lte", path, Severity.ERROR);
    }
}
