package me.matl114.hacks.modules;

import me.matl114.hacks.api.ModuleGroup;

public class HackModules {

    public static final ModuleMain main = new ModuleMain();

    public static void registerModuleGroup(ModuleGroup group) {
        main.registerModule(group);
    }

    public static ModuleGroup getModuleGroup(String name) {
        return main.moduleGroups.get(name);
    }

    public static void reloadModuleGroups() {
        main.reloadModules();
    }

    public static void init() {}
}
