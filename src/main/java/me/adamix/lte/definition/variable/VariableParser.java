package me.adamix.lte.definition.variable;

import me.adamix.kidl.Cardinality;
import me.adamix.kidl.Cursor;
import me.adamix.kidl.Kidl;
import me.adamix.kidl.diagnostic.Diagnostic;
import me.adamix.kidl.diagnostic.FileSpan;
import me.adamix.kidl.error.DiagnosticError;
import me.adamix.kidl.validator.Context;
import me.adamix.kidl.view.ChildKind;
import me.adamix.kidl.view.Properties;
import me.adamix.lte.LteErrors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VariableParser {
    private final Kidl kidl = Kidl.builder()
            .ansiRenderer()
            .standardExceptionTranslation()
            .build();

    enum VariableChild implements ChildKind {
        SOURCE("source", Cardinality.ONE),
        TRANSFORMER("transformer", Cardinality.OPTIONAL);

        private final String id;
        private final Cardinality cardinality;

        VariableChild(String id, Cardinality cardinality) {
            this.id = id;
            this.cardinality = cardinality;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Cardinality cardinality() {
            return cardinality;
        }
    }

    public @NotNull List<VariableDefinition> parseFile(@NotNull Path path) throws IOException {
        List<VariableDefinition> definitions = new ArrayList<>();
        Map<String, FileSpan> spans = new HashMap<>();

        var result = kidl.load(path, (id, ctx, cursor) -> {
            if (!id.equals("variable")) return;

            VariableDefinition definition = parseVariable(cursor, ctx);
            if (definition == null) return;

            FileSpan previous = spans.putIfAbsent(definition.name(), cursor.span());
            if (previous != null) {
                report(
                        ctx,
                        LteErrors.DUPLICATE_VARIABLE,
                        "variable `" + definition.name() + "` is defined more than once",
                        cursor.span(),
                        "redefined here"
                );
                return;
            }

            definitions.add(definition);
        });

        if (result.hasErrors()) {
            throw new IllegalStateException("Variable configuration contains errors");
        }

        return List.copyOf(definitions);
    }

    private @Nullable VariableDefinition parseVariable(@NotNull Cursor cursor, @NotNull Context ctx) {
        String variableName = cursor.arguments().getString(0);
        if (variableName == null) return null;

        var children = cursor.children(VariableChild.class);

        var transformerChildren = children.list(VariableChild.TRANSFORMER);
        String transformer = transformerChildren.isEmpty()
                ? null
                : transformerChildren.getFirst().arguments().getString(0);

        // Kidl still creates cursors for duplicated children. Consume their argument so its
        // `finish()` pass does not add a misleading "unexpected argument #0" warning.
        for (int i = 1; i < transformerChildren.size(); i++) {
            transformerChildren.get(i).arguments().getString(0);
        }

        var sourceCursor = children.get(VariableChild.SOURCE).orElse(null);
        if (sourceCursor == null) return null;

        VariableDefinition.Source source = parseSource(sourceCursor, ctx);
        if (source == null) return null;

        return new VariableDefinition(variableName, source, transformer);
    }

    private @Nullable VariableDefinition.Source parseSource(
            @NotNull Cursor sourceCursor,
            @NotNull Context ctx
    ) {
        String sourceId = sourceCursor.arguments().getString(0);
        if (sourceId == null) return null;

        return switch (sourceId) {
            case "pdc" -> parsePdcSource(sourceCursor, ctx);
            case "computed" -> parseComputedSource(sourceCursor);
            default -> {
                sourceCursor.properties().optString("key");
                sourceCursor.properties().optString("type");
                sourceCursor.properties().optString("fn");
                report(
                        ctx,
                        LteErrors.VARIABLE_UNKNOWN_SOURCE,
                        "unknown variable source `" + sourceId + "`",
                        sourceCursor.span(),
                        "use `pdc` or `computed`"
                );
                yield null;
            }
        };
    }

    private @Nullable VariableDefinition.Source parsePdcSource(
            @NotNull Cursor sourceCursor,
            @NotNull Context ctx
    ) {
        Properties properties = sourceCursor.properties();

        String key = properties.getString("key");
        String rawType = properties.getString("type");
        PdcValueType type = switch (rawType == null ? "" : rawType) {
            case "string" -> PdcValueType.STRING;
            case "int", "integer" -> PdcValueType.INT;
            case "double", "float" -> PdcValueType.DOUBLE;
            case "boolean", "bool" -> PdcValueType.BOOLEAN;
            default -> null;
        };

        if (rawType != null && type == null) {
            report(
                    ctx,
                    LteErrors.VARIABLE_UNKNOWN_TYPE,
                    "unknown PDC value type `" + rawType + "`",
                    sourceCursor.span(),
                    "use string, int, double, or boolean"
            );
        }

        VariableValue defaultValue = type == null ? null : parseDefault(properties, type);
        if (key == null || type == null) return null;

        return new VariableDefinition.Source.Pdc(key, type, defaultValue);
    }

    private @Nullable VariableDefinition.Source parseComputedSource(@NotNull Cursor sourceCursor) {
        String fn = sourceCursor.properties().getString("fn");
        VariableValue defaultValue = sourceCursor.properties().optString("default")
                .map(value -> VariableValue.of(value))
                .orElse(null);

        if (fn == null) return null;

        return new VariableDefinition.Source.Computed(fn, defaultValue);
    }

    private @Nullable VariableValue parseDefault(
            @NotNull Properties properties,
            @NotNull PdcValueType type
    ) {
        return switch (type) {
            case INT -> properties.optInt("default")
                    .map(value -> VariableValue.of(value.intValue()))
                    .orElse(null);
            case DOUBLE -> properties.optDouble("default")
                    .map(value -> VariableValue.of(value.doubleValue()))
                    .orElse(null);
            case STRING -> properties.optString("default")
                    .map(value -> VariableValue.of(value))
                    .orElse(null);
            case BOOLEAN -> properties.optBoolean("default")
                    .map(value -> VariableValue.of(value.booleanValue()))
                    .orElse(null);
        };
    }

    private static void report(
            @NotNull Context ctx,
            @NotNull DiagnosticError error,
            @NotNull String message,
            @NotNull FileSpan span,
            @NotNull String label
    ) {
        ctx.addDiagnostic(Diagnostic.of(error, message, span, label));
    }
}
