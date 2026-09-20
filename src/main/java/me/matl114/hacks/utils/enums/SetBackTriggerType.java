package me.matl114.hacks.utils.enums;

import me.matl114.managers.config.ConfigEnum;

public enum SetBackTriggerType implements ConfigEnum {
    SIMULATION,
    CRASH_PACKETS;

    @Override
    public String getConfigEnumType() {
        return "setback_trigger_type";
    }
}
