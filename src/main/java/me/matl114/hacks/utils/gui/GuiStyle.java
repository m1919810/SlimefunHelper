package me.matl114.hacks.utils.gui;

import me.matl114.managers.config.ConfigEnum;

public enum GuiStyle implements ConfigEnum {
    VANILLA,
    CUSTOM;

    @Override
    public String getConfigEnumType() {
        return "gui_style";
    }
}
