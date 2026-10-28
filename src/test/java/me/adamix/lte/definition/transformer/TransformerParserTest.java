package me.adamix.lte.definition.transformer;

import me.adamix.lte.definition.ParserTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransformerParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesProductionTransformers() throws IOException {
        List<TransformerDefinition> transformers = new TransformerParser()
                .parseFile(ParserTestSupport.resource("/transformers.kdl"));

        assertEquals(3, transformers.size());

        Map<String, TransformerDefinition> byName = transformers.stream()
                .collect(Collectors.toMap(TransformerDefinition::name, Function.identity()));

        TransformerDefinition rarity = byName.get("rarity_lookup");
        assertEquals(
                Map.of(
                        "common", entry("common", "<gray>", null, "Common"),
                        "uncommon", entry("uncommon", "<green>", null, "Uncommon"),
                        "rare", entry("rare", "<blue>", null, "Rare"),
                        "epic", entry("epic", "<light_purple><bold>", null, "Epic"),
                        "legendary", entry("legendary", "<gold><bold>", null, "Legendary")
                ),
                rarity.entries()
        );
        assertEquals(entry("fallback", "<gray>", null, "Unknown"), rarity.fallback());

        TransformerDefinition gems = byName.get("gem_lookup");
        assertEquals(entry("empty", "<dark_gray>", "[ ] ", "Empty Socket"), gems.entries().get("empty"));
        assertEquals(entry("chipped_ruby", "<gray>", null, "Chipped Ruby"), gems.entries().get("chipped_ruby"));
        assertEquals(entry("pristine_ruby", "<aqua>", null, "Pristine Ruby"), gems.entries().get("pristine_ruby"));

        TransformerDefinition enchantments = byName.get("ench_style");
        assertEquals(entry("mending", "<gold>", "MEN", null), enchantments.entries().get("mending"));
        assertEquals(entry("protection", "<aqua>", "PROT", null), enchantments.entries().get("protection"));
        assertEquals(entry("thorns", "<green>", "THOR", null), enchantments.entries().get("thorns"));
        assertEquals(entry("fallback", "<gray>", "", null), enchantments.fallback());
    }

    @Test
    void rejectsDuplicateEntries() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "duplicate-entry.kdl", """
                transformer "dup" {
                    entry "a" {
                    }
                    entry "a" {
                    }
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new TransformerParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Transformer configuration contains errors",
                4,
                5,
                "entry `a` is defined more than once in transformer `dup`"
        );
    }

    @Test
    void rejectsDuplicateFallbacks() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "duplicate-fallback.kdl", """
                transformer "dup" {
                    fallback {
                    }
                    fallback {
                    }
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new TransformerParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Transformer configuration contains errors",
                4,
                5,
                "defines `fallback` more than once"
        );
    }

    @Test
    void parsesCustomEntryFieldNames() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "custom-fields.kdl", """
                transformer "custom" {
                    entry "a" {
                        display "Custom"
                        weight "heavy"
                    }
                    fallback {
                        display "Unknown"
                    }
                }
                """);

        TransformerDefinition definition = new TransformerParser().parseFile(file).getFirst();

        assertEquals(
                Map.of("display", "Custom", "weight", "heavy"),
                definition.entries().get("a").fields()
        );
        assertEquals(
                Optional.of("Custom"),
                definition.entries().get("a").field("display")
        );
        assertEquals(Map.of("display", "Unknown"), definition.fallback().fields());
    }

    @Test
    void entryFieldsOnlyContainDefinedValues() {
        TransformerDefinition.Entry entry = new TransformerDefinition.Entry(
                "a",
                Map.of("color", "<gray>")
        );

        assertEquals(Map.of("color", "<gray>"), entry.fields());
        assertEquals(Optional.of("<gray>"), entry.field("color"));
    }

    private static TransformerDefinition.Entry entry(
            String id,
            String color,
            String icon,
            String label
    ) {
        Map<String, String> fields = new HashMap<>();
        if (color != null) fields.put("color", color);
        if (icon != null) fields.put("icon", icon);
        if (label != null) fields.put("label", label);
        return new TransformerDefinition.Entry(id, fields);
    }
}
