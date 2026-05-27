package me.matl114.hacks.modules;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import me.matl114.hacks.api.AbstractManager;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.utils.Debug;

public class ModuleMain extends AbstractManager<ModuleGroup> {
    @Getter
    Map<String, ModuleGroup> moduleGroups = new LinkedHashMap<>();

    public ModuleMain() {}

    @Override
    public void registerModule(ModuleGroup module) {
        super.registerModule(module);
        moduleGroups.put(module.getName(), module);
    }

    @Override
    public void unregisterModule(ModuleGroup module) {
        Debug.info("Unexpected unregister in a moduleGroup! " + module.getName());
        super.unregisterModule(module);
        moduleGroups.remove(module.getName());
    }

    @Override
    public void unloadModules() {
        // remove all unload logic, this shouldn't be unloaded if it work as intended
    }

    @Override
    public void reloadModules() {
        this.registered.forEach(ModuleGroup::reloadModules);
    }
}
