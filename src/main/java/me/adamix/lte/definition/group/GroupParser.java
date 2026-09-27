package me.adamix.lte.definition.group;

import dev.kdl.KdlDocument;
import dev.kdl.KdlNode;
import dev.kdl.parse.KdlParseException;
import dev.kdl.parse.KdlParser;
import me.adamix.lte.exception.TemplateParsingException;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GroupParser {

    public @NotNull List<GroupDefinition> parseFile(@NotNull Path path) throws KdlParseException, IOException, TemplateParsingException {
        var parser = KdlParser.v2();
        var document = parser.parse(path);

        return kdlToGroups(document);
    }

    public @NotNull List<GroupDefinition> kdlToGroups(@NotNull KdlDocument document) throws TemplateParsingException {
        List<GroupDefinition> definitions = new ArrayList<>();

        for (KdlNode node : document.nodes()) {
            if (!node.name().equals("group")) continue;

            if (node.arguments().isEmpty()) {
                throw new TemplateParsingException("Group requires at least one argument for its name");
            }

            var nameArg = node.arguments().getFirst();
            if (!nameArg.isString()) {
                throw new TemplateParsingException("Group name must be a string");
            }

            String groupName = (String) nameArg.value();

            GroupDefinition.Source.Builtin builtinSource = null;
            String pdcKey = null;
            String valueName = null;

            for (KdlNode child : node.children()) {
                switch (child.name()) {
                    case "builtin" -> {
                        if (builtinSource != null) {
                            throw new TemplateParsingException("Group '" + groupName + "' has duplicate 'builtin' definitions");
                        }
                        if (child.arguments().isEmpty() || !child.arguments().getFirst().isString()) {
                            throw new TemplateParsingException("Builtin source requires a string name argument");
                        }

                        String builtinName = (String) child.arguments().getFirst().value();
                        String slot = null;
                        var slotProp = child.getProperty("slot");
                        if (slotProp.isPresent()) {
                            if (!slotProp.get().isString()) {
                                throw new TemplateParsingException("Builtin 'slot' property must be a string");
                            }
                            slot = normalizeSlot((String) slotProp.get().value());
                            if (slot == null) {
                                throw new TemplateParsingException(
                                        "Unknown builtin slot '" + slotProp.get().value() + "' for group '" + groupName + "'"
                                );
                            }
                        }

                        builtinSource = new GroupDefinition.Source.Builtin(builtinName, slot);
                    }
                    case "source" -> {
                        if (child.arguments().isEmpty() || !child.arguments().getFirst().isString()) {
                            throw new TemplateParsingException("Source block requires a string argument");
                        }

                        String sourceId = (String) child.arguments().getFirst().value();
                        if (sourceId.equals("pdc")) {
                            var keyProp = child.getProperty("key");
                            if (keyProp.isEmpty() || !keyProp.get().isString()) {
                                throw new TemplateParsingException("PDC source requires a string 'key' property");
                            }
                            pdcKey = (String) keyProp.get().value();
                        } else {
                            throw new TemplateParsingException("Unknown source type: " + sourceId);
                        }
                    }
                    case "value-name" -> {
                        if (child.arguments().isEmpty() || !child.arguments().getFirst().isString()) {
                            throw new TemplateParsingException("'value-name' requires a string argument");
                        }
                        valueName = (String) child.arguments().getFirst().value();
                    }
                    default -> {
                        throw new TemplateParsingException("Unknown value: " + child.name());
                    }
                }
            }

            if (builtinSource != null) {
                if (pdcKey != null || valueName != null) {
                    throw new TemplateParsingException(
                            "Group '" + groupName + "' defines 'builtin' but also includes PDC settings (key or value-name)"
                    );
                }
                definitions.add(new GroupDefinition(groupName, builtinSource));
                continue;
            }

            if (pdcKey != null) {
                if (valueName == null) {
                    throw new TemplateParsingException("Group '" + groupName + "' using PDC source requires a 'value-name'");
                }
                definitions.add(new GroupDefinition(groupName, new GroupDefinition.Source.PDC(pdcKey, valueName)));
                continue;
            }

            throw new TemplateParsingException("Group '" + groupName + "' must specify a valid source");
        }

        return definitions;
    }

    private static @Nullable String normalizeSlot(@NotNull String raw) {
        String key = raw.trim().toLowerCase().replace("-", "").replace("_", "");
        EquipmentSlotGroup group = EquipmentSlotGroup.getByName(key);
        if (group == null) {
            return null;
        }
        return group.toString();
    }
}