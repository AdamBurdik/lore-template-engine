package me.adamix.lte.integration.nexo;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import me.adamix.lte.api.LoreTemplateAPI;
import me.adamix.lte.definition.variable.VariableValue;
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
            Map<String, VariableValue> variables,
            Map<String, List<String>> groups
    ) {
        static Config parse(ConfigurationSection section) {
            Map<String, VariableValue> variables = new LinkedHashMap<>();
            ConfigurationSection vars = section.getConfigurationSection("variables");
            if (vars != null) {
                for (String k : vars.getKeys(false)) {
                    VariableValue value = parseVariableValue(vars, k);
                    if (value != null) {
                        variables.put(k, value);
                    }
                }
            }

            Map<String, List<String>> groups = new LinkedHashMap<>();
            ConfigurationSection grp = section.getConfigurationSection("groups");
            if (grp != null) {
                for (String k : grp.getKeys(false)) {
                    groups.put(k, grp.getStringList(k));
                }
            }

            return new Config(
                    section.getString("template"),
                    Collections.unmodifiableMap(variables),
                    Collections.unmodifiableMap(groups)
            );
        }

        private static @Nullable VariableValue parseVariableValue(ConfigurationSection section, String key) {
            if (section.isBoolean(key)) {
                return VariableValue.of(section.getBoolean(key));
            } else if (section.isInt(key)) {
                return VariableValue.of(section.getInt(key));
            } else if (section.isDouble(key)) {
                return VariableValue.of(section.getDouble(key));
            } else if (section.isString(key)) {
                String val = section.getString(key);
                return val != null ? VariableValue.of(val) : null;
            } else {
                Object val = section.get(key);
                return val != null ? VariableValue.of(String.valueOf(val)) : null;
            }
        }

        boolean isApplicable(LoreTemplateAPI api) {
            return template != null && api.hasTemplate(template);
        }

        void applyTo(LoreTemplateAPI api, ItemStack item) {
            if (template == null) return;
            var editor = api.edit(item).template(template);
            variables.forEach(editor::variable);
            groups.forEach(editor::group);
            editor.apply();
        }
    }
}