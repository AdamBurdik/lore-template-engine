package me.adamix.lte;

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

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DefinitionValidatorTest {

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

        assertDoesNotThrow(() -> DefinitionValidator.validate(
                transformers,
                variables,
                groups,
                templates
        ));
    }
}
