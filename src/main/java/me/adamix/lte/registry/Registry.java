package me.adamix.lte.registry;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Registry<T> {
    private final Map<String, T> map = new ConcurrentHashMap<>();

    public @NotNull Optional<T> get(@NotNull String id) {
        return Optional.ofNullable(map.get(id));
    }

    public void register(@NotNull String id, @NotNull T template) {
        map.put(id, template);
    }

    public void clear() {
        map.clear();
    }

    public @NotNull Set<String> keySet() {
        return map.keySet();
    }
}
