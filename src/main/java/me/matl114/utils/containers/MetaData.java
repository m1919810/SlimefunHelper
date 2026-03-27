package me.matl114.utils.containers;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class MetaData {
    Map<Object, Map<String, Object>> referenceMap = new WeakHashMap<>();

    public <W> void put(W val, String key, Object value) {
        referenceMap.computeIfAbsent(val, (s) -> new ConcurrentHashMap<>()).put(key, value);
    }

    public <W, T> T get(W val, String key) {
        var re = referenceMap.get(val);
        if (re != null) {
            return (T) re.get(key);
        } else {
            return null;
        }
    }

    public <W, T> T getOrPut(W val, String key, Supplier<T> supplier) {
        var re = referenceMap.computeIfAbsent(val, (s) -> new ConcurrentHashMap<>());
        return (T) re.computeIfAbsent(key, (s) -> supplier.get());
    }
}
