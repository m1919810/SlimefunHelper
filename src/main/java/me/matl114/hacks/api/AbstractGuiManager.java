package me.matl114.hacks.api;

import java.util.ArrayList;
import java.util.List;
import me.matl114.gui.basic.SubScreenWidget;

public class AbstractGuiManager<T extends ModuleGuiProvider<?>> implements ModuleGuiProvider<SubScreenWidget> {
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

    // todo: implement group page
    @Override
    public SubScreenWidget createGui(int x, int y, int dx, int dy) {
        return null;
    }

    @Override
    public void saveGui(SubScreenWidget gui) {}
}
