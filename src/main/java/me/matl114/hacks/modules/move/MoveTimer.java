package me.matl114.hacks.modules.move;

import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;

public class MoveTimer extends BaseModule {
    public static final String[] MOVE_TIMER_ENABLE = {"hotkeys-toggle", "speed-timer"};
    public static final String[] MOVE_TICK_TIMER = {"move-speed", "timer"};

    public MoveTimer() {
        bindFlag(enable);
    }

    public final FlagRef enable = toggle(MOVE_TIMER_ENABLE).build();

    public final KeyBindRef keyBind = toggleHotkey(
                    MOVE_TIMER_ENABLE, new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.KEY_Y))
            .build();

    public final IntRef timer = builder(Configs.MOV_CONFIG, MOVE_TICK_TIMER, IntRef.TYPE)
            .defaultValue(0)
            .validator(Configs.INT_NONNEGATIVE)
            .build();
}
