package me.matl114.gui.interfaces;

import java.util.function.Supplier;
import me.matl114.hacks.utils.gui.GuiColorSamplier;

public class ClickGuiElementFactory extends CustomElementFactory {
    public ClickGuiElementFactory(GuiColorSamplier config) {
        super(() -> config);
    }

    public ClickGuiElementFactory(Supplier<GuiColorSamplier> config) {
        super(config);
    }
}
