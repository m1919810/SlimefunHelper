package me.matl114.utils.config;

import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.interfaces.ElementFactory;

public interface WidgetGenerator<T> {
    public DrawableWidget generateWidget(ElementFactory factory, T val, int x, int y, int width, int height);
}
