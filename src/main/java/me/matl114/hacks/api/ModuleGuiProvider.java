package me.matl114.hacks.api;

import me.matl114.gui.basic.SubScreenWidget;

public interface ModuleGuiProvider<T extends SubScreenWidget> {
    // create a gui at the given position for data
    T createGui(int x, int y, int dx, int dy);
    // save the gui at the given position
    void saveGui(T gui);
}
