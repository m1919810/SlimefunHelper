package me.matl114.hacks.modules.move;

import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;

public class Sprint extends BaseModule implements LegalMovementManager.MovementModifier {
    public static final String[] MOVE_AUTO_TOGGLE_SPRINT = {"move-speed", "sprint", "legal-auto-sprint"};
    public static final String[] FAKE_SPRINT = {"move-speed", "sprint", "fake-sprint"};
    public static final String[] FAKE_SPRINT_MODE = {"move-speed", "sprint", "fake-sprint-mode"};
    public static final String[] MOVE_ALL_DIRECTION_SPRINT = {"move-speed", "sprint", "all-direction-sprint"};
    public static final String[] MOVE_SPRINT_BYPASS_MODE = {"move-speed", "sprint", "bypass-mode"};

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
            flagBuilder(Configs.MOV_CONFIG, MOVE_AUTO_TOGGLE_SPRINT).build();
    // todo: attack entity cause fake sprint, keep the state, do not send any other packets, try later
    public final FlagRef fakeSprint =
            flagBuilder(Configs.MOV_CONFIG, FAKE_SPRINT).build();

    public final EnumRef<Configs.BypassMode> fakeSprintMode = builder(
                    Configs.MOV_CONFIG, FAKE_SPRINT_MODE, Configs.BypassMode.class)
            .defaultValue(Configs.BypassMode.NO_BYPASS)
            .build();

    public final FlagRef directionalSprint =
            flagBuilder(Configs.MOV_CONFIG, MOVE_ALL_DIRECTION_SPRINT).build();

    public final EnumRef<Configs.BypassMode> directionalSprintMode = builder(
                    Configs.MOV_CONFIG, MOVE_SPRINT_BYPASS_MODE, Configs.BypassMode.class)
            .defaultValue(Configs.BypassMode.NO_BYPASS)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onTick);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
    }

    public boolean enableSprintDirectionalThisTick = false;
    // check if we can speedup using moveFoward+sideway
    // no use: sidewaywalk no faster than foward, but jump-sprint much faster

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        enableSprintDirectionalThisTick = false;
        workRotationThisTick = false;
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        enableSprintDirectionalThisTick = false;
        workRotationThisTick = false;
    }

    public void onTick(Event<ClientPlayerEntity> event) {
        if (autoSprintLegal.get() && mc.currentScreen == null) {
            if (!mc.options.sprintKey.isPressed()) {
                Debug.chat("[Sprint] toggle sprint on");
                mc.options.sprintKey.setPressed(true);
            }
        }
    }

    boolean workRotationThisTick;

    @Override
    public int priority() {
        // the least important shit
        return PRIORITY_HIGHEST;
    }

    @Override
    public boolean mayModifyRotation() {
        return directionalSprint.get()
                && !mc.player.input.hasForwardMovement()
                && directionalSprintMode.getValue() != Configs.BypassMode.NO_BYPASS;
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {}

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        //                if(player.getVelocity().horizontalLength() > 0.05)
        //                Debug.info("check vc", player.getVelocity().horizontalLength());

        // Debug.info(player.input.movementForward);
        if (directionalSprint.get()) {
            PlayerInputUtils.Input input = PlayerInputUtils.of(player.input);
            if (input.backward()
                    && !input.forward()
                    && !(player.isTouchingWater() && !player.isSubmergedInWater())
                    && !(player.horizontalCollision && !player.collidedSoftly)) {
                enableSprintDirectionalThisTick = true;
                if (player.isSprinting() && directionalSprintMode.getValue() == Configs.BypassMode.BYPASS_GRIM) {}
            }
        }
    }

    boolean fakeSprintThisTick = false;

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (directionalSprint.get()
                && (player.input.playerInput.backward() && !player.input.playerInput.forward())
                && player.isSprinting()) {
            PlayerInputUtils.Input input = PlayerInputUtils.of(player.input);
            if (directionalSprintMode.getValue() == Configs.BypassMode.BYPASS_GRIM) {
                // do not use mixin, modify the input
                // enableSprintDirectionalThisTick = false;
                workRotationThisTick = true;
                //                            float yaw = EntityUtils.rotationToYaw(walkingWay);
                //                        Debug.info(walkingWay);
                //                        Debug.info(yaw);
                // turn around to bypass ,movingAround

                EntityUtils.setEntityYawSafe(player, player.getYaw() + 180);
                // rotate the input
                //                    var input = PlayerInputUtils.of(player.input);
                // reverse input
                input.clone()
                        .right(input.left())
                        .left(input.right())
                        .forward(input.backward())
                        .backward(input.forward())
                        .applyInput(player.input);
            }

            // }

        }
        if (player.isSprinting()) {
            if (fakeSprint.get()) {
                if (!fakeSprintMode.get().hasAc()) {
                    fakeSprintThisTick = true;
                    player.setSprinting(false);
                    PlayerInputUtils.of(player.input).sprint(false).applyInput(player.input);
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
                    //                        PlayerInputUtils.of(player.input).sprint(false).applyInput(player.input);
                    //                        ClientPlayerAccess.of(player).resyncSprint();
                    //                    }
                }
            }
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        enableSprintDirectionalThisTick = false;
        if (enabledThisTick) {
            if (workRotationThisTick) {
                workRotationThisTick = false;
                movementManagerEvent.context.playerStatus.restoreRotation();
                ClientPlayerAccess.of(movementManagerEvent.context.playerStatus.entity)
                        .resyncRot();
            }
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
            case AC_GRIM -> {
                fakeSprintMode.set(Configs.BypassMode.BYPASS_GRIM);
            }
            default -> {
                fakeSprintMode.set(Configs.BypassMode.NO_BYPASS);
            }
        }
    }
}
