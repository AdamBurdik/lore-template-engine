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
import me.adamix.lte.definition.group.GroupDefinition;
import me.adamix.lte.definition.variable.VariableDefinition;
import me.adamix.lte.registry.GroupRegistry;
import me.adamix.lte.registry.VariableRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
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
    private final VariableRegistry variableRegistry;
    private final GroupRegistry groupRegistry;
    
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
        sender.sendMessage("Rebuilt $" + player.getName() + "'s templated items!");
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
        var definitionOpt = variableRegistry.get(name);
        if (definitionOpt.isEmpty()) {
            player.sendMessage("Unknown variable: " + name);
            return;
        }

        var source = definitionOpt.get().source();
        if (!(source instanceof VariableDefinition.Source.PDC(String key))) {
            player.sendMessage("Variable '" + name + "' is not PDC-backed and cannot be set");
            return;
        }

        ItemStack itemStack = player.getInventory().getItemInMainHand();
        itemStack.editMeta(meta -> {
            var container = meta.getPersistentDataContainer();
            container.set(new NamespacedKey(plugin, "variable_" + key), PersistentDataType.STRING, value);
        });

        templateService.rebuild(itemStack);
        player.sendMessage("Set variable '" + name + "' to '" + value + "'");
    }

    @Subcommand("set group")
    @CommandCompletion("@groups")
    public void setGroup(@NotNull Player player, @NotNull String name, @NotNull String... args) {
        var definitionOpt = groupRegistry.get(name);
        if (definitionOpt.isEmpty()) {
            player.sendMessage("Unknown group: " + name);
            return;
        }

        var source = definitionOpt.get().source();
        if (!(source instanceof GroupDefinition.Source.PDC pdcSource)) {
            player.sendMessage("Group '" + name + "' is not PDC-backed and cannot be set");
            return;
        }

        List<String> values = splitLines(String.join(" ", args));

        ItemStack itemStack = player.getInventory().getItemInMainHand();
        itemStack.editMeta(meta -> {
            var container = meta.getPersistentDataContainer();
            container.set(new NamespacedKey(plugin, "group_" + pdcSource.key()), PersistentDataType.STRING, GroupService.encodeList(values));
        });

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
