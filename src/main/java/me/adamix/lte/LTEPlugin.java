package me.adamix.lte;

import co.aikar.commands.PaperCommandManager;
import me.adamix.lte.api.LoreTemplateAPI;
import me.adamix.lte.command.LTECommand;
import me.adamix.lte.definition.group.GroupDefinition;
import me.adamix.lte.definition.group.GroupParser;
import me.adamix.lte.definition.template.LoreTemplateDefinition;
import me.adamix.lte.definition.template.TemplateParser;
import me.adamix.lte.definition.transformer.TransformerDefinition;
import me.adamix.lte.definition.transformer.TransformerParser;
import me.adamix.lte.definition.variable.VariableDefinition;
import me.adamix.lte.definition.variable.VariableParser;
import me.adamix.lte.definition.variable.VariableValue;
import me.adamix.lte.editor.ItemEditorImpl;
import me.adamix.lte.integration.nexo.NexoIntegration;
import me.adamix.lte.listener.ItemListener;
import me.adamix.lte.listener.PlayerListener;
import me.adamix.lte.registry.Registry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;


public class LTEPlugin extends JavaPlugin implements LoreTemplateAPI {
    public static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Registry<VariableDefinition> variableRegistry = new Registry<>();
    private final Registry<LoreTemplateDefinition> templateRegistry = new Registry<>();
    private final Registry<GroupDefinition> groupRegistry = new Registry<>();
    private final Registry<TransformerDefinition> transformerRegistry = new Registry<>();


    private TemplateService templateService;
    private VariableService variableService;
    private TransformerService transformerService;
    private GroupService groupService;
    private LifecycleService lifecycleService;
    
    private PaperCommandManager commandManager;

    public void reload() {
        variableRegistry.clear();
        templateRegistry.clear();
        groupRegistry.clear();
        transformerRegistry.clear();

        var templateParser = new TemplateParser();
        var variableParser = new VariableParser();
        var groupParser = new GroupParser();
        var transformerParser = new TransformerParser();

        try {
            var transformerResult = transformerParser.parseFile(getDataPath().resolve("transformers.kdl"));
            for (TransformerDefinition definition : transformerResult) {
                transformerRegistry.register(definition.name(), definition);
            }

            var variableResult = variableParser.parseFile(getDataPath().resolve("variables.kdl"));
            for (VariableDefinition definition : variableResult) {
                variableRegistry.register(definition.name(), definition);
            }

            var groupResult = groupParser.parseFile(getDataPath().resolve("groups.kdl"));
            for (GroupDefinition definition : groupResult) {
                groupRegistry.register(definition.name(), definition);
            }

            var templateResult = templateParser.parseFile(getDataPath().resolve("templates.kdl"));
            for (LoreTemplateDefinition loreTemplateDefinition : templateResult) {
                templateRegistry.register(loreTemplateDefinition.id(), loreTemplateDefinition);
            }

            DefinitionValidator.validate(
                    transformerRegistry,
                    variableRegistry,
                    groupRegistry,
                    templateRegistry
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onEnable() {
        saveResource("templates.kdl", false);
        saveResource("variables.kdl", false);
        saveResource("groups.kdl", false);
        saveResource("transformers.kdl", false);

        commandManager = new PaperCommandManager(this);

        commandManager.getCommandCompletions().registerCompletion("templates", _ -> templateRegistry.keySet());
        commandManager.getCommandCompletions().registerCompletion("variables", _ -> variableRegistry.keySet());
        commandManager.getCommandCompletions().registerCompletion("groups", _ -> groupRegistry.keySet());
        commandManager.getCommandCompletions().registerCompletion("variableValues", c -> {
            var args = c.getArgs();
            if (args.isEmpty()) return Set.of();

            var definitionOpt = variableRegistry.get(args.getFirst());
            if (definitionOpt.isEmpty()) return Set.of();

            String transformer = definitionOpt.get().transformer();
            if (transformer == null) return Set.of();

            return transformerService.get(transformer)
                    .map(definition -> definition.entries().keySet())
                    .orElse(Set.of());
        });
        commandManager.getCommandCompletions().registerCompletion("groupValues", c -> {
            var args = c.getArgs();
            if (args.isEmpty()) return Set.of();

            String groupName = args.getFirst();
            var groupOpt = groupRegistry.get(groupName);
            if (groupOpt.isEmpty()) return Set.of();

            var transformer = groupOpt.get().transformer();
            if (transformer == null) return Set.of();

            return transformerService.get(transformer.name())
                    .map(definition -> definition.entries().keySet())
                    .orElse(Set.of());
        });

        transformerService = new TransformerService(transformerRegistry);
        variableService = new VariableService(this, variableRegistry, new HashMap<>(), transformerService);
        groupService = new GroupService(this, groupRegistry, transformerService);
        templateService = new TemplateService(this, templateRegistry, variableService, groupService);
        lifecycleService = new LifecycleService(templateService);

        commandManager.registerCommand(new LTECommand(this, templateService, lifecycleService, variableService, groupService));

        Bukkit.getPluginManager().registerEvents(new ItemListener(this, templateService), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(lifecycleService), this);
        reload();

        attemptNexoIntegration();
    }
    
    private void attemptNexoIntegration() {
        if (getServer().getPluginManager().isPluginEnabled("Nexo")) {
            new NexoIntegration(this, this).enable();
        }
    }
    
    @Override
    public boolean hasTemplate(@NotNull String templateId) {
        return templateRegistry.get(templateId).isPresent();
    }

    @Override
    public @NotNull Set<String> templateIds() {
        return templateRegistry.keySet();
    }

    @Override
    public @NotNull Optional<String> getTemplateId(@NotNull ItemStack itemStack) {
        return templateService.getTemplateId(itemStack);
    }

    @Override
    public @NotNull List<Component> render(@NotNull ItemStack itemStack, @NotNull String templateId) {
        return templateService.render(itemStack, templateId);
    }

    @Override
    public void apply(@NotNull ItemStack itemStack, @NotNull String templateId) {
        try {
            templateService.apply(templateId, itemStack);
        } catch (NoSuchElementException _) {}
    }

    @Override
    public void rebuild(@NotNull ItemStack itemStack) {
        try {
            templateService.rebuild(itemStack);
        } catch (NoSuchElementException _) {}
    }

    @Override
    public void clear(@NotNull ItemStack itemStack) {
        templateService.clear(itemStack);
    }

    @Override
    public @NotNull Optional<VariableValue> getVariable(@NotNull ItemStack itemStack, @NotNull String variable) {
        try {
            return variableService.getRawValue(variable, itemStack);
        } catch (NoSuchElementException _) {
            return Optional.empty();
        }
    }

    @Override
    public @NotNull List<String> getGroup(@NotNull ItemStack item, @NotNull String group) {
        try {
            return groupService.get(group, item);
        } catch (NoSuchElementException _) {
            return Collections.emptyList();
        }
    }

    @Override
    public @NotNull ItemEditor edit(@NotNull ItemStack itemStack) {
        return new ItemEditorImpl(itemStack, templateService, variableService, groupService);
    }

    @Override
    public void rebuildPlayer(@NotNull Player player) {
        lifecycleService.rebuildPlayer(player);
    }
}
