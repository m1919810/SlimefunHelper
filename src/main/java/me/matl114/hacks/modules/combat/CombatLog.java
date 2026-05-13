package me.matl114.hacks.modules.combat;

import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;

public class CombatLog extends BaseModule {
    public CombatLog() {}

    public final FlagRef enableHit =
            flagBuilder(Configs.COMBAT_CONFIG, makePath("")).build();
}
