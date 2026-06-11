package me.matl114.hacks;

import lombok.Getter;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.models.*;

public class ModelTasks {
    public static void init() {}

    public static final ModuleGroup moduleManager = new ModuleGroup("Model");

    @Getter
    public static ModelExtra modelExtra;

    @Getter
    public static CustomTextures customTextures;

    @Getter
    public static NewStyleModel newStyleModel;

    @Getter
    public static SlimefunModels slimefunModels;

    private static void initModule(ModuleManager m) {
        modelExtra = new ModelExtra().register(m);
        customTextures = new CustomTextures().register(m);
        newStyleModel = new NewStyleModel().register(m);
        slimefunModels = new SlimefunModels().register(m);
    }

    static {
        moduleManager.registerFactories(ModelTasks::initModule);
        HackModules.registerModuleGroup(moduleManager);
    }
}
