package me.matl114.hacks.utils.render;

import me.matl114.managers.config.ConfigEnum;

public enum NotifyType implements ConfigEnum {
    PS_WINDOW,
    TRAY;

    public String getConfigEnumType() {
        return "notify_type";
    }
}
