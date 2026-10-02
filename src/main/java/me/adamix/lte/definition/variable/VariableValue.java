package me.adamix.lte.definition.variable;

import org.jetbrains.annotations.NotNull;

public sealed interface VariableValue {
    record IntValue(int value) implements VariableValue {}
    record DoubleValue(double value) implements VariableValue {}
    record StringValue(@NotNull String value) implements VariableValue {}
    record BooleanValue(boolean value) implements VariableValue {}

    static @NotNull String stringify(@NotNull VariableValue value) {
        return switch (value) {
            case IntValue(int v) -> String.valueOf(v);
            case DoubleValue(double v) -> String.valueOf(v);
            case StringValue(String v) -> v;
            case BooleanValue(boolean v) -> String.valueOf(v);
        };
    }

    static @NotNull IntValue of(int value) {
        return new IntValue(value);
    }

    static @NotNull DoubleValue of(double value) {
        return new DoubleValue(value);
    }

    static @NotNull StringValue of(@NotNull String value) {
        return new StringValue(value);
    }

    static @NotNull BooleanValue of(boolean value) {
        return new BooleanValue(value);
    }
}