package me.matl114.hacks.modules;

import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.hacks.api.AbstractGuiManager;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.utils.Debug;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModuleMain extends AbstractGuiManager<ModuleGroup>  {
    Map<String, ModuleGroup> moduleGroups = new LinkedHashMap<>();
    public ModuleMain() {

    }

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

    //todo:
    @Override
    public SubScreenWidget createGui(int x, int y, int dx, int dy) {
        return null;
    }

    @Override
    public void saveGui(SubScreenWidget gui) {

    }

    @Override
    public void unloadModules() {
        //remove all unload logic, this shouldn't be unloaded if it work as intended
    }

    @Override
    public void reloadModules() {
        this.registered.forEach(ModuleGroup::reloadModules);
    }
}
