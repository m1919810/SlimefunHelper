package me.matl114.hacks.modules.render;

import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.StringRef;

public class CustomOverlay extends BaseModule {
    public static final String[] CUSTOM_OVERLAY = {"custom-overlay", "enable-custom"};
    public static final String[] CUSTOM_OVERLAY_PATH = {"custom-overlay", "enable-custom-path"};
    public static final String[] CUSTOM_OVERLAY_BACKGROUND_COLOR = {"custom-overlay", "custom-background-color"};
    public static final String[] PROGRESS_BAR = {"custom-overlay", "custom-progress-bar-color"};

    public CustomOverlay() {
        bindFlag(enable);
    }

    @Override
    public void registerAll() {
        super.registerAll();
    }

    public FlagRef enable = flagBuilder(Configs.RENDER_CONFIG, CUSTOM_OVERLAY).build();

    public StringRef texturePath = builder(Configs.RENDER_CONFIG, CUSTOM_OVERLAY_PATH, StringRef.TYPE)
            .defaultValue("slimefunhelper:textures/custom/genshin_impact.png")
            .validator(Configs.IDENTIFIER_VALIDATOR)
            .build();

    public IntRef color = builder(Configs.RENDER_CONFIG, CUSTOM_OVERLAY_BACKGROUND_COLOR, IntRef.TYPE)
            .defaultValue(-1)
            .build();

    public IntRef colorProgressbar = builder(Configs.RENDER_CONFIG, PROGRESS_BAR, IntRef.TYPE)
            .defaultValue(16777215)
            .build();
}
