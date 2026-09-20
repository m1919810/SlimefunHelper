package me.matl114.hacks.modules.extra;

import me.matl114.events.Event;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.util.Hand;

public class AntiAFK extends BaseModule {
    public AntiAFK() {
        super("AntiAFK");
    }

    public final ModulePath root = makePath(Configs.EXTRA_CONFIG, "other.anti-afk");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            toggleHotkey(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final FlagRef move = flagBuilder(root.add("move")).build();

    public final FlagRef jump = flagBuilder(root.add("jump")).build();

    public final FlagRef swing = flagBuilder(root.add("swing")).build();

    public final FlagRef rotate = flagBuilder(root.add("rotate")).build();

    public final IntRef delay = intBuilder(root.add("delay")).defaultValue(1200).build();

    boolean lastForward = false;
    boolean lastRotateBack = false;

    @Override
    public void registerAll() {
        super.registerAll();
    }

    public void onPreInputEvent(Event<Void> event) {
        if (enable.get() && PlayerStateManager.INSTANCE.lastActiveTicks < Tasks.getTick() - delay.get()) {
            if (move.get() || jump.get()) {
                var modifier = PlayerInputManager.Modifier.empty(0);
                if (move.get()) {
                    if (lastForward) {
                        modifier = modifier.withBackward(true);
                        ;
                        lastForward = false;
                    } else {
                        modifier = modifier.withForward(true);
                        lastForward = true;
                    }
                }
                if (jump.get()) {
                    modifier = modifier.withJump(true);
                }
                PlayerInputManager.INSTANCE.addInputModifier(modifier, 1);
            }
            if (swing.get()) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            if (rotate.get()) {
                if (lastRotateBack) {
                    mc.player.setYaw(mc.player.getYaw() + 30);
                    lastRotateBack = false;
                } else {
                    mc.player.setYaw(mc.player.getYaw() - 30);
                    lastRotateBack = true;
                }
            }
        }
    }
}
