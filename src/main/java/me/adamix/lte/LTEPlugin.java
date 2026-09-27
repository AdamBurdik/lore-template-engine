package me.adamix.lte;

import co.aikar.commands.PaperCommandManager;
import dev.kdl.parse.KdlParseException;
import dev.kdl.parse.Reporter;
import me.adamix.lte.command.LTECommand;
import me.adamix.lte.definition.group.GroupDefinition;
import me.adamix.lte.definition.group.GroupParser;
import me.adamix.lte.definition.template.LoreTemplateDefinition;
import me.adamix.lte.definition.template.TemplateParser;
import me.adamix.lte.definition.variable.VariableDefinition;
import me.adamix.lte.definition.variable.VariableParser;
import me.adamix.lte.listener.ItemListener;
import me.adamix.lte.listener.PlayerListener;
import me.adamix.lte.registry.GroupRegistry;
import me.adamix.lte.registry.TemplateRegistry;
import me.adamix.lte.registry.VariableRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;


public class LTEPlugin extends JavaPlugin {
    public static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final VariableRegistry variableRegistry = new VariableRegistry();
    private final TemplateRegistry templateRegistry = new TemplateRegistry();
    private final GroupRegistry groupRegistry = new GroupRegistry();
    
    private PaperCommandManager commandManager;

    public void reload() {
        variableRegistry.clear();
        templateRegistry.clear();

        var templateParser = new TemplateParser();
        var variableParser = new VariableParser();
        var groupParser = new GroupParser();
        
        try {
            var result = templateParser.parseFile(getDataPath().resolve("templates.kdl"));
            for (LoreTemplateDefinition loreTemplateDefinition : result) {
                templateRegistry.register(loreTemplateDefinition.id(), loreTemplateDefinition);
            }

            var variableResult = variableParser.parseFile(getDataPath().resolve("variables.kdl"));
            for (VariableDefinition definition : variableResult) {
                variableRegistry.register(definition.name(), definition);
            }
            
            var groupResult = groupParser.parseFile(getDataPath().resolve("groups.kdl"));
            for (GroupDefinition definition : groupResult) {
                groupRegistry.register(definition.name(), definition);
            }

        } catch (KdlParseException e) {
            var report = Reporter.getReport(e, true);
            System.out.println(report);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onEnable() {
        saveResource("templates.kdl", false);
        saveResource("variables.kdl", false);
        saveResource("groups.kdl", false);
        
        commandManager = new PaperCommandManager(this);

        commandManager.getCommandCompletions().registerCompletion("templates", _ -> templateRegistry.keySet());
        commandManager.getCommandCompletions().registerCompletion("variables", _ -> variableRegistry.keySet());
        commandManager.getCommandCompletions().registerCompletion("groups", _ -> groupRegistry.keySet());
        commandManager.getCommandCompletions().registerCompletion("variableLookupValues", c -> {
            var args = c.getArgs();
            if (args.isEmpty()) return Set.of();

            var definitionOpt = variableRegistry.get(args.getFirst());
            if (definitionOpt.isEmpty()) return Set.of();

            var lookup = definitionOpt.get().lookup();
            if (lookup == null) return Set.of();

            return lookup.map().keySet();
        });
        
        VariableService variableService = new VariableService(this, variableRegistry);
        GroupService groupService = new GroupService(this, groupRegistry);
        TemplateService templateService = new TemplateService(this, templateRegistry, variableService, groupService);
        LifecycleService lifecycleService = new LifecycleService(templateService);

        commandManager.registerCommand(new LTECommand(this, templateService, lifecycleService, variableRegistry, groupRegistry));

        Bukkit.getPluginManager().registerEvents(new ItemListener(this, templateService), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(lifecycleService), this);
        
        reload();
    }

    @Override
    public void onDisable() {

    }
}
