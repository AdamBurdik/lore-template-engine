package me.adamix.lte.registry;

import me.adamix.lte.definition.variable.VariableDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class VariableRegistry {
    private final Map<String, VariableDefinition> map = new ConcurrentHashMap<>();

    public @NotNull Optional<VariableDefinition> get(@NotNull String id) {
        return Optional.ofNullable(map.get(id));
    }

    public void register(@NotNull String id, @NotNull VariableDefinition definition) {
        map.put(id, definition);
    }
    
    public void clear() {
        map.clear();
    }

    public @NotNull Set<String> keySet() {
        return map.keySet();
    }
}
