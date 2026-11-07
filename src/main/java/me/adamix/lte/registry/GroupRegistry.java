package me.adamix.lte.registry;

import me.adamix.lte.definition.group.GroupDefinition;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GroupRegistry {
    private final Map<String, GroupDefinition> map = new ConcurrentHashMap<>();

    public Optional<GroupDefinition> get(String id) {
        return Optional.ofNullable(map.get(id));
    }

    public void register(String id, GroupDefinition template) {
        map.put(id, template);
    }

    public void clear() {
        map.clear();
    }

    public Set<String> keySet() {
        return map.keySet();
    }
}
