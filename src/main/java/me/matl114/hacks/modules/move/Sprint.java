package me.matl114.hacks.modules.move;

import java.util.Optional;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.hacks.utils.enums.BypassMode;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;

public class Sprint extends BaseModule implements LegalMovementManager.MovementModifier {
    public final ModulePath moveSpeed = makePath(Configs.MOV_CONFIG, "move-speed");
    public final ModulePath sprint = moveSpeed.add("sprint");

    public static LegalMovementManager.DelegateMovementModifier instance;
    public static Sprint INSTANCE;
    // todo: 顶头跑 here
    public Sprint() {
        super("Sprint");
        INSTANCE = this;
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            // register at here for the first time
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public final FlagRef autoSprintLegal =
            flagBuilder(sprint.add("legal-auto-sprint")).build();

    public final KeyBindRef hotkey = moduleEntry(
                    sprint.add("legal-auto-sprint-hotkey"), new MultiKeyBind(), sprint.add("legal-auto-sprint"))
            .build();

    public final FlagRef logSprint = flagBuilder(sprint.add("log-auto-sprint")).build();

    // todo: attack entity cause fake sprint, keep the state, do not send any other packets, try later
    public final FlagRef fakeSprint = flagBuilder(sprint.add("fake-sprint")).build();

    public final EnumRef<BypassMode> fakeSprintMode = builder(sprint.add("fake-sprint-mode"), BypassMode.class)
            .defaultValue(BypassMode.NO_BYPASS)
            .build();

    public final FlagRef directionalSprint =
            flagBuilder(sprint.add("all-direction-sprint")).build();

    public final EnumRef<BypassMode> directionalSprintMode = builder(sprint.add("bypass-mode"), BypassMode.class)
            .defaultValue(BypassMode.NO_BYPASS)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onTick);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
    }

    public boolean enableSprintDirectionalThisTick = false;
    // check if we can speedup using moveFoward+sideway
    // no use: sidewaywalk no faster than foward, but jump-sprint much faster

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        enableSprintDirectionalThisTick = false;
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        enableSprintDirectionalThisTick = false;
    }

    public void onTick(Event<ClientPlayerEntity> event) {
        if (autoSprintLegal.get()) {
            if (!mc.options.sprintKey.isPressed()
                    && PlayerInputUtils.of(mc.options).hasWASDMovement()) {
                mc.options.sprintKey.setPressed(true);
                if (logSprint.get()) {
                    logI18N("message.module.sprint.toggle-on");
                }
            }
        }
        if (directionalSprint.get()
                && directionalSprintMode.getValue().hasAc()
                && mayWorkSprint()
                && PlayerInputUtils.of(mc.options).sprint()) {
            var input = PlayerInputUtils.of(mc.options);
            // be legal,
            if (!input.forward() && input.hasWASDMovement()) {
                float yaw;
                if (input.backward()) {
                    yaw = 180;
                } else {
                    yaw = 180 + input.sidewaysSpeed() * 90;
                }
                if (yaw != 0.0F) {
                    RotationManager.INSTANCE.request(
                            RotationManager.ofDirection(Optional.empty(), Optional.of(mc.player.getYaw() + yaw))
                                    .preTick(() -> {
                                        if (lastTickLandingRotateJump) {
                                            PlayerInputManager.INSTANCE.addJumpModifier(0, false, 0);
                                        }
                                        PlayerInputManager.INSTANCE.addSprintModifier(0, true, 0);
                                    })
                                    .occupied(false));
                }
            }
        }
    }

    @Override
    public int priority() {
        // the least important shit
        return PRIORITY_HIGHEST;
    }

    boolean lastTickLandingRotateJump = false;

    public boolean mayWorkSprint() {
        return !mc.player.isFallFlying()
                && !mc.player.isSwimming()
                && !mc.player.isClimbing()
                && !mc.player.isTouchingWater()
                && !mc.player.isSubmergedInWater()
                && !(mc.player.horizontalCollision && !mc.player.collidedSoftly);
    }

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        //                if(player.getVelocity().horizontalLength() > 0.05)
        //                Debug.info("check vc", player.getVelocity().horizontalLength());

        // Debug.info(player.input.movementForward);
        enableSprintDirectionalThisTick = false;
        if (directionalSprint.get() && directionalSprintMode.getValue() == BypassMode.NO_BYPASS) {
            PlayerInputUtils.Input input = PlayerInputUtils.of(player);
            if (mayWorkSprint() && input.hasWASDMovement() && !input.forward()) {
                // there is no rotation here
                enableSprintDirectionalThisTick = true;
            }
        }
    }

    boolean fakeSprintThisTick = false;

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (player.isSprinting()) {
            if (fakeSprint.get()) {
                if (!fakeSprintMode.get().hasAc()) {
                    fakeSprintThisTick = true;
                    player.setSprinting(false);
                    PlayerInputUtils.of(player).sprint(false).applyInput(player);
                }
            }
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        lastTickLandingRotateJump = false;
        if (!movementManagerEvent.context.playerStatus.onGround
                && movementManagerEvent.context.playerStatus.entity.isOnGround()) {
            lastTickLandingRotateJump = true;
        }

        if (fakeSprintThisTick) {
            movementManagerEvent.context.playerStatus.entity.setSprinting(true);
            fakeSprintThisTick = false;
        }
        return true;
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        if (event.context().getValue().isOptionPreset()) return;
        ModulePreset preset = event.context().getValue();
        switch (preset) {
            case HACKING, VANILLA -> {
                directionalSprintMode.set(BypassMode.NO_BYPASS);
            }
            default -> {
                directionalSprintMode.set(BypassMode.BYPASS_GRIM);
            }
        }
        switch (preset) {
            case AC_GRIM, AC_GRIM_LEGACY -> {
                fakeSprintMode.set(BypassMode.BYPASS_GRIM);
            }
            default -> {
                fakeSprintMode.set(BypassMode.NO_BYPASS);
            }
        }
    }
}
