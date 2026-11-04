package me.matl114.hacks.utils.enums;

import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.config.ConfigEnum;

public enum LegalServerRotMode implements ConfigEnum {
    NONE,
    LEGACY_SNAP,
    DELAY_MOVEMENT,
    USE_ITEM;

    @Override
    public String getConfigEnumType() {
        return "legal_server_rot_mode";
    }

    public static LegalServerRotMode getFromPreset(ModulePreset preset) {
        return switch (preset) {
            case HACKING, VANILLA -> LegalServerRotMode.NONE;
            case AC_GRIM_LEGACY -> LegalServerRotMode.LEGACY_SNAP;
            default -> LegalServerRotMode.USE_ITEM;
        };
    }
}
