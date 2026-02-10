package me.matl114.hacks.modules.models;

import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;

public class ModelExtra extends BaseModule {
    public static final String[] MODEL_PROTECT = {"model-config", "enable-block-model-protect"};

    public ModelExtra() {}

    public final FlagRef enableProtect =
            flagBuilder(Configs.MODEL_CONFIG, MODEL_PROTECT).build();
}
