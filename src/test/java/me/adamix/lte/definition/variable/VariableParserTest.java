package me.adamix.lte.definition.variable;

import me.adamix.lte.definition.ParserTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class VariableParserTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsMultipleTransformers() throws IOException {
        Path file = ParserTestSupport.write(tempDir, "duplicate-transformer.kdl", """
                variable "rarity" {
                    source "pdc" key="rarity" type="string" default="common"
                    transformer "rarity_lookup"
                    transformer "rarity_lookup"
                }
                """);

        ParserTestSupport.Failure failure = ParserTestSupport.captureFailure(
                () -> new VariableParser().parseFile(file)
        );

        ParserTestSupport.assertFailure(
                failure,
                "Variable configuration contains errors",
                4,
                5,
                "child `transformer` may only be defined once"
        );
        assertFalse(
                failure.output().contains("unexpected argument #0"),
                () -> "duplicate transformer should not produce an unexpected-argument warning:\n"
                        + failure.output()
        );
    }
}
