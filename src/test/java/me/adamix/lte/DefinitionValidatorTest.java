package me.adamix.lte;

import me.adamix.kidl.diagnostic.Diagnostic;
import me.adamix.lte.definition.ParserTestSupport;
import me.adamix.lte.definition.group.GroupDefinition;
import me.adamix.lte.definition.group.GroupParser;
import me.adamix.lte.definition.template.LoreTemplateDefinition;
import me.adamix.lte.definition.template.TemplateParser;
import me.adamix.lte.definition.transformer.TransformerDefinition;
import me.adamix.lte.definition.transformer.TransformerParser;
import me.adamix.lte.definition.variable.VariableDefinition;
import me.adamix.lte.definition.variable.VariableParser;
import me.adamix.lte.registry.Registry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void productionDefinitionsAreValid() throws IOException {
        Registry<TransformerDefinition> transformers = new Registry<>();
        new TransformerParser()
                .parseFile(ParserTestSupport.resource("/transformers.kdl"))
                .forEach(definition -> transformers.register(definition.name(), definition));

        Registry<VariableDefinition> variables = new Registry<>();
        new VariableParser()
                .parseFile(ParserTestSupport.resource("/variables.kdl"))
                .forEach(definition -> variables.register(definition.name(), definition));

        Registry<GroupDefinition> groups = new Registry<>();
        new GroupParser()
                .parseFile(ParserTestSupport.resource("/groups.kdl"))
                .forEach(definition -> groups.register(definition.name(), definition));

        Registry<LoreTemplateDefinition> templates = new Registry<>();
        new TemplateParser()
                .parseFile(ParserTestSupport.resource("/templates.kdl"))
                .forEach(definition -> templates.register(definition.id(), definition));

        List<Diagnostic> diagnostics = DefinitionValidator.validate(
                transformers,
                variables,
                groups,
                templates
        );

        assertTrue(diagnostics.isEmpty(), () -> "Unexpected diagnostics: " + diagnostics);
    }

    @Test
    void missingTransformerDisplayReportsDiagnostic() throws IOException {
        Path transformersFile = ParserTestSupport.write(tempDir, "transformers.kdl", """
                transformer "rarity_lookup" {
                    entry "rare" {
                        color "<blue>"
                        label "Rare"
                    }
                }
                """);
        Path variablesFile = ParserTestSupport.write(tempDir, "variables.kdl", """
                variable "rarity" {
                    source "pdc" key="rarity" type="string" default="common"
                    transformer "rarity_lookup"
                }
                """);
        Path templatesFile = ParserTestSupport.write(tempDir, "templates.kdl", """
                template "vanilla_extended" {
                    var "rarity"
                }
                """);

        Registry<TransformerDefinition> transformers = new Registry<>();
        new TransformerParser()
                .parseFile(transformersFile)
                .forEach(definition -> transformers.register(definition.name(), definition));

        Registry<VariableDefinition> variables = new Registry<>();
        new VariableParser()
                .parseFile(variablesFile)
                .forEach(definition -> variables.register(definition.name(), definition));

        Registry<LoreTemplateDefinition> templates = new Registry<>();
        new TemplateParser()
                .parseFile(templatesFile)
                .forEach(definition -> templates.register(definition.id(), definition));

        List<Diagnostic> diagnostics = DefinitionValidator.validate(
                transformers,
                variables,
                new Registry<>(),
                templates
        );

        assertEquals(1, diagnostics.size());
        Diagnostic diagnostic = diagnostics.getFirst();
        assertEquals(
                "template `vanilla_extended` variable `rarity` uses a transformer but has no `display`",
                diagnostic.message()
        );
        assertEquals(
                "templates.kdl",
                Path.of(diagnostic.span().file()).getFileName().toString()
        );
        assertEquals(2, diagnostic.span().start().line());
        assertEquals("add display=\"...\"", diagnostic.help());
    }

    @Test
    void arbitraryTransformerFieldsAreRecognized() throws IOException {
        Path transformersFile = ParserTestSupport.write(tempDir, "transformers.kdl", """
                transformer "rarity_lookup" {
                    entry "rare" {
                        weight "heavy"
                    }
                }
                """);
        Path variablesFile = ParserTestSupport.write(tempDir, "variables.kdl", """
                variable "rarity" {
                    source "pdc" key="rarity" type="string" default="common"
                    transformer "rarity_lookup"
                }
                """);
        Path templatesFile = ParserTestSupport.write(tempDir, "templates.kdl", """
                template "vanilla_extended" {
                    var "rarity" display="<weight>"
                }
                """);

        List<Diagnostic> diagnostics = validate(transformersFile, variablesFile, templatesFile);

        assertTrue(diagnostics.isEmpty(), () -> "Unexpected diagnostics: " + diagnostics);
    }

    @Test
    void transformerFieldWithoutTransformerIsReported() throws IOException {
        Path transformersFile = ParserTestSupport.write(tempDir, "transformers.kdl", """
                transformer "rarity_lookup" {
                    entry "rare" {
                        weight "heavy"
                    }
                }
                """);
        Path variablesFile = ParserTestSupport.write(tempDir, "variables.kdl", "");
        Path templatesFile = ParserTestSupport.write(tempDir, "templates.kdl", """
                template "vanilla_extended" {
                    text "<weight>"
                }
                """);

        List<Diagnostic> diagnostics = validate(transformersFile, variablesFile, templatesFile);

        assertEquals(1, diagnostics.size());
        assertEquals(
                "template `vanilla_extended` text uses transformer field(s) [weight] without a transformer",
                diagnostics.getFirst().message()
        );
    }

    @Test
    void transformerFieldNotProvidedIsReported() throws IOException {
        Path transformersFile = ParserTestSupport.write(tempDir, "transformers.kdl", """
                transformer "rarity_lookup" {
                    entry "rare" {
                        weight "heavy"
                    }
                }
                transformer "ench_style" {
                    entry "mending" {
                        color "<gold>"
                    }
                }
                """);
        Path variablesFile = ParserTestSupport.write(tempDir, "variables.kdl", """
                variable "rarity" {
                    source "pdc" key="rarity" type="string" default="common"
                    transformer "rarity_lookup"
                }
                """);
        Path templatesFile = ParserTestSupport.write(tempDir, "templates.kdl", """
                template "vanilla_extended" {
                    var "rarity" display="<color>"
                }
                """);

        List<Diagnostic> diagnostics = validate(transformersFile, variablesFile, templatesFile);

        assertEquals(1, diagnostics.size());
        assertEquals(
                "template `vanilla_extended` variable `rarity` display uses field(s) [color] " +
                        "not provided by transformer `rarity_lookup`",
                diagnostics.getFirst().message()
        );
    }

    private List<Diagnostic> validate(
            Path transformersFile,
            Path variablesFile,
            Path templatesFile
    ) throws IOException {
        Registry<TransformerDefinition> transformers = new Registry<>();
        new TransformerParser()
                .parseFile(transformersFile)
                .forEach(definition -> transformers.register(definition.name(), definition));

        Registry<VariableDefinition> variables = new Registry<>();
        new VariableParser()
                .parseFile(variablesFile)
                .forEach(definition -> variables.register(definition.name(), definition));

        Registry<LoreTemplateDefinition> templates = new Registry<>();
        new TemplateParser()
                .parseFile(templatesFile)
                .forEach(definition -> templates.register(definition.id(), definition));

        return DefinitionValidator.validate(transformers, variables, new Registry<>(), templates);
    }
}
