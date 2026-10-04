package me.matl114.gui.interfaces;

import java.util.function.Supplier;
import me.matl114.hacks.utils.gui.GuiColorSamplier;

public final class ConfigElementFactory extends ClickGuiElementFactory {
    public ConfigElementFactory(Supplier<GuiColorSamplier> config) {
        super(config);
    }
}
