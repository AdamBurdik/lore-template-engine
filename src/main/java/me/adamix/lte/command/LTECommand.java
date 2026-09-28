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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
        var itemStack = player.getInventory().getItemInMainHand();
        
        templateService.apply(templateId, itemStack);
    }

    @Subcommand("set variable")
    @CommandCompletion("@variables @variableLookupValues")
    public void setVariable(@NotNull Player player, @NotNull String name, @NotNull String value) {
        ItemStack itemStack = player.getInventory().getItemInMainHand();

        try {
            variableService.set(itemStack, name, value);
        } catch (DefinitionNotFoundException | NotPdcBackedException e) {
            player.sendMessage(e.getMessage());
            return;
        }

        templateService.rebuild(itemStack);
        player.sendMessage("Set variable '" + name + "' to '" + value + "'");
    }

    @Subcommand("set group")
    @CommandCompletion("@groups")
    public void setGroup(@NotNull Player player, @NotNull String name, @NotNull String... args) {
        List<String> values = splitLines(String.join(" ", args));

        ItemStack itemStack = player.getInventory().getItemInMainHand();

        try {
            groupService.set(itemStack, name, values);
        } catch (DefinitionNotFoundException | NotPdcBackedException e) {
            player.sendMessage(e.getMessage());
            return;
        }

        templateService.rebuild(itemStack);
        player.sendMessage("Set group '" + name + "' to " + values.size() + " value(s)");
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
