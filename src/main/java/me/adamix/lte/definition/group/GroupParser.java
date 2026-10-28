package me.adamix.lte.definition.group;

import me.adamix.kidl.Cardinality;
import me.adamix.kidl.Cursor;
import me.adamix.kidl.Kidl;
import me.adamix.kidl.diagnostic.Diagnostic;
import me.adamix.kidl.diagnostic.FileSpan;
import me.adamix.kidl.error.DiagnosticError;
import me.adamix.kidl.validator.Context;
import me.adamix.kidl.view.ChildKind;
import me.adamix.lte.LteErrors;
import me.adamix.lte.api.exception.ConfigurationException;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GroupParser {
    private final Kidl kidl = Kidl.builder()
            .ansiRenderer()
            .standardExceptionTranslation()
            .build();

    enum GroupChild implements ChildKind {
        BUILTIN("builtin", Cardinality.OPTIONAL),
        SOURCE("source", Cardinality.OPTIONAL),
        VALUE_NAME("value-name", Cardinality.OPTIONAL),
        TRANSFORMER("transformer", Cardinality.OPTIONAL);

        private final String id;
        private final Cardinality cardinality;

        GroupChild(String id, Cardinality cardinality) {
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

    public @NotNull List<GroupDefinition> parseFile(@NotNull Path path) throws IOException {
        List<GroupDefinition> definitions = new ArrayList<>();
        Map<String, FileSpan> spans = new HashMap<>();

        var result = kidl.load(path, (id, ctx, cursor) -> {
            if (!id.equals("group")) return;

            String groupName = cursor.arguments().getString(0);
            if (groupName == null) return;

            GroupDefinition definition = parseGroup(cursor, ctx);
            if (definition == null) return;

            FileSpan previous = spans.putIfAbsent(groupName, cursor.span());
            if (previous != null) {
                report(
                        ctx,
                        LteErrors.DUPLICATE_GROUP,
                        "group `" + groupName + "` is defined more than once",
                        cursor.span(),
                        "redefined here"
                );
                return;
            }

            definitions.add(definition);
        });

        if (result.hasErrors()) {
            throw new ConfigurationException("Group configuration contains errors");
        }

        return List.copyOf(definitions);
    }

    private @Nullable GroupDefinition parseGroup(@NotNull Cursor cursor, @NotNull Context ctx) {
        String groupName = cursor.arguments().getString(0);
        if (groupName == null) return null;

        var children = cursor.children(GroupChild.class);

        var builtinCursor = children.get(GroupChild.BUILTIN).orElse(null);
        var sourceCursor = children.get(GroupChild.SOURCE).orElse(null);

        String valueName = children.get(GroupChild.VALUE_NAME)
                .map(child -> child.arguments().getString(0))
                .orElse(null);

        var transformerCursor = children.get(GroupChild.TRANSFORMER).orElse(null);
        TransformerBinding transformer = null;
        if (transformerCursor != null) {
            String transformerName = transformerCursor.arguments().getString(0);
            if (transformerName != null) {
                String key = transformerCursor.properties().optString("key").orElse(null);
                transformer = new TransformerBinding(transformerName, key, transformerCursor.span());
            }
        }

        GroupDefinition.Source.Builtin builtin = null;
        if (builtinCursor != null) {
            builtin = parseBuiltin(builtinCursor, ctx);
        }

        String sourceId = null;
        String pdcKey = null;
        if (sourceCursor != null) {
            sourceId = sourceCursor.arguments().getString(0);
            if ("pdc".equals(sourceId)) {
                pdcKey = sourceCursor.properties().getString("key");
                sourceCursor.properties().optString("type");
            } else {
                sourceCursor.properties().optString("key");
                sourceCursor.properties().optString("type");
                if (sourceId != null) {
                    report(
                            ctx,
                            LteErrors.GROUP_UNKNOWN_SOURCE,
                            "unknown group source `" + sourceId + "`",
                            sourceCursor.span(),
                            "only `pdc` is supported"
                    );
                }
            }
        }

        if (builtin != null) {
            if (sourceCursor != null || valueName != null) {
                report(
                        ctx,
                        LteErrors.GROUP_SOURCE_CONFLICT,
                        "group `" + groupName + "` defines `builtin` together with PDC settings",
                        cursor.span(),
                        "use either `builtin` or a `source` definition"
                );
                return null;
            }

            if (transformer != null && transformer.key() == null) {
                report(
                        ctx,
                        LteErrors.GROUP_TRANSFORMER_KEY_REQUIRED,
                        "group `" + groupName + "` uses a transformer with a builtin source but has no `key`",
                        transformerCursor.span(),
                        "add `key=\"...\"` to the `transformer` child"
                );
                return null;
            }

            return new GroupDefinition(groupName, builtin, transformer);
        }

        if (sourceCursor == null) {
            report(
                    ctx,
                    LteErrors.GROUP_MISSING_SOURCE,
                    "group `" + groupName + "` does not define a source",
                    cursor.span(),
                    "add a `builtin` or `source` child"
            );
            return null;
        }

        if (!"pdc".equals(sourceId)) {
            return null;
        }

        if (pdcKey == null) {
            return null;
        }

        if (valueName == null) {
            report(
                    ctx,
                    LteErrors.GROUP_MISSING_VALUE_NAME,
                    "group `" + groupName + "` using a PDC source requires a `value-name`",
                    cursor.span(),
                    "add a `value-name` child"
            );
            return null;
        }

        if (transformer != null && transformer.key() != null) {
            report(
                    ctx,
                    LteErrors.GROUP_TRANSFORMER_KEY_UNEXPECTED,
                    "group `" + groupName + "` uses a transformer with a PDC source and a `key`",
                    transformerCursor.span(),
                    "remove `key`; PDC groups transform their stored values directly"
            );
            return null;
        }

        return new GroupDefinition(
                groupName,
                new GroupDefinition.Source.PDC(pdcKey, valueName),
                transformer
        );
    }

    private @Nullable GroupDefinition.Source.Builtin parseBuiltin(
            @NotNull Cursor builtinCursor,
            @NotNull Context ctx
    ) {
        String builtinName = builtinCursor.arguments().getString(0);
        if (builtinName == null) return null;

        String rawSlot = builtinCursor.properties().optString("slot").orElse(null);
        String slot = null;
        if (rawSlot != null) {
            slot = normalizeSlot(rawSlot);
            if (slot == null) {
                report(
                        ctx,
                        LteErrors.GROUP_UNKNOWN_SLOT,
                        "unknown builtin slot `" + rawSlot + "`",
                        builtinCursor.span(),
                        "not a recognised equipment slot"
                );
            }
        }

        return new GroupDefinition.Source.Builtin(builtinName, slot);
    }

    private static @Nullable String normalizeSlot(@NotNull String raw) {
        String key = raw.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        EquipmentSlotGroup group = EquipmentSlotGroup.getByName(key);
        return group != null ? group.toString() : null;
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
