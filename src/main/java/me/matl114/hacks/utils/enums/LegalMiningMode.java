package me.matl114.hacks.utils.enums;

import me.matl114.managers.config.ConfigEnum;

public enum LegalMiningMode implements ConfigEnum {
    NONE,
    LEGACY_SNAP,
    DELAY_MOVEMENT;

    @Override
    public String getConfigEnumType() {
        return "legal_mining_mode";
    }
}
