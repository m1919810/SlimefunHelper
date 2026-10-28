package me.matl114.hacks.modules.render;

import java.awt.Color;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.StringRef;
import net.minecraft.util.Identifier;

public class SkyRender extends BaseModule {
    public static SkyRender INSTANCE;

    public final ModulePath root = makePath(Configs.RENDER_CONFIG, "sky-render");
    public final ModulePath colorSettings = root.add("color-override");
    public final ModulePath backgroundSettings = root.add("background");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();
    public final FlagRef customColor = flagBuilder(colorSettings.addEnable()).build();
    public final NBTRef<WrapColor> color = builder(colorSettings.add("color"), WrapColor.class)
            .defaultValue(new WrapColor(new Color(120, 170, 255)))
            .build();
    public final FlagRef skyBackground =
            flagBuilder(backgroundSettings.addEnable()).build();
    public final StringRef background = builder(backgroundSettings.add("texture"), StringRef.TYPE)
            .defaultValue("slimefunhelper:textures/custom/genshin_impact.png")
            .validator(value -> value.isEmpty() || Configs.IDENTIFIER_VALIDATOR.test(value))
            .build();

    public SkyRender() {
        super("SkyRender");
        bindFlag(enable);
        INSTANCE = this;
    }

    public boolean hasCustomColor() {
        return isActive() && customColor.get();
    }

    public Identifier getBackground() {
        return isActive() && skyBackground.get() && !background.get().isEmpty()
                ? Identifier.tryParse(background.get())
                : null;
    }
}
