package me.matl114.gui.interfaces;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import me.matl114.hacks.utils.config.GuiSetting;
import me.matl114.hacks.utils.gui.CustomGuiConfig;
import net.minecraft.util.Identifier;

/** A registered element style and its configuration policy. */
public record ElementStyle(
        Function<Supplier<GuiSetting>, ElementFactory> factory,
        boolean useConfig,
        Identifier id,
        CustomGuiConfig defaultedConfig) {
    public ElementStyle {
        Objects.requireNonNull(id);
        Objects.requireNonNull(factory);
        Objects.requireNonNull(defaultedConfig);
    }
}
