package me.matl114.hacks.modules.move;

import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;

public class MoveTimer extends BaseModule {
    public static final String[] MOVE_TIMER_ENABLE = {"move-speed", "timer", "timer-enable"};
    public static final String[] MOVE_TIMER_ENABLE_HOTKEY = {"move-speed", "timer", "timer-enable-hotkey"};
    public static final String[] MOVE_TICK_TIMER = {"move-speed", "timer", "multiply"};

    public MoveTimer() {
        bindFlag(enable);
    }

    public final FlagRef enable =
            flagBuilder(Configs.MOV_CONFIG, MOVE_TIMER_ENABLE).build();

    public final KeyBindRef keyBind = toggleHotkey(
                    Configs.MOV_CONFIG,
                    MOVE_TIMER_ENABLE_HOTKEY,
                    new MultiKeyBind(),
                    MOVE_TIMER_ENABLE)
            .build();

    public final IntRef timer = builder(Configs.MOV_CONFIG, MOVE_TICK_TIMER, IntRef.TYPE)
            .defaultValue(0)
            .validator(Configs.INT_NONNEGATIVE)
            .build();
}
