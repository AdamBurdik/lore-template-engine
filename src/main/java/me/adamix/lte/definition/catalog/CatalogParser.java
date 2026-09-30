package me.adamix.lte.definition.catalog;

import dev.kdl.KdlDocument;
import dev.kdl.KdlNode;
import dev.kdl.parse.KdlParseException;
import dev.kdl.parse.KdlParser;
import me.adamix.lte.api.exception.TemplateParsingException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CatalogParser {
    public @NotNull List<CatalogDefinition> parseFile(
            @NotNull Path path
    ) throws KdlParseException, IOException, TemplateParsingException {
        var parser = KdlParser.v2();
        var document = parser.parse(path);
        return parseDocument(document);
    }

    public @NotNull List<CatalogDefinition> parseDocument(@NotNull KdlDocument document)
            throws TemplateParsingException {
        List<CatalogDefinition> catalogs = new ArrayList<>();

        for (KdlNode node : document.nodes()) {
            if (node.name().equals("catalog")) {
                catalogs.add(parseCatalogNode(node));
            }
        }

        return List.copyOf(catalogs);
    }

    private @NotNull CatalogDefinition parseCatalogNode(
            @NotNull KdlNode catalogNode
    ) throws TemplateParsingException {
        String catalogName = extractSingleStringArgument(catalogNode, "catalog node");
        Map<String, CatalogDefinition.Entry> entries = new HashMap<>();
        Set<String> entryIds = new HashSet<>();

        for (KdlNode child : catalogNode.children()) {
            if (!child.name().equals("entry")) {
                throw new TemplateParsingException(
                        "Unexpected node '%s' inside catalog '%s'".formatted(child.name(), catalogName)
                );
            }

            var entry = parseEntryNode(child, catalogName);

            if (!entryIds.add(entry.id())) {
                throw new TemplateParsingException(
                        "Duplicate entry ID '%s' in catalog '%s'".formatted(entry.id(), catalogName)
                );
            }

            entries.put(entry.id(), entry);
        }

        return new CatalogDefinition(catalogName, Map.copyOf(entries));
    }

    private @NotNull CatalogDefinition.Entry parseEntryNode(
            @NotNull KdlNode entryNode,
            @NotNull String catalogName
    ) throws TemplateParsingException {
        String entryId = extractSingleStringArgument(entryNode, "entry node in catalog '" + catalogName + "'");
        String display = null;

        for (KdlNode child : entryNode.children()) {
            if (child.name().equals("display")) {
                if (display != null) {
                    throw new TemplateParsingException(
                            "Duplicate 'display' property for entry '%s' in catalog '%s'".formatted(entryId, catalogName)
                    );
                }
                display = extractSingleStringArgument(child, "'display' node in entry '" + entryId + "'");
            } else {
                throw new TemplateParsingException(
                        "Unknown node '%s' inside entry '%s'".formatted(child.name(), entryId)
                );
            }
        }

        if (display == null) {
            throw new TemplateParsingException(
                    "Entry '%s' in catalog '%s' is missing a 'display' definition".formatted(entryId, catalogName)
            );
        }

        return new CatalogDefinition.Entry(entryId, display);
    }

    private @NotNull String extractSingleStringArgument(
            @NotNull KdlNode node,
            @NotNull String context
    ) throws TemplateParsingException {
        var args = node.arguments();
        if (args.isEmpty()) {
            throw new TemplateParsingException("Expected a string argument for " + context + ", but none was provided");
        }

        var arg = args.getFirst();
        if (!arg.isString() || !(arg.value() instanceof String strVal)) {
            throw new TemplateParsingException("Argument for " + context + " must be a valid string");
        }

        return strVal;
    }
}