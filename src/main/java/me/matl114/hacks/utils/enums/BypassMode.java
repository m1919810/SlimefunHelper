package me.matl114.hacks.utils.enums;

import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.config.ConfigEnum;

public enum BypassMode implements ConfigEnum {
    NO_BYPASS,
    BYPASS_GRIM;

    public static BypassMode getFromPreset(ModulePreset preset) {
        return switch (preset) {
            case AC_GRIM_LEGACY, AC_GRIM -> BYPASS_GRIM;
            default -> NO_BYPASS;
        };
    }

    public boolean hasAc() {
        return this != NO_BYPASS;
    }

    @Override
    public String getConfigEnumType() {
        return "bypass_mode";
    }
}
