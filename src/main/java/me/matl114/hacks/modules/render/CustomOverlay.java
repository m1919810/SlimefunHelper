package me.matl114.hacks.modules.render;

import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.StringRef;

public class CustomOverlay extends BaseModule {
    public final ModulePath customOverlay = makePath(Configs.RENDER_CONFIG, "custom-overlay");

    public CustomOverlay() {
        super("CustomOverlay");
        bindFlag(enable);
    }

    @Override
    public void registerAll() {
        super.registerAll();
    }

    public FlagRef enable = flagBuilder(customOverlay.add("enable-custom")).build();

    public StringRef texturePath = builder(customOverlay.add("enable-custom-path"), StringRef.TYPE)
            .defaultValue("slimefunhelper:textures/custom/genshin_impact.png")
            .validator(Configs.IDENTIFIER_VALIDATOR)
            .build();

    public IntRef color = builder(customOverlay.add("custom-background-color"), IntRef.TYPE)
            .defaultValue(-1)
            .build();

    public NBTRef<WrapColor> colorProgressbar = builder(customOverlay.add("custom-progress-bar-color"), WrapColor.class)
            .defaultValue(WrapColor.WHITE)
            .build();
}
