package me.matl114.utils.containers;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ArgsMap {
    public final Map<String, Object> args;

    public ArgsMap(Map<String, Object> args) {
        this.args = new HashMap<>(args);
    }

    public ArgsMap() {
        this.args = new HashMap<>();
    }

    public <T> ArgsMap put(String key, T value) {
        this.args.put(key, value);
        return this;
    }

    public <T> T get(String key) {
        return (T) this.args.get(key);
    }

    public <T> T getOr(String key, Supplier<T> defaultValue) {
        T value = get(key);
        if (value == null) {
            return defaultValue.get();
        } else {
            return value;
        }
    }
}
