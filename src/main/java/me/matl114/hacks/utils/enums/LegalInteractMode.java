package me.matl114.hacks.utils.enums;

import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.config.ConfigEnum;

public enum LegalInteractMode implements ConfigEnum {
    NONE,
    MOVEMENT_POST,
    DELAY_MOVEMENT,
    USEITEM_PACKET,
    LEGACY_SLIENT_ROT;
    ;

    public static LegalInteractMode getFromPreset(ModulePreset preset) {
        return switch (preset) {
            case HACKING, VANILLA -> LegalInteractMode.NONE;
            case AC_GRIM_LEGACY -> LegalInteractMode.LEGACY_SLIENT_ROT;
            default -> LegalInteractMode.DELAY_MOVEMENT;
        };
    }

    @Override
    public String getConfigEnumType() {
        return "legal_interact_mode";
    }

    public boolean isLegal() {
        return this != NONE;
    }

    public boolean canMultiRotPlace() {
        return this == NONE || this == LEGACY_SLIENT_ROT;
    }
}
