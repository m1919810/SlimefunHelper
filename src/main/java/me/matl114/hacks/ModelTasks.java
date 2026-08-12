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
    private static ModelExtra modelExtra;

    @Getter
    private static CustomTextures customTextures;

    @Getter
    private static NewStyleModel newStyleModel;

    @Getter
    private static SlimefunModels slimefunModels;

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
