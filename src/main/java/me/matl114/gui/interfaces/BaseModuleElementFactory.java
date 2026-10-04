package me.matl114.gui.interfaces;

import java.util.function.Supplier;
import me.matl114.hacks.utils.gui.GuiColorSamplier;

public final class BaseModuleElementFactory extends ClickGuiElementFactory {
    public BaseModuleElementFactory(Supplier<GuiColorSamplier> config) {
        super(config);
    }
}
