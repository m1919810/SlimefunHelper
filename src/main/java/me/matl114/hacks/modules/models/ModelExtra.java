package me.matl114.hacks.modules.models;

import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;

public class ModelExtra extends BaseModule {
    public final ModulePath modelConfig = makePath(Configs.MODEL_CONFIG, "model-config");

    public ModelExtra() {}

    public final FlagRef enableProtect =
            flagBuilder(modelConfig.add("enable-block-model-protect")).build();
}
