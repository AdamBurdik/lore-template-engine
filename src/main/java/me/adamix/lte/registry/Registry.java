package me.adamix.lte.registry;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Registry<T> {
    private final Map<String, T> map = new ConcurrentHashMap<>();

    public Optional<T> get(String id) {
        return Optional.ofNullable(map.get(id));
    }

    public void register(String id, T template) {
        map.put(id, template);
    }

    public void replace(Registry<T> other) {
        map.clear();
        map.putAll(other.map);
    }

    public void clear() {
        map.clear();
    }

    public Set<String> keySet() {
        return map.keySet();
    }
}
