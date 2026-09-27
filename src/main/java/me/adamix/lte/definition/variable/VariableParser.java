package me.adamix.lte.definition.variable;

import dev.kdl.KdlDocument;
import dev.kdl.KdlNode;
import dev.kdl.parse.KdlParseException;
import dev.kdl.parse.KdlParser;
import me.adamix.lte.exception.TemplateParsingException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VariableParser {

    public @NotNull List<VariableDefinition> parseFile(@NotNull Path path) throws KdlParseException, IOException, TemplateParsingException {
        var parser = KdlParser.v2();
        var document = parser.parse(path);

        return kdlToVariables(document);
    }

    public @NotNull List<VariableDefinition> kdlToVariables(@NotNull KdlDocument document) throws TemplateParsingException {
        List<VariableDefinition> definitions = new ArrayList<>();

        for (KdlNode node : document.nodes()) {
            if (!node.name().equals("variable")) continue;

            if (node.arguments().isEmpty()) {
                throw new TemplateParsingException("Variable requires at least one argument for its name");
            }

            var nameArg = node.arguments().getFirst();
            if (!nameArg.isString()) {
                throw new TemplateParsingException("Variable name must be a string");
            }

            String variableName = (String) nameArg.value();

            VariableDefinition.Source source = null;
            Map<String, String> lookupMap = new HashMap<>();
            String fallback = null;
            String defaultValue = null;

            for (KdlNode child : node.children()) {
                switch (child.name()) {
                    case "source" -> source = parseSource(child);
                    case "lookup" -> parseLookup(child, lookupMap);
                    case "fallback" -> fallback = parseSingleStringArgument(child, "fallback");
                    case "default" -> defaultValue = parseSingleStringArgument(child, "default");
                    default ->  throw new TemplateParsingException("Unknown value: "+ child.name());
                }
            }

            if (source == null) {
                throw new TemplateParsingException("Variable '" + variableName + "' must specify a source");
            }

            VariableDefinition.Lookup lookup = null;
            if (!lookupMap.isEmpty() || fallback != null) {
                lookup = new VariableDefinition.Lookup(lookupMap, fallback);
            }

            definitions.add(new VariableDefinition(variableName, source, defaultValue, lookup));
        }

        return definitions;
    }

    private VariableDefinition.Source parseSource(KdlNode child) throws TemplateParsingException {
        if (child.arguments().isEmpty()) {
            throw new TemplateParsingException("Source block requires an argument");
        }

        var sourceArg = child.arguments().getFirst();
        if (!sourceArg.isString()) {
            throw new TemplateParsingException("Source argument must be a string");
        }

        String sourceId = (String) sourceArg.value();

        return switch (sourceId) {
            case "pdc" -> {
                var keyProp = child.getProperty("key");
                if (keyProp.isEmpty() || !keyProp.get().isString()) {
                    throw new TemplateParsingException("PDC source requires a string 'key' property");
                }
                yield new VariableDefinition.Source.PDC((String) keyProp.get().value());
            }
            case "computed" -> {
                var fnProp = child.getProperty("fn");
                if (fnProp.isEmpty() || !fnProp.get().isString()) {
                    throw new TemplateParsingException("Computed source requires a string 'fn' property");
                }
                yield new VariableDefinition.Source.Computed((String) fnProp.get().value());
            }
            default -> throw new TemplateParsingException("Unknown source type: " + sourceId);
        };
    }

    private void parseLookup(KdlNode child, Map<String, String> lookupMap) throws TemplateParsingException {
        for (KdlNode entryNode : child.children()) {
            String key = entryNode.name();

            if (entryNode.arguments().isEmpty()) {
                throw new TemplateParsingException("Lookup entry for key '" + key + "' requires a mapped target string value");
            }

            var valueArg = entryNode.arguments().getFirst();
            if (!valueArg.isString()) {
                throw new TemplateParsingException("Lookup target value for key '" + key + "' must be a string");
            }

            lookupMap.put(key, (String) valueArg.value());
        }
    }

    private String parseSingleStringArgument(KdlNode child, String nodeName) throws TemplateParsingException {
        if (child.arguments().isEmpty()) {
            throw new TemplateParsingException("Node '" + nodeName + "' requires an argument");
        }

        var arg = child.arguments().getFirst();
        if (!arg.isString()) {
            throw new TemplateParsingException("Argument for '" + nodeName + "' must be a string");
        }

        return (String) arg.value();
    }
}