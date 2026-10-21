package me.adamix.lte.definition.group;

import me.adamix.lte.definition.ParserTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class GroupParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesProductionGroups() throws IOException {
        List<GroupDefinition> groups = new GroupParser()
                .parseFile(ParserTestSupport.resource("/groups.kdl"));

        assertEquals(13, groups.size());
        assertEquals(
                Set.of(
                        "enchantments",
                        "styled_enchantments",
                        "attributes",
                        "attributes-any",
                        "attributes-main-hand",
                        "attributes-off-hand",
                        "attributes-head",
                        "attributes-chest",
                        "attributes-legs",
                        "attributes-feet",
                        "tags",
                        "description",
                        "gem_sockets"
                ),
                groups.stream().map(GroupDefinition::name).collect(Collectors.toSet())
        );

        Map<String, GroupDefinition> byName = groups.stream()
                .collect(Collectors.toMap(GroupDefinition::name, Function.identity()));

        assertBuiltin(byName.get("enchantments"), "enchantments", null);
        assertBuiltin(byName.get("styled_enchantments"), "enchantments", null);
        assertBuiltin(byName.get("attributes"), "attributes", null);
        assertBuiltin(byName.get("attributes-any"), "attributes", "any");
        assertBuiltin(byName.get("attributes-main-hand"), "attributes", "mainhand");
        assertBuiltin(byName.get("attributes-off-hand"), "attributes", "offhand");
        assertBuiltin(byName.get("attributes-head"), "attributes", "head");
        assertBuiltin(byName.get("attributes-chest"), "attributes", "chest");
        assertBuiltin(byName.get("attributes-legs"), "attributes", "legs");
        assertBuiltin(byName.get("attributes-feet"), "attributes", "feet");

        assertPdc(byName.get("tags"), "tags", "tag", null, null);
        assertPdc(byName.get("description"), "description", "line", null, null);
        assertPdc(byName.get("gem_sockets"), "gem_sockets", "gem_socket", "gem_lookup", null);

        assertTransformer(byName.get("styled_enchantments"), "ench_style", "ench_id");
        assertTransformer(byName.get("gem_sockets"), "gem_lookup", null);
    }

    @Test
    void parsesPdcGroupWithTransformer() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "gem-sockets.kdl", """
                group "gem_sockets" {
                    source "pdc" key="gem_sockets" type="string_list"
                    value-name "gem_socket"
                    transformer "gem_lookup"
                }
                """);

        List<GroupDefinition> groups = new GroupParser().parseFile(file);

        assertEquals(1, groups.size());
        assertPdc(groups.getFirst(), "gem_sockets", "gem_socket", "gem_lookup", null);
    }

    @Test
    void parsesBuiltinGroupWithTransformerKey() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "styled-enchantments.kdl", """
                group "styled_enchantments" {
                    builtin "enchantments"
                    transformer "ench_style" key="ench_id"
                }
                """);

        List<GroupDefinition> groups = new GroupParser().parseFile(file);

        assertEquals(1, groups.size());
        assertBuiltin(groups.getFirst(), "enchantments", null);
        assertTransformer(groups.getFirst(), "ench_style", "ench_id");
    }

    @Test
    void rejectsBuiltinTransformerWithoutKey() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "missing-key.kdl", """
                group "styled_enchantments" {
                    builtin "enchantments"
                    transformer "ench_style"
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new GroupParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Group configuration contains errors",
                3,
                5,
                "has no `key`"
        );
    }

    @Test
    void rejectsPdcTransformerWithKey() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "unexpected-key.kdl", """
                group "gem_sockets" {
                    source "pdc" key="gem_sockets"
                    value-name "gem_socket"
                    transformer "gem_lookup" key="gem_socket"
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new GroupParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Group configuration contains errors",
                4,
                5,
                "and a `key`"
        );
    }

    @Test
    void rejectsDuplicateGroups() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "duplicate.kdl", """
                group "dup" {
                    builtin "enchantments"
                }
                group "dup" {
                    builtin "enchantments"
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new GroupParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Group configuration contains errors",
                4,
                1,
                "group `dup` is defined more than once"
        );
    }

    @Test
    void rejectsBuiltinAndPdcConflict() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "conflict.kdl", """
                group "conflict" {
                    builtin "enchantments"
                    source "pdc" key="x"
                    value-name "v"
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new GroupParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Group configuration contains errors",
                1,
                1,
                "defines `builtin` together with PDC settings"
        );
    }

    @Test
    void rejectsPdcGroupWithoutValueName() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "missing-value-name.kdl", """
                group "missing" {
                    source "pdc" key="x"
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new GroupParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Group configuration contains errors",
                1,
                1,
                "requires a `value-name`"
        );
    }

    @Test
    void rejectsUnknownBuiltinSlot() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "unknown-slot.kdl", """
                group "slot" {
                    builtin "attributes" slot="banana"
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new GroupParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Group configuration contains errors",
                2,
                5,
                "unknown builtin slot `banana`"
        );
    }

    @Test
    void rejectsUnknownSource() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "unknown-source.kdl", """
                group "source" {
                    source "wat" key="x"
                    value-name "v"
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new GroupParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Group configuration contains errors",
                2,
                5,
                "unknown group source `wat`"
        );
    }

    @Test
    void rejectsGroupWithoutSource() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "missing-source.kdl", """
                group "empty" {
                    transformer "gem_lookup"
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new GroupParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Group configuration contains errors",
                1,
                1,
                "does not define a source"
        );
    }

    @Test
    void missingSourceArgumentUsesSourceSpan() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "missing-argument.kdl", """
                group "missing-arg" {
                    source
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new GroupParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Group configuration contains errors",
                2,
                5,
                "missing required argument #0"
        );
    }

    private static void assertBuiltin(GroupDefinition definition, String name, String slot) {
        GroupDefinition.Source.Builtin builtin = assertInstanceOf(
                GroupDefinition.Source.Builtin.class,
                definition.source()
        );

        assertEquals(name, builtin.name());
        assertEquals(slot, builtin.slot());
    }

    private static void assertPdc(
            GroupDefinition definition,
            String key,
            String valueName,
            String transformer,
            String transformerKey
    ) {
        GroupDefinition.Source.PDC pdc = assertInstanceOf(
                GroupDefinition.Source.PDC.class,
                definition.source()
        );

        assertEquals(key, pdc.key());
        assertEquals(valueName, pdc.valueName());
        assertTransformer(definition, transformer, transformerKey);
    }

    private static void assertTransformer(
            GroupDefinition definition,
            String name,
            String key
    ) {
        if (name == null) {
            assertNull(definition.transformer());
            return;
        }

        TransformerBinding binding = definition.transformer();
        assertEquals(name, binding.name());
        assertEquals(key, binding.key());
    }
}
