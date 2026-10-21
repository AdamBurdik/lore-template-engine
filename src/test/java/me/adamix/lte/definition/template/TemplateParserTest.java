package me.adamix.lte.definition.template;

import me.adamix.lte.definition.ParserTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesProductionTemplates() throws IOException {
        List<LoreTemplateDefinition> templates = new TemplateParser()
                .parseFile(ParserTestSupport.resource("/templates.kdl"));

        assertEquals(6, templates.size());
        assertTrue(
                templates.stream().allMatch(template -> template.extendsId().isEmpty()),
                "resolved templates should not retain an extension id"
        );

        Map<String, LoreTemplateDefinition> byId = templates.stream()
                .collect(Collectors.toMap(LoreTemplateDefinition::id, Function.identity()));

        assertEquals(
                Set.of(
                        "vanilla",
                        "vanilla_extended",
                        "vanilla_styled",
                        "vanilla_custom",
                        "vanilla_with_gems",
                        "weapon_default"
                ),
                byId.keySet()
        );

        LoreTemplateDefinition vanilla = byId.get("vanilla");
        assertEquals(16, vanilla.elements().size());
        assertEquals(Set.of("enchantments", "attributes"), vanilla.sections().keySet());
        assertEquals(Optional.empty(), vanilla.tooltipStyle());

        LoreTemplateDefinition extended = byId.get("vanilla_extended");
        assertEquals(19, extended.elements().size());
        assertEquals(Set.of("enchantments", "attributes"), extended.sections().keySet());
        assertEquals(Optional.empty(), extended.tooltipStyle());
        assertFalse(extended.elements().stream().anyMatch(TemplateElement.Insert.class::isInstance));
        TemplateElement.Variable rarity = extended.elements().stream()
                .filter(TemplateElement.Variable.class::isInstance)
                .map(TemplateElement.Variable.class::cast)
                .filter(variable -> variable.name().equals("rarity"))
                .findFirst()
                .orElseThrow();
        assertEquals(Optional.of("<color><label>"), rarity.display());
        assertTrue(
                extended.elements().stream().anyMatch(element ->
                        element instanceof TemplateElement.Group group && group.name().equals("description")
                )
        );

        LoreTemplateDefinition styled = byId.get("vanilla_styled");
        assertEquals(16, styled.elements().size());
        assertEquals(Set.of("enchantments", "attributes"), styled.sections().keySet());
        assertTrue(
                styled.elements().stream().anyMatch(element ->
                        element instanceof TemplateElement.Group group &&
                                group.name().equals("styled_enchantments")
                )
        );

        LoreTemplateDefinition custom = byId.get("vanilla_custom");
        assertEquals(16, custom.elements().size());
        assertEquals(Set.of("enchantments", "attributes"), custom.sections().keySet());
        assertEquals(Optional.of("simple"), custom.tooltipStyle());

        LoreTemplateDefinition withGems = byId.get("vanilla_with_gems");
        assertEquals(19, withGems.elements().size());
        assertEquals(Set.of("enchantments", "attributes"), withGems.sections().keySet());

        LoreTemplateDefinition weapon = byId.get("weapon_default");
        assertEquals(9, weapon.elements().size());
        assertEquals(Optional.empty(), weapon.extendsId());
    }

    @Test
    void parsesInlineElements() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "inline.kdl", """
                template "inline" tooltip-style="simple" {
                    text "Hello " "world" collapse-if-empty="text"
                    blank
                    variable "name" prefix="[" suffix="]"
                    group "gems" each="<x>" empty="KEEP" inline=#true joiner=", "
                    section "s" {
                        text "inside"
                    }
                }
                """);

        List<LoreTemplateDefinition> templates = new TemplateParser().parseFile(file);

        assertEquals(1, templates.size());

        LoreTemplateDefinition template = templates.getFirst();
        assertEquals("inline", template.id());
        assertEquals(Optional.empty(), template.extendsId());
        assertEquals(Optional.of("simple"), template.tooltipStyle());
        assertEquals(5, template.elements().size());

        TemplateElement.Text text = assertInstanceOf(
                TemplateElement.Text.class,
                template.elements().get(0)
        );
        assertEquals("Hello world", text.value());
        assertEquals(Optional.of("text"), text.collapseIfEmpty());

        TemplateElement.Blank blank = assertInstanceOf(
                TemplateElement.Blank.class,
                template.elements().get(1)
        );
        assertEquals(Optional.empty(), blank.collapseIfEmpty());

        TemplateElement.Variable variable = assertInstanceOf(
                TemplateElement.Variable.class,
                template.elements().get(2)
        );
        assertEquals("name", variable.name());
        assertEquals(Optional.of("["), variable.prefix());
        assertEquals(Optional.of("]"), variable.suffix());

        TemplateElement.Group group = assertInstanceOf(
                TemplateElement.Group.class,
                template.elements().get(3)
        );
        assertEquals("gems", group.name());
        assertEquals("<x>", group.each());
        assertEquals(TemplateElement.Group.Empty.KEEP, group.empty());
        assertTrue(group.inline());
        assertEquals(Optional.of(", "), group.joiner());

        TemplateElement.Text sectionText = assertInstanceOf(
                TemplateElement.Text.class,
                template.elements().get(4)
        );
        assertEquals("inside", sectionText.value());

        assertEquals(Set.of("s"), template.sections().keySet());
        assertEquals(1, template.sections().get("s").size());
    }

    @Test
    void inheritsParentElementsSectionsAndTooltip() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "inherit.kdl", """
                template "base" tooltip-style="simple" {
                    text "base"
                    section "s" {
                        text "section"
                    }
                }
                template "child" extends="base" {
                    text "child"
                }
                """);

        Map<String, LoreTemplateDefinition> byId = parseById(file);

        LoreTemplateDefinition base = byId.get("base");
        assertEquals(Optional.of("simple"), base.tooltipStyle());
        assertEquals(2, base.elements().size());

        LoreTemplateDefinition child = byId.get("child");
        assertEquals(Optional.empty(), child.extendsId());
        assertEquals(Optional.of("simple"), child.tooltipStyle());
        assertEquals(List.of("base", "section", "child"), textValues(child.elements()));
        assertEquals(Set.of("s"), child.sections().keySet());

        TemplateElement.Text sectionText = assertInstanceOf(
                TemplateElement.Text.class,
                child.sections().get("s").getFirst()
        );
        assertEquals("section", sectionText.value());
    }

    @Test
    void insertReplacesSectionContentsInOrder() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "insert.kdl", """
                template "base" {
                    section "s" {
                        text "one"
                        text "two"
                    }
                }
                template "child" extends="base" {
                    text "before"
                    insert "s"
                    text "after"
                }
                """);

        Map<String, LoreTemplateDefinition> byId = parseById(file);
        LoreTemplateDefinition child = byId.get("child");

        assertEquals(List.of("before", "one", "two", "after"), textValues(child.elements()));
        assertFalse(child.elements().stream().anyMatch(TemplateElement.Insert.class::isInstance));
        assertEquals(Set.of("s"), child.sections().keySet());
    }

    @Test
    void childTooltipOverridesParent() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "tooltip-override.kdl", """
                template "base" tooltip-style="simple" {
                }
                template "child" extends="base" tooltip-style="fancy" {
                }
                """);

        Map<String, LoreTemplateDefinition> byId = parseById(file);

        assertEquals(Optional.of("simple"), byId.get("base").tooltipStyle());
        assertEquals(Optional.of("fancy"), byId.get("child").tooltipStyle());
    }

    @Test
    void rejectsDuplicateTemplates() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "duplicate-template.kdl", """
                template "dup" {
                }
                template "dup" {
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new TemplateParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Template configuration contains errors",
                3,
                1,
                "template `dup` is defined more than once"
        );
    }

    @Test
    void rejectsDuplicateSections() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "duplicate-section.kdl", """
                template "dup" {
                    section "s" {
                    }
                    section "s" {
                    }
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new TemplateParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Template configuration contains errors",
                4,
                5,
                "section `s` is defined more than once"
        );
    }

    @Test
    void rejectsInsertWithoutExtends() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "insert-without-extends.kdl", """
                template "child" {
                    insert "s"
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new TemplateParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Template configuration contains errors",
                1,
                1,
                "uses `insert` but does not extend another template"
        );
    }

    @Test
    void rejectsUnknownParent() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "unknown-parent.kdl", """
                template "child" extends="missing" {
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new TemplateParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Template configuration contains errors",
                1,
                1,
                "unknown template `missing`"
        );
    }

    @Test
    void rejectsUnknownInsertedSection() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "unknown-section.kdl", """
                template "base" {
                }
                template "child" extends="base" {
                    insert "missing"
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new TemplateParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Template configuration contains errors",
                3,
                1,
                "inserts unknown section `missing` from `base`"
        );
    }

    @Test
    void rejectsCircularInheritance() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "circular.kdl", """
                template "a" extends="b" {
                }
                template "b" extends="a" {
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new TemplateParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Template configuration contains errors",
                "circular template extension involving"
        );
    }

    @Test
    void missingSectionArgumentUsesSectionSpan() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "missing-section-argument.kdl", """
                template "one" {
                    section "s" {
                    }
                    section {
                    }
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new TemplateParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Template configuration contains errors",
                4,
                5,
                "missing required argument #0"
        );
    }

    @Test
    void missingGroupEachUsesGroupSpan() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "missing-group-each.kdl", """
                template "one" {
                    group "g"
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new TemplateParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Template configuration contains errors",
                2,
                5,
                "missing required property `each`"
        );
    }

    private Map<String, LoreTemplateDefinition> parseById(Path file) throws IOException {
        return new TemplateParser().parseFile(file).stream()
                .collect(Collectors.toMap(LoreTemplateDefinition::id, Function.identity()));
    }

    private static List<String> textValues(List<TemplateElement> elements) {
        return elements.stream()
                .map(TemplateElement.Text.class::cast)
                .map(TemplateElement.Text::value)
                .toList();
    }
}
