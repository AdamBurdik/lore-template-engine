package me.adamix.lte.registry;

import me.adamix.lte.definition.group.GroupDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GroupRegistry {
    private final Map<String, GroupDefinition> map = new ConcurrentHashMap<>();

    public @NotNull Optional<GroupDefinition> get(@NotNull String id) {
        return Optional.ofNullable(map.get(id));
    }

    public void register(@NotNull String id, @NotNull GroupDefinition template) {
        map.put(id, template);
    }

    public void clear() {
        map.clear();
    }

    public @NotNull Set<String> keySet() {
        return map.keySet();
    }
}
