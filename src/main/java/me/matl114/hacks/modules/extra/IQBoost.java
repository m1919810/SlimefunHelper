package me.matl114.hacks.modules.extra;

import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;

public class IQBoost extends BaseModule {
    public IQBoost() {}

    ModulePath path = makePath(Configs.MISC_CONFIG, "iq-boost");

    public final FlagRef enable = flagBuilder(path.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(path.addHotkey(), new MultiKeyBind(), path.addEnable()).build();

    @Override
    public void registerAll() {
        super.registerAll();
    }
}
