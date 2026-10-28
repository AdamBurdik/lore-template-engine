package me.adamix.lte;

import co.aikar.commands.PaperCommandManager;
import me.adamix.kidl.config.DiagnosticsConfig;
import me.adamix.kidl.diagnostic.Diagnostic;
import me.adamix.kidl.render.AnsiRenderer;
import me.adamix.lte.api.LoreTemplateAPI;
import me.adamix.lte.api.exception.ConfigurationException;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Path transformersFile = getDataPath().resolve("transformers.kdl");
        Path variablesFile = getDataPath().resolve("variables.kdl");
        Path groupsFile = getDataPath().resolve("groups.kdl");
        Path templatesFile = getDataPath().resolve("templates.kdl");

        Registry<TransformerDefinition> transformers = new Registry<>();
        Registry<VariableDefinition> variables = new Registry<>();
        Registry<GroupDefinition> groups = new Registry<>();
        Registry<LoreTemplateDefinition> templates = new Registry<>();

        try {
            for (TransformerDefinition definition : new TransformerParser().parseFile(transformersFile)) {
                transformers.register(definition.name(), definition);
            }

            for (VariableDefinition definition : new VariableParser().parseFile(variablesFile)) {
                variables.register(definition.name(), definition);
            }

            for (GroupDefinition definition : new GroupParser().parseFile(groupsFile)) {
                groups.register(definition.name(), definition);
            }

            for (LoreTemplateDefinition definition : new TemplateParser().parseFile(templatesFile)) {
                templates.register(definition.id(), definition);
            }
        } catch (IOException e) {
            throw new ConfigurationException("Could not read LTE configuration files", e);
        }

        List<Diagnostic> diagnostics = DefinitionValidator.validate(
                transformers,
                variables,
                groups,
                templates
        );

        if (!diagnostics.isEmpty()) {
            renderDiagnostics(diagnostics, List.of(transformersFile, variablesFile, groupsFile, templatesFile));
            throw new ConfigurationException("Definition validation failed");
        }

        transformerRegistry.replace(transformers);
        variableRegistry.replace(variables);
        groupRegistry.replace(groups);
        templateRegistry.replace(templates);
    }

    private static void renderDiagnostics(
            @NotNull List<Diagnostic> diagnostics,
            @NotNull List<Path> files
    ) {
        var sources = new HashMap<String, List<String>>();
        for (Path file : files) {
            try {
                sources.put(file.toString(), Files.readString(file).lines().toList());
            } catch (IOException ignored) {
            }
        }

        new AnsiRenderer(DiagnosticsConfig.builder().build()).sout(diagnostics, sources);
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

        try {
            reload();
        } catch (ConfigurationException e) {
            getLogger().severe("LTE could not load its configuration; disabling. Fix the diagnostics above and restart the server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

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
