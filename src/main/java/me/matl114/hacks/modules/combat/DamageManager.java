package me.matl114.hacks.modules.combat;

import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;

public class DamageManager extends BaseModule {
    public final ModulePath combat = makePath(Configs.COMBAT_CONFIG, "attack");
    public static DamageManager INSTANCE;

    public DamageManager() {
        INSTANCE = this;
    }

    @Override
    public void registerAll() {
        super.registerAll();
    }
}
