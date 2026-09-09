package me.matl114.hacks.modules.survival;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.OptionalPrimitive;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.client.network.ClientPlayerEntity;

public class WalkControl extends BaseModule {
    public static WalkControl INSTANCE;

    public WalkControl() {
        super("WalkControl");
        INSTANCE = this;
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.SURVIVAL_CONFIG, "survival-move-utils.walk-control");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final FlagRef enableForward = flagBuilder(root.add("forward")).build();

    public final FlagRef enableJump = flagBuilder(root.add("jump")).build();

    public final FlagRef enableSneak = flagBuilder(root.add("sneak")).build();

    public final EnumRef<Mode> mode =
            builder(root.add("mode"), Mode.class).defaultValue(Mode.NONE).build();

    public final NBTRef<OptionalPrimitive<Integer>> longPressReset = builder(
                    root.add("long-press-reset"), OptionalPrimitive.INT_TYPE)
            .defaultValue(new OptionalPrimitive<>(true, NBTTypes.INT_TYPE, 25))
            .show(() -> mode.get().isIn(Mode.PRESS_TOGGLE))
            .build();

    public final FlagRef enableAutoJump = flagBuilder(root.add("auto-jump")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onPreGameTick);
    }

    boolean currentForward;
    int lastForwardTicks;
    int lastJumpTicks;
    int lastSneakTicks;
    boolean currentJump;

    boolean currentSneak;

    public void onPreGameTick(Event<ClientPlayerEntity> event) {
        if (enable.get()) {
            if (enableForward.get()) {
                switch (mode.get()) {
                    case NONE -> currentForward = true;
                    case PRESS_TOGGLE -> {
                        if (lastForwardTicks <= 0 && mc.options.forwardKey.isPressed()) {
                            currentForward = !currentForward;
                        }
                        if (longPressReset.get().test((u) -> lastForwardTicks >= u)) {
                            currentForward = mc.options.forwardKey.isPressed();
                        }
                    }
                    case HOLD_USE -> {
                        currentForward = mc.options.forwardKey.isPressed();
                    }
                }
            } else {
                currentForward = false;
            }
            if (enableJump.get()) {
                switch (mode.get()) {
                    case NONE -> currentJump = true;
                    case PRESS_TOGGLE -> {
                        if (lastJumpTicks <= 0 && mc.options.jumpKey.isPressed()) {
                            currentJump = !currentJump;
                        }
                        if (longPressReset.get().test((u) -> lastJumpTicks >= u)) {
                            currentJump = mc.options.jumpKey.isPressed();
                        }
                    }
                    case HOLD_USE -> {
                        currentJump = mc.options.jumpKey.isPressed();
                    }
                }
            } else {
                currentJump = false;
            }
            if (enableSneak.get()) {
                switch (mode.get()) {
                    case NONE -> currentSneak = true;
                    case PRESS_TOGGLE -> {
                        if (lastSneakTicks <= 0 && mc.options.sneakKey.isPressed()) {
                            currentSneak = !currentSneak;
                        }
                        if (longPressReset.get().test((u) -> lastSneakTicks >= u)) {
                            currentSneak = mc.options.sneakKey.isPressed();
                        }
                    }
                    case HOLD_USE -> {
                        currentSneak = mc.options.sneakKey.isPressed();
                    }
                }
            } else {
                currentSneak = false;
            }
            PlayerInputManager.Modifier modifier = PlayerInputManager.Modifier.empty(0);
            if (currentForward) {
                modifier = modifier.withForward(true);
            }
            if (currentJump) {
                modifier = modifier.withJump(true);
            }
            if (currentSneak) {
                modifier = modifier.withSneak(true);
            }
            if (!currentJump && enableAutoJump.get()) {
                // todo: calculate landing
                if (mc.player.horizontalCollision) {
                    modifier = modifier.withJump(true);
                }
            }
            if (!modifier.isEmpty()) {
                PlayerInputManager.INSTANCE.addInputModifier(modifier);
            }
        }
        if (mc.options.forwardKey.isPressed()) {
            lastForwardTicks++;
        } else {
            lastForwardTicks = 0;
        }
        if (mc.options.jumpKey.isPressed()) {
            lastJumpTicks++;
        } else {
            lastJumpTicks = 0;
        }
        if (mc.options.sneakKey.isPressed()) {
            lastSneakTicks++;
        } else {
            lastSneakTicks = 0;
        }
    }

    public enum Mode implements ConfigEnum {
        NONE,
        PRESS_TOGGLE,
        HOLD_USE;

        @Override
        public String getConfigEnumType() {
            return "walk_control_mode";
        }
    }
}
