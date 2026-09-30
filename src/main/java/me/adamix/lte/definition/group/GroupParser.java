package me.adamix.lte.definition.group;

import dev.kdl.KdlDocument;
import dev.kdl.KdlNode;
import dev.kdl.parse.KdlParseException;
import dev.kdl.parse.KdlParser;
import me.adamix.lte.api.exception.TemplateParsingException;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GroupParser {
    public @NotNull List<GroupDefinition> parseFile(
            @NotNull Path path
    ) throws KdlParseException, IOException, TemplateParsingException {
        var parser = KdlParser.v2();
        var document = parser.parse(path);
        return parseDocument(document);
    }

    public @NotNull List<GroupDefinition> parseDocument(@NotNull KdlDocument document)
            throws TemplateParsingException {
        List<GroupDefinition> definitions = new ArrayList<>();

        for (KdlNode node : document.nodes()) {
            if (node.name().equals("group")) {
                definitions.add(parseGroupNode(node));
            }
        }

        return List.copyOf(definitions);
    }

    private @NotNull GroupDefinition parseGroupNode(
            @NotNull KdlNode groupNode
    ) throws TemplateParsingException {
        String groupName = extractSingleStringArgument(groupNode, "group node");

        GroupDefinition.Source.Builtin builtinSource = null;
        String pdcKey = null;
        String pdcType = null;
        String valueName = null;
        String catalog = null;
        String defaultValue = null;

        for (KdlNode child : groupNode.children()) {
            switch (child.name()) {
                case "builtin" -> {
                    if (builtinSource != null) {
                        throw new TemplateParsingException("Group '" + groupName + "' has duplicate 'builtin' definitions");
                    }
                    builtinSource = parseBuiltinNode(child, groupName);
                }
                case "source" -> {
                    String sourceId = extractSingleStringArgument(child, "'source' node in group '" + groupName + "'");
                    if (!sourceId.equals("pdc")) {
                        throw new TemplateParsingException("Unknown source type: '" + sourceId + "' in group '" + groupName + "'");
                    }

                    pdcKey = extractPropertyString(child, "key", "PDC source in group '" + groupName + "'");
                    pdcType = extractOptionalPropertyString(child, "type");
                }
                case "value-name" -> {
                    if (valueName != null) {
                        throw new TemplateParsingException("Duplicate 'value-name' in group '" + groupName + "'");
                    }
                    valueName = extractSingleStringArgument(child, "'value-name' node in group '" + groupName + "'");
                }
                case "catalog" -> {
                    if (catalog != null) {
                        throw new TemplateParsingException("Duplicate 'catalog' in group '" + groupName + "'");
                    }
                    catalog = extractSingleStringArgument(child, "'catalog' node in group '" + groupName + "'");
                }
                case "default" -> {
                    if (defaultValue != null) {
                        throw new TemplateParsingException("Duplicate 'default' in group '" + groupName + "'");
                    }
                    defaultValue = extractSingleStringArgument(child, "'default' node in group '" + groupName + "'");
                }
                default -> throw new TemplateParsingException(
                        "Unknown node '" + child.name() + "' inside group '" + groupName + "'"
                );
            }
        }

        // Validate Builtin vs PDC source configuration
        if (builtinSource != null) {
            if (pdcKey != null || valueName != null || catalog != null || defaultValue != null) {
                throw new TemplateParsingException(
                        "Group '" + groupName + "' defines 'builtin' but also includes PDC/catalog settings"
                );
            }
            return new GroupDefinition(groupName, builtinSource);
        }

        if (pdcKey != null) {
            if (valueName == null) {
                throw new TemplateParsingException("Group '" + groupName + "' using PDC source requires a 'value-name'");
            }
            return new GroupDefinition(
                    groupName,
                    new GroupDefinition.Source.PDC(pdcKey, valueName, catalog, defaultValue)
            );
        }

        throw new TemplateParsingException("Group '" + groupName + "' must specify a valid source (builtin or PDC)");
    }

    private @NotNull GroupDefinition.Source.Builtin parseBuiltinNode(
            @NotNull KdlNode builtinNode,
            @NotNull String groupName
    ) throws TemplateParsingException {
        String builtinName = extractSingleStringArgument(builtinNode, "'builtin' node in group '" + groupName + "'");
        String slot = null;

        var slotProp = builtinNode.getProperty("slot");
        if (slotProp.isPresent()) {
            if (!slotProp.get().isString()) {
                throw new TemplateParsingException("Builtin 'slot' property must be a string in group '" + groupName + "'");
            }
            String rawSlot = (String) slotProp.get().value();
            slot = normalizeSlot(rawSlot);
            if (slot == null) {
                throw new TemplateParsingException("Unknown builtin slot '" + rawSlot + "' for group '" + groupName + "'");
            }
        }

        return new GroupDefinition.Source.Builtin(builtinName, slot);
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

    private @NotNull String extractPropertyString(
            @NotNull KdlNode node,
            @NotNull String propertyKey,
            @NotNull String context
    ) throws TemplateParsingException {
        var prop = node.getProperty(propertyKey);
        if (prop.isEmpty() || !prop.get().isString() || !(prop.get().value() instanceof String strVal)) {
            throw new TemplateParsingException("Property '" + propertyKey + "' is required and must be a string in " + context);
        }
        return strVal;
    }

    private @Nullable String extractOptionalPropertyString(@NotNull KdlNode node, @NotNull String propertyKey) {
        var prop = node.getProperty(propertyKey);
        if (prop.isPresent() && prop.get().isString() && prop.get().value() instanceof String strVal) {
            return strVal;
        }
        return null;
    }

    private static @Nullable String normalizeSlot(@NotNull String raw) {
        String key = raw.trim().toLowerCase().replace("-", "").replace("_", "");
        EquipmentSlotGroup group = EquipmentSlotGroup.getByName(key);
        return group != null ? group.toString() : null;
    }
}