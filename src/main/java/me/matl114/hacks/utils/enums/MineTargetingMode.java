package me.matl114.hacks.utils.enums;

import me.matl114.managers.config.ConfigEnum;

public enum MineTargetingMode implements ConfigEnum {
    NO_BYPASS,
    SWING_HAND,
    SWING_HAND_AND_ROT,
    SWING_HAND_AND_TARGET;

    public boolean hasSwing() {
        return this != NO_BYPASS;
    }

    @Override
    public String getConfigEnumType() {
        return "mine_targeting_mode";
    }
}
