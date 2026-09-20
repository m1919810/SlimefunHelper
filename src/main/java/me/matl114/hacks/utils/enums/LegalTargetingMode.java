package me.matl114.hacks.utils.enums;

import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.config.ConfigEnum;

public enum LegalTargetingMode implements ConfigEnum {
    NONE,
    DELAY_MOVEMENT,
    // PRE_MOVEMENT,
    // USEITEM_PACKET,
    LEGACY_SLIENT_ROT;

    public boolean isMovement() {
        return this == LegalTargetingMode.DELAY_MOVEMENT; // || this == LegalTargetingMode.PRE_MOVEMENT;
    }

    public boolean isLegal() {
        return this != NONE;
    }

    public boolean isLegacy() {
        return this == LegalTargetingMode.LEGACY_SLIENT_ROT;
    }

    public static LegalTargetingMode getFromPreset(ModulePreset preset) {
        return switch (preset) {
            case HACKING, VANILLA -> NONE;
            case AC_GRIM_LEGACY -> LEGACY_SLIENT_ROT;
            default -> DELAY_MOVEMENT;
        };
    }

    @Override
    public String getConfigEnumType() {
        return "legal_targeting_mode";
    }
}
