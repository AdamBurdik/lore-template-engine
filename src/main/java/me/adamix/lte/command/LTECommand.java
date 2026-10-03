package me.adamix.lte.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import lombok.RequiredArgsConstructor;
import me.adamix.lte.GroupService;
import me.adamix.lte.LTEPlugin;
import me.adamix.lte.LifecycleService;
import me.adamix.lte.TemplateService;
import me.adamix.lte.VariableService;
import me.adamix.lte.api.exception.DefinitionNotFoundException;
import me.adamix.lte.api.exception.NotPdcBackedException;
import me.adamix.lte.definition.variable.VariableValue;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@CommandAlias("lore|lte")
public class LTECommand extends BaseCommand {
    private final LTEPlugin plugin;
    private final TemplateService templateService;
    private final LifecycleService lifecycleService;
    private final VariableService variableService;
    private final GroupService groupService;

    @Subcommand("reload")
    @CommandPermission("lte.command.reload")
    public void reload(@NotNull CommandSender sender) {
        plugin.reload();
        lifecycleService.rebuildAllPlayers();
        sender.sendMessage("Reloaded and rebuilt all templated items!");
    }

    @Subcommand("rebuild")
    @CommandPermission("lte.command.rebuild")
    public void rebuild(@NotNull CommandSender sender, @Optional @Nullable Player target) {
        Player player = target != null ? target : (sender instanceof Player s ? s : null);
        if (player == null) {
            sender.sendMessage("This command must be run by a player, or specify a target player.");
            return;
        }

        lifecycleService.rebuildPlayer(player);
        sender.sendMessage("Rebuilt " + player.getName() + "'s templated items!");
    }

    @Subcommand("set template")
    @CommandCompletion("@templates")
    public void apply(@NotNull Player player, @NotNull String templateId) {
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        templateService.apply(templateId, itemStack);
    }

    @Subcommand("set variable")
    @CommandCompletion("@variables @variableLookupValues")
    public void setVariable(@NotNull Player player, @NotNull String name, @NotNull String value) {
        ItemStack itemStack = player.getInventory().getItemInMainHand();

        try {
            variableService.setValue(itemStack, name, VariableValue.of(value));
        } catch (DefinitionNotFoundException | NotPdcBackedException e) {
            player.sendMessage(e.getMessage());
            return;
        }

        templateService.rebuild(itemStack);
        player.sendMessage("Set variable '" + name + "' to '" + value + "'");
    }

    @Subcommand("group set")
    @CommandCompletion("@groups @groupValues")
    public void setGroup(@NotNull Player player, @NotNull String name, @NotNull String... args) {
        List<String> values = splitLines(String.join(" ", args));
        ItemStack itemStack = player.getInventory().getItemInMainHand();

        if (executeGroupAction(player, () -> groupService.set(itemStack, name, values))) {
            templateService.rebuild(itemStack);
            player.sendMessage("Set group '" + name + "' to " + values.size() + " value(s).");
        }
    }

    @Subcommand("group add")
    @CommandCompletion("@groups @groupValues")
    public void addGroupValue(@NotNull Player player, @NotNull String name, @NotNull String... args) {
        List<String> rawInput = splitLines(String.join(" ", args));

        List<String> toAdd = rawInput.stream()
                .map(arg -> arg.equalsIgnoreCase("null") ? null : arg)
                .toList();
        
        ItemStack itemStack = player.getInventory().getItemInMainHand();

        if (executeGroupAction(player, () -> {
            List<String> current = new ArrayList<>(groupService.get(name, itemStack));
            current.addAll(toAdd);
            groupService.set(itemStack, name, current);
        })) {
            templateService.rebuild(itemStack);
            player.sendMessage("Added " + toAdd.size() + " element(s) to group '" + name + "'.");
        }
    }

    @Subcommand("group setslot")
    @CommandCompletion("@groups 1|2|3|4 @groupValues")
    public void setGroupSlot(@NotNull Player player, @NotNull String name, int index, @NotNull String value) {
        ItemStack itemStack = player.getInventory().getItemInMainHand();

        if (executeGroupAction(player, () -> {
            List<String> current = new ArrayList<>(groupService.get(name, itemStack));

            // 1-based index conversion
            int targetIndex = index - 1;
            if (targetIndex < 0 || targetIndex >= current.size()) {
                throw new IllegalArgumentException("Index " + index + " is out of bounds for group size " + current.size());
            }
            
            String toAdd;
            if (value.equalsIgnoreCase("null")) {
                toAdd = null;
            } else {
                toAdd = value;
            }
            
            current.set(targetIndex, toAdd);
            groupService.set(itemStack, name, current);
        })) {
            templateService.rebuild(itemStack);
            player.sendMessage("Set slot " + index + " in group '" + name + "' to '" + value + "'.");
        }
    }

    @Subcommand("group removeat")
    @CommandCompletion("@groups 1|2|3|4")
    public void removeGroupSlot(@NotNull Player player, @NotNull String name, int index) {
        ItemStack itemStack = player.getInventory().getItemInMainHand();

        if (executeGroupAction(player, () -> {
            List<String> current = new ArrayList<>(groupService.get(name, itemStack));

            // 1-based index conversion
            int targetIndex = index - 1;
            if (targetIndex < 0 || targetIndex >= current.size()) {
                throw new IllegalArgumentException("Index " + index + " is out of bounds for group size " + current.size());
            }

            current.remove(targetIndex);
            groupService.set(itemStack, name, current);
        })) {
            templateService.rebuild(itemStack);
            player.sendMessage("Removed slot " + index + " from group '" + name + "'.");
        }
    }

    @Subcommand("group remove")
    @CommandCompletion("@groups @groupValues")
    public void removeGroupValue(@NotNull Player player, @NotNull String name, @NotNull String value) {
        ItemStack itemStack = player.getInventory().getItemInMainHand();

        if (executeGroupAction(player, () -> {
            List<String> current = new ArrayList<>(groupService.get(name, itemStack));
            boolean removed = current.remove(value);
            if (!removed) {
                throw new IllegalArgumentException("Value '" + value + "' not found in group '" + name + "'.");
            }
            groupService.set(itemStack, name, current);
        })) {
            templateService.rebuild(itemStack);
            player.sendMessage("Removed first occurrence of '" + value + "' from group '" + name + "'.");
        }
    }

    @Subcommand("group clear")
    @CommandCompletion("@groups")
    public void clearGroup(@NotNull Player player, @NotNull String name) {
        ItemStack itemStack = player.getInventory().getItemInMainHand();

        if (executeGroupAction(player, () -> groupService.set(itemStack, name, List.of()))) {
            templateService.rebuild(itemStack);
            player.sendMessage("Cleared all values from group '" + name + "'.");
        }
    }

    @FunctionalInterface
    private interface GroupAction {
        void execute() throws DefinitionNotFoundException, NotPdcBackedException, IllegalArgumentException;
    }

    private boolean executeGroupAction(@NotNull Player player, @NotNull GroupAction action) {
        try {
            action.execute();
            return true;
        } catch (DefinitionNotFoundException | NotPdcBackedException | IllegalArgumentException e) {
            player.sendMessage(e.getMessage());
            return false;
        }
    }

    private static @NotNull List<String> splitLines(@NotNull String raw) {
        List<String> lines = new ArrayList<>();
        for (String segment : raw.split("\\\\n", -1)) {
            for (String line : segment.split("\\R")) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }
}