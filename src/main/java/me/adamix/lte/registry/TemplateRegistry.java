package me.adamix.lte.registry;

import me.adamix.lte.definition.template.LoreTemplateDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TemplateRegistry {
    private final Map<String, LoreTemplateDefinition> map = new ConcurrentHashMap<>();
    
    public @NotNull Optional<LoreTemplateDefinition> get(@NotNull String id) {
        return Optional.ofNullable(map.get(id));
    }
    
    public void register(@NotNull String id, @NotNull LoreTemplateDefinition template) {
        map.put(id, template);
    }
    
    public void clear() {
        map.clear();
    }
    
    public @NotNull Set<String> keySet() {
        return map.keySet();
    }
}
