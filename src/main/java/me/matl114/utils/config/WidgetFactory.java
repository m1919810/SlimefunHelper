package me.matl114.utils.config;

import me.matl114.gui.basic.DrawableWidget;

public interface WidgetFactory<T> {
    public DrawableWidget generateWidget(T val, int x, int y, int width, int height);
}
