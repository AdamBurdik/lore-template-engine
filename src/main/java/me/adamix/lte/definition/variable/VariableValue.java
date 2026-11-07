package me.adamix.lte.definition.variable;

public sealed interface VariableValue {
    record IntValue(int value) implements VariableValue {}
    record DoubleValue(double value) implements VariableValue {}
    record StringValue(String value) implements VariableValue {}
    record BooleanValue(boolean value) implements VariableValue {}

    static String stringify(VariableValue value) {
        return switch (value) {
            case IntValue(int v) -> String.valueOf(v);
            case DoubleValue(double v) -> String.valueOf(v);
            case StringValue(String v) -> v;
            case BooleanValue(boolean v) -> String.valueOf(v);
        };
    }

    static IntValue of(int value) {
        return new IntValue(value);
    }

    static DoubleValue of(double value) {
        return new DoubleValue(value);
    }

    static StringValue of(String value) {
        return new StringValue(value);
    }

    static BooleanValue of(boolean value) {
        return new BooleanValue(value);
    }
}