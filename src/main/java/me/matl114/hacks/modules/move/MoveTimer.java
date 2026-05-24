package me.matl114.hacks.modules.move;

import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;

public class MoveTimer extends BaseModule {
    public final ModulePath moveSpeed = makePath(Configs.MOV_CONFIG, "move-speed");
    public final ModulePath moveTimer = moveSpeed.add("timer");

    public MoveTimer() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(moveTimer.add("timer-enable")).build();

    public final KeyBindRef keyBind = moduleEntry(
                    moveTimer.add("timer-enable-hotkey"), new MultiKeyBind(), moveTimer.add("timer-enable"))
            .build();

    public final IntRef timer = builder(moveTimer.add("multiply"), IntRef.TYPE)
            .defaultValue(0)
            .validator(Configs.INT_NONNEGATIVE)
            .build();
}
