package me.matl114.hacks.modules.move;

import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public class ElytraFlight extends BaseModule implements LegalMovementManager.MovementModifier {
    // public static final String[]

    public static final String[] MOVE_ELYTRA_FLY = {"elytra", "simple-flight-control", "enable-control"};

    public static final String[] MOVE_ELYTRA_FLY_HOTKEY = {"elytra", "simple-flight-control", "enable-control-hotkey"};

    public static final String[] MOVE_ELYTRA_MOTION_CONTROL = {"elytra", "simple-flight-control", "enable-motion"};

    public static final String[] MOVE_ELYTRA_MOTION_MODE = {"elytra", "simple-flight-control", "motion-mode"};

    public static final String[] MOVE_ELYTRA_HEIGHT_CONTROL = {"elytra", "simple-flight-control", "enable-height"};

    public static final String[] MOVE_ELYTRA_MOTION_ADJUST = {"elytra", "simple-flight-control", "motion-adjust"};

    public static final String[] ELYTRA_PACKET_MOTION_AMOUNT = {"elytra", "custom-fireworks", "motion-amount"};

    public static final String[] ELYTRA_FLIGHT_CONTROL = {"elytra", "simple-flight-control", "enable-flight"};

    public static final String[] ELYTRA_NO_FALL_WHEN_CONTROL = {
        "elytra", "simple-flight-control", "no-fall-when-landing"
    };

    public FlagRef enable = flagBuilder(Configs.MOV_CONFIG, MOVE_ELYTRA_FLY).build();

    public KeyBindRef hotkey = toggleHotkey(
                    Configs.MOV_CONFIG, MOVE_ELYTRA_FLY_HOTKEY, new MultiKeyBind(), MOVE_ELYTRA_FLY)
            .build();

    public final DoubleRef packetMotion = builder(Configs.MOV_CONFIG, ELYTRA_PACKET_MOTION_AMOUNT, DoubleRef.TYPE)
            .defaultValue(0.05)
            .validator(Configs.doubleRange(0.0, 10000.0))
            .build();

    public final FlagRef simpleControlM =
            flagBuilder(Configs.MOV_CONFIG, MOVE_ELYTRA_MOTION_CONTROL).build();

    public final FlagRef simpleControlH =
            flagBuilder(Configs.MOV_CONFIG, MOVE_ELYTRA_HEIGHT_CONTROL).build();

    public final EnumRef<ElytraExtra.MotionMode> motionMode = builder(
                    Configs.MOV_CONFIG, MOVE_ELYTRA_MOTION_MODE, ElytraExtra.MotionMode.class)
            .defaultValue(ElytraExtra.MotionMode.VOID)
            .build();

    public final FlagRef motionAdjust = builder(Configs.MOV_CONFIG, MOVE_ELYTRA_MOTION_ADJUST, FlagRef.TYPE)
            .defaultValue(true)
            .build();

    public final FlagRef simpleControlE =
            flagBuilder(Configs.MOV_CONFIG, ELYTRA_FLIGHT_CONTROL).build();

    public final FlagRef noFallLand =
            flagBuilder(Configs.MOV_CONFIG, ELYTRA_NO_FALL_WHEN_CONTROL).build();

    private static LegalMovementManager.DelegateMovementModifier instance;

    public ElytraFlight() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_ROT.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enable);
    }

    @Override
    public void registerAll() {
        super.registerAll();
    }

    @Override
    public boolean mayModifyRotation() {
        return enable.get()
                && motionMode.get() == ElytraExtra.MotionMode.FIRE_WORKS
                && (simpleControlE.get()
                        || ((simpleControlM.get()
                                        && (mc.options.forwardKey.isPressed() != mc.options.backKey.isPressed()))
                                || (simpleControlH.get()
                                        && (mc.options.jumpKey.isPressed() != mc.options.sneakKey.isPressed()))));
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (player.isFallFlying() && enable.get()) {
            Vec3d controlMotion = new Vec3d(0, 0, 0);
            boolean shouldControl = false;
            double motionAmount = this.packetMotion.get();
            boolean shouldCheckRocket = false;
            PlayerInputUtils.Input input = PlayerInputUtils.of(mc.options);
            if (simpleControlE.get()) {
                boolean packetMotion = true;

                Vec3d movementInput = new Vec3d(input.sidewaysSpeed(), input.upwardSpeed(), input.forwardSpeed());
                Vec3d velocity = EntityUtils.movementInputToVelocity(movementInput, 1.0F, player.getYaw());

                if (motionMode.get() == ElytraExtra.MotionMode.FIRE_WORKS) {
                    packetMotion = false;
                    shouldCheckRocket = true;
                    if (MovTasks.getElytraExtra().canFireworkControlMotion() && motionAdjust.get()) {
                        packetMotion = true;
                    }
                }
                if (packetMotion) {
                    shouldControl = true;
                    controlMotion = velocity;
                }
            } else {
                if (simpleControlM.get()) {
                    // motion control
                    boolean forward = input.forward();
                    boolean backward = input.backward();
                    if (forward != backward) {
                        boolean packetMotion = true;
                        if (motionMode.get() == ElytraExtra.MotionMode.FIRE_WORKS) {
                            packetMotion = false;
                            shouldCheckRocket = true;
                            if (MovTasks.getElytraExtra().canFireworkControlMotion() && motionAdjust.get()) {
                                packetMotion = true;
                            }
                        }
                        if (packetMotion) {
                            shouldControl = true;
                            controlMotion = controlMotion.add(
                                    mc.player.getRotationVector().normalize().multiply(forward ? 1 : -1));
                        }
                    }
                }
                if (simpleControlH.get()) {
                    boolean upward = input.jump();
                    boolean downward = input.sneak();
                    if (upward != downward) {
                        boolean packetMotion = true;
                        if (motionMode.get() == ElytraExtra.MotionMode.FIRE_WORKS) {
                            packetMotion = false;
                            shouldCheckRocket = true;
                            if (MovTasks.getElytraExtra().canFireworkControlMotion() && motionAdjust.get()) {
                                packetMotion = true;
                            }
                        }
                        if (packetMotion) {
                            shouldControl = true;
                            controlMotion = controlMotion.add(0, upward ? 1 : -1, 0);
                        }
                    }
                }
            }

            if (shouldControl) {
                Vec3d wayVector = controlMotion.normalize();
                Vec3d realVector = wayVector.multiply(motionAmount);
                mc.player.setVelocity(realVector);
                controllingTick = true;
                if (Math.abs(realVector.y) <= 1e-7) {
                    modifyNoGravity = player.hasNoGravity();
                    player.setNoGravity(true);
                }
                if (motionMode.get() == ElytraExtra.MotionMode.FIRE_WORKS) {
                    // fliter zero control
                    if (realVector.lengthSquared() > 0) {
                        Vec2f py = EntityUtils.rotationToPitchYaw(wayVector);
                        modifyPitchYawThisTick = true;
                        EntityUtils.setEntityPitchSafe(mc.player, py.x);
                        EntityUtils.setEntityYawSafe(mc.player, py.y);
                    }
                }
            }
            if (shouldCheckRocket) {
                MovTasks.getElytraExtra().launchFirework(mc.player.getPitch(), mc.player.getYaw());
            }
        }
    }

    boolean controllingTick = false;
    boolean modifyPitchYawThisTick = false;
    Boolean modifyNoGravity = null;
    boolean nextTickIsOnGroundTick = false;

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (enable.get()
                && noFallLand.get()
                && controllingTick
                && !nextTickIsOnGroundTick
                && player.isOnGround()
                && !movementManagerEvent.context.playerStatus.onGround) {
            player.setPosition(player.getX(), movementManagerEvent.context.playerStatus.pos.y + 9E-8, player.getZ());
            ClientPlayerAccess.of(player).resyncPos();
            player.setOnGround(false);
            nextTickIsOnGroundTick = true;
        } else {
            nextTickIsOnGroundTick = false;
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (modifyPitchYawThisTick) {
            modifyPitchYawThisTick = false;
            movementManagerEvent.context().playerStatus.restoreRotation();
        }
        if (modifyNoGravity != null) {
            movementManagerEvent.context().playerStatus.entity.setNoGravity(modifyNoGravity);
            modifyNoGravity = null;
        }
        return true;
    }
}
