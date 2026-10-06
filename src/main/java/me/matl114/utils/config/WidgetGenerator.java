package me.matl114.utils.config;

import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.TextAlign;
import me.matl114.gui.interfaces.ElementFactory;
import org.jetbrains.annotations.Nullable;

public interface WidgetGenerator<T> {
    public DrawableWidget generateWidget(ElementFactory factory, T val, int x, int y, int width, int height);

    default DrawableWidget generateWidget(
            ElementFactory factory, T val, int x, int y, int width, int height, @Nullable TextAlign overrideAlign) {
        return generateWidget(factory, val, x, y, width, height);
    }
}
