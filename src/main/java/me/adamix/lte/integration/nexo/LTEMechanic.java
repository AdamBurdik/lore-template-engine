package me.adamix.lte.integration.nexo;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import me.adamix.lte.api.LoreTemplateAPI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LTEMechanic extends Mechanic {
    private final Config config;

    public LTEMechanic(
            @NotNull MechanicFactory factory,
            @NotNull ConfigurationSection section
    ) {
        super(factory, section);
        this.config = Config.parse(section);
    }

    public void applyTo(@NotNull LoreTemplateAPI api, @NotNull ItemStack item) {
        if (config.isApplicable(api)) config.applyTo(api, item);
    }

    private record Config(
            @Nullable String template,
            Map<String, String> variables,
            Map<String, List<String>> groups
    ) {
        static Config parse(ConfigurationSection section) {
            Map<String, String> variables = new LinkedHashMap<>();
            ConfigurationSection vars = section.getConfigurationSection("variables");
            if (vars != null) {
                for (String k : vars.getKeys(false)) variables.put(k, vars.getString(k));
            }

            Map<String, List<String>> groups = new LinkedHashMap<>();
            ConfigurationSection grp = section.getConfigurationSection("groups");
            if (grp != null) {
                for (String k : grp.getKeys(false)) groups.put(k, grp.getStringList(k));
            }

            return new Config(
                    section.getString("template"),
                    Collections.unmodifiableMap(variables),
                    Collections.unmodifiableMap(groups)
            );
        }

        boolean isApplicable(LoreTemplateAPI api) {
            return template != null && api.hasTemplate(template);
        }

        void applyTo(LoreTemplateAPI api, ItemStack item) {
            var editor = api.edit(item).template(template);
            variables.forEach(editor::variable);
            groups.forEach(editor::group);
            editor.apply();
        }
    }
}