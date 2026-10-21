package me.adamix.lte.definition.transformer;

import me.adamix.kidl.Cardinality;
import me.adamix.kidl.Cursor;
import me.adamix.kidl.Kidl;
import me.adamix.kidl.diagnostic.Diagnostic;
import me.adamix.kidl.diagnostic.FileSpan;
import me.adamix.kidl.error.DiagnosticError;
import me.adamix.kidl.validator.Context;
import me.adamix.kidl.view.ChildKind;
import me.adamix.kidl.view.TypedCursor;
import me.adamix.lte.LteErrors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TransformerParser {
    private final Kidl kidl = Kidl.builder()
            .ansiRenderer()
            .standardExceptionTranslation()
            .build();

    enum TransformerChild implements ChildKind {
        ENTRY("entry", Cardinality.MANY),
        FALLBACK("fallback", Cardinality.OPTIONAL);

        private final String id;
        private final Cardinality cardinality;

        TransformerChild(String id, Cardinality cardinality) {
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

    enum EntryChild implements ChildKind {
        COLOR("color", Cardinality.OPTIONAL),
        ICON("icon", Cardinality.OPTIONAL),
        LABEL("label", Cardinality.OPTIONAL);

        private final String id;
        private final Cardinality cardinality;

        EntryChild(String id, Cardinality cardinality) {
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

    public @NotNull List<TransformerDefinition> parseFile(@NotNull Path path) throws IOException {
        List<TransformerDefinition> definitions = new ArrayList<>();
        Map<String, FileSpan> spans = new HashMap<>();

        var result = kidl.load(path, (id, ctx, cursor) -> {
            if (!id.equals("transformer")) return;

            String transformerName = cursor.arguments().getString(0);
            if (transformerName == null) return;

            TransformerDefinition definition = parseTransformer(cursor, transformerName, ctx);
            if (definition == null) return;

            FileSpan previous = spans.putIfAbsent(definition.name(), cursor.span());
            if (previous != null) {
                report(
                        ctx,
                        LteErrors.DUPLICATE_TRANSFORMER,
                        "transformer `" + definition.name() + "` is defined more than once",
                        cursor.span(),
                        "redefined here"
                );
                return;
            }

            definitions.add(definition);
        });

        if (result.hasErrors()) {
            throw new IllegalStateException("Transformer configuration contains errors");
        }

        return List.copyOf(definitions);
    }

    private @Nullable TransformerDefinition parseTransformer(
            @NotNull Cursor cursor,
            @NotNull String transformerName,
            @NotNull Context ctx
    ) {
        Map<String, TransformerDefinition.Entry> entries = new LinkedHashMap<>();
        TransformerDefinition.Entry fallback = null;

        for (TypedCursor<TransformerChild> child : cursor.children(TransformerChild.class)) {
            switch (child.kind()) {
                case ENTRY -> {
                    TransformerDefinition.Entry entry = parseEntry(child);
                    if (entry == null) continue;

                    if (entries.putIfAbsent(entry.id(), entry) != null) {
                        report(
                                ctx,
                                LteErrors.DUPLICATE_TRANSFORMER_ENTRY,
                                "entry `" + entry.id() + "` is defined more than once in transformer `" +
                                        transformerName + "`",
                                child.span(),
                                "redefined here"
                        );
                    }
                }
                case FALLBACK -> {
                    if (fallback != null) {
                        report(
                                ctx,
                                LteErrors.DUPLICATE_TRANSFORMER_FALLBACK,
                                "transformer `" + transformerName + "` defines `fallback` more than once",
                                child.span(),
                                "already defined"
                        );
                        continue;
                    }

                    fallback = parseFields(child, "fallback");
                }
            }
        }

        return new TransformerDefinition(transformerName, entries, fallback);
    }

    private @Nullable TransformerDefinition.Entry parseEntry(@NotNull Cursor cursor) {
        String entryId = cursor.arguments().getString(0);
        if (entryId == null) return null;

        return parseFields(cursor, entryId);
    }

    private @NotNull TransformerDefinition.Entry parseFields(
            @NotNull Cursor cursor,
            @NotNull String entryId
    ) {
        String color = null;
        String icon = null;
        String label = null;

        for (TypedCursor<EntryChild> child : cursor.children(EntryChild.class)) {
            String value = child.arguments().getString(0);
            switch (child.kind()) {
                case COLOR -> color = value;
                case ICON -> icon = value;
                case LABEL -> label = value;
            }
        }

        return new TransformerDefinition.Entry(entryId, color, icon, label);
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
