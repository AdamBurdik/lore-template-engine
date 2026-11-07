package me.adamix.lte.registry;

import me.adamix.lte.definition.variable.VariableDefinition;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class VariableRegistry {
    private final Map<String, VariableDefinition> map = new ConcurrentHashMap<>();

    public Optional<VariableDefinition> get(String id) {
        return Optional.ofNullable(map.get(id));
    }

    public void register(String id, VariableDefinition definition) {
        map.put(id, definition);
    }
    
    public void clear() {
        map.clear();
    }

    public Set<String> keySet() {
        return map.keySet();
    }
}
