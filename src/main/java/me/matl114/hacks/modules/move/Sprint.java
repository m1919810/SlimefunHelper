package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;

public class Sprint extends BaseModule implements LegalMovementManager.MovementModifier {
    public final ModulePath moveSpeed = makePath(Configs.MOV_CONFIG, "move-speed");
    public final ModulePath sprint = moveSpeed.add("sprint");

    public static LegalMovementManager.DelegateMovementModifier instance;
    // todo: 顶头跑 here
    public Sprint() {
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

    public final EnumRef<Configs.BypassMode> fakeSprintMode = builder(
                    sprint.add("fake-sprint-mode"), Configs.BypassMode.class)
            .defaultValue(Configs.BypassMode.NO_BYPASS)
            .build();

    public final FlagRef directionalSprint =
            flagBuilder(sprint.add("all-direction-sprint")).build();

    public final EnumRef<Configs.BypassMode> directionalSprintMode = builder(
                    sprint.add("bypass-mode"), Configs.BypassMode.class)
            .defaultValue(Configs.BypassMode.NO_BYPASS)
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
                    Debug.chat(ChatUtils.stringToText("&c[Sprint] &fToggle sprint on"));
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
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        if (directionalSprint.get() && directionalSprintMode.getValue().hasAc()) {
            PlayerInputUtils.Input input = PlayerInputUtils.of(mc.options);
            var player = movementManagerEvent.context.playerStatus.entity;
            if (mayWorkSprint()
                    && input.sprint()
                    && !input.forward()
                    && input.backward()
                    && !movementManagerEvent.context.hasImportantRotation()) {
                EntityUtils.setEntityYawSafe(player, player.getYaw() + 180);
                movementManagerEvent.context.markForResetRot();
                movementManagerEvent.context.markForMoveFix();
            }
        }
    }

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        //                if(player.getVelocity().horizontalLength() > 0.05)
        //                Debug.info("check vc", player.getVelocity().horizontalLength());

        // Debug.info(player.input.movementForward);
        if (directionalSprint.get() && directionalSprintMode.getValue().hasAc()) {
            PlayerInputUtils.Input input = PlayerInputUtils.of(player);
            if (lastTickLandingRotateJump) {
                input.jump(false);
            }
            input.applyInput(player);
        }
        if (directionalSprint.get() && directionalSprintMode.getValue() == Configs.BypassMode.NO_BYPASS) {
            PlayerInputUtils.Input input = PlayerInputUtils.of(player);
            if (mayWorkSprint() && input.backward() && !input.forward()) {
                // there is no rotation here
                enableSprintDirectionalThisTick = true;
            }
        }
    }

    boolean fakeSprintThisTick = false;

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        //        if (directionalSprint.get()
        //                && (player.input.playerInput.backward() && !player.input.playerInput.forward())
        //                && player.isSprinting()) {
        //            PlayerInputUtils.Input input = PlayerInputUtils.of(player);
        //            if (directionalSprintMode.getValue() == Configs.BypassMode.BYPASS_GRIM) {
        //                // do not use mixin, modify the input
        //                // enableSprintDirectionalThisTick = false;
        //                workRotationThisTick = true;
        //                //                            float yaw = EntityUtils.rotationToYaw(walkingWay);
        //                //                        Debug.info(walkingWay);
        //                //                        Debug.info(yaw);
        //                // turn around to bypass ,movingAround
        //
        //                EntityUtils.setEntityYawSafe(player, player.getYaw() + 180);
        //                // rotate the input
        //                //                    var input = PlayerInputUtils.of(player);
        //                // reverse input
        //                input.clone()
        //                        .right(input.left())
        //                        .left(input.right())
        //                        .forward(input.backward())
        //                        .backward(input.forward())
        //                        .applyInput(player);
        //            }
        //
        //            // }
        //
        //        }
        if (player.isSprinting()) {
            if (fakeSprint.get()) {
                if (!fakeSprintMode.get().hasAc()) {
                    fakeSprintThisTick = true;
                    player.setSprinting(false);
                    PlayerInputUtils.of(player).sprint(false).applyInput(player);
                } else {
                    // NO PLAN YET
                    //                    if (player.isSprinting()) {
                    //                        ClientPlayerAccess.of(player).onPlayerInputPackets();
                    //                        fakeSprintThisTick = true;
                    //                        player.setSprinting(false);
                    //                        //                        mc.getNetworkHandler().sendPacket(new
                    // ClientCommandC2SPacket(player,
                    //                        // ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                    //                        //                        mc.getNetworkHandler().sendPacket(new
                    // ClientCommandC2SPacket(player,
                    //                        // ClientCommandC2SPacket.Mode.START_SPRINTING));
                    //                        PlayerInputUtils.of(player).sprint(false).applyInput(player);
                    //                        ClientPlayerAccess.of(player).resyncSprint();
                    //                    }
                }
            }
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        enableSprintDirectionalThisTick = false;
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
        ModulePreset preset = event.context().getValue();
        switch (preset) {
            case HACKING, VANILLA -> {
                directionalSprintMode.set(Configs.BypassMode.NO_BYPASS);
            }
            default -> {
                directionalSprintMode.set(Configs.BypassMode.BYPASS_GRIM);
            }
        }
        switch (preset) {
            case AC_GRIM, AC_GRIM_LEGACY -> {
                fakeSprintMode.set(Configs.BypassMode.BYPASS_GRIM);
            }
            default -> {
                fakeSprintMode.set(Configs.BypassMode.NO_BYPASS);
            }
        }
    }
}
