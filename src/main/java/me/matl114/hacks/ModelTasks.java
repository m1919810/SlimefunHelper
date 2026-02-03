package me.matl114.hacks;

import lombok.Getter;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.models.ModelExtra;
import me.matl114.hacks.modules.models.NewStyleModel;
import me.matl114.hacks.modules.models.SlimefunModels;
import me.matl114.hacks.modules.models.StorageDisplay;

public class ModelTasks {
    public static void init(){

    }
    public static final ModuleGroup moduleManager = new ModuleGroup("Model");
    @Getter
    public static ModelExtra modelExtra;
    @Getter
    public static NewStyleModel newStyleModel;
    @Getter
    public static SlimefunModels slimefunModels;
    @Getter
    public static StorageDisplay storageDisplay;



    private static void initModule(ModuleManager m){
        modelExtra = new ModelExtra()
            .register(m);
        newStyleModel = new NewStyleModel()
            .register(m);
        slimefunModels = new SlimefunModels()
            .register(m);
        storageDisplay = new StorageDisplay()
            .register(m);
    }
    static{
        moduleManager.registerFactories(ModelTasks::initModule);
        HackModules.registerModuleGroup(moduleManager);
    }
}
