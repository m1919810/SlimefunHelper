package me.matl114.hacks.api;

import java.util.ArrayList;
import java.util.List;

public class AbstractManager<T> {
    public List<T> registered = new ArrayList<>();

    public void registerModule(T module) {
        registered.add(module);
    }

    public void unregisterModule(T module) {
        registered.remove(module);
    }

    public void unloadModules() {
        List<T> toRemove = new ArrayList<>(registered);
        registered.clear();
        toRemove.forEach(this::unregisterModule);
    }

    public void loadModules() {}

    public void reloadModules() {
        unloadModules();
        loadModules();
    }
}
