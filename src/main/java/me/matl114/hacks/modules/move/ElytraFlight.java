package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.move.FlightVelocity;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public class ElytraFlight extends BaseModule implements LegalMovementManager.MovementModifier {
    public static ElytraFlight INSTANCE;
    public final ModulePath elytra = makePath(Configs.MOV_CONFIG, "elytra");
    public final ModulePath simpleFlightControl = elytra.add("simple-flight-control");
    public final ModulePath customFireworks = elytra.add("custom-fireworks");

    public FlagRef enable =
            flagBuilder(simpleFlightControl.add("enable-control")).build();

    public KeyBindRef hotkey = moduleEntry(
                    simpleFlightControl.add("enable-control-hotkey"),
                    new MultiKeyBind(),
                    simpleFlightControl.add("enable-control"),
                    moduleMeta(() -> this.controlMode))
            .build();

    public final DoubleRef packetMotion = builder(customFireworks.add("motion-amount"), DoubleRef.TYPE)
            .defaultValue(0.05)
            .validator(Configs.doubleRange(0.0, 10000.0))
            .build();

    public final EnumRef<ElytraExtra.MotionMode> motionMode = builder(
                    simpleFlightControl.add("motion-mode"), ElytraExtra.MotionMode.class)
            .defaultValue(ElytraExtra.MotionMode.VOID)
            .build();

    public final EnumRef<Mode> controlMode = builder(simpleFlightControl.add("flight-mode"), Mode.class)
            .defaultValue(Mode.CONTROL)
            .build();

    public final KeyBindRef motionToggle = hotkey(simpleFlightControl.add("flight-toggle"))
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::toggleMode))
            .build();

    public final FlagRef useFloatingUtils =
            flagBuilder(simpleFlightControl.add("use-floating-utils")).build();
    public final FlagRef horizontalFlyNoGravity = builder(
                    simpleFlightControl.add("horizontal-no-gravity"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef landAutoClose =
            flagBuilder(simpleFlightControl.add("land-auto-close")).build();

    public final DoubleRef motionArg = builder(simpleFlightControl.add("motion-lerp-argument"), DoubleRef.TYPE)
            .defaultValue(1.0D)
            .build();

    public final FlagRef motionArgLerpStarting = builder(simpleFlightControl.add("motion-lerp-starting"), Boolean.class)
            .defaultValue(false)
            .build();

    public final FlagRef useAutoRescale = flagBuilder(simpleFlightControl.add("use-auto-rescale"))
            .show(() -> ElytraExtra.INSTANCE.autoRescale.get())
            .build();
    boolean currentTakeOff = false;
    public final FlagRef autoFly =
            flagBuilder(simpleFlightControl.add("auto-fly")).build();

    public final KeyBindRef autoFlyKey = toggleHotkey(
                    simpleFlightControl.add("auto-fly-hotkey"), new MultiKeyBind(), simpleFlightControl.add("auto-fly"))
            .build();

    public final FlagRef autoFlyAutoJumpOff =
            flagBuilder(simpleFlightControl.add("auto-fly-auto-jump-off")).build();

    public final FlagRef autoFlyLandAutoClose =
            flagBuilder(simpleFlightControl.add("auto-fly-land-auto-close")).build();

    private static LegalMovementManager.DelegateMovementModifier instance;

    public ElytraFlight() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enable);
        INSTANCE = this;
    }

    @Override
    public int priority() {
        return PRIORITY_COMMON;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetLoad);
    }

    public boolean toggleMode() {
        controlMode.next();
        Debug.chat(
                ChatUtils.stringToText("&c[ElytraFlight] &fMode switch to"),
                controlMode.get().getDisplay());
        return false;
    }

    public boolean shouldFlyRocketOnFirstOff() {
        if (enable.get()) {
            if (autoFly.get()) return true;
            if (PlayerInputUtils.of(mc.options).hasMovementControl()) return true;
            return !useFloatingUtils.get();
        } else {
            return false;
        }
    }

    Vec3d lastVelocity = null;
    boolean duplicateRotSet = false;

    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (player.isFallFlying()) {
            currentTakeOff = true;
        }
        if (enable.get()) {
            if (player.isFallFlying()) {
                if (lastVelocity == null) {
                    lastVelocity = Vec3d.ZERO;
                }
                if (MovTasks.getElytraGrimAccelerate().enable.get()) {
                    // GrimAccelerate on
                    // close
                    return;
                }
                Vec3d controlMotion = new Vec3d(0, 0, 0);
                boolean shouldControl = false;
                double motionAmount = this.packetMotion.get();
                boolean shouldCheckRocket = false;
                PlayerInputUtils.Input input = PlayerInputUtils.of(mc.options);
                switch (controlMode.get()) {
                    case CONTROL -> {
                        boolean packetMotion = true;

                        Vec3d movementInput =
                                new Vec3d(input.sidewaysSpeed(), input.upwardSpeed(), input.forwardSpeed());
                        Vec3d velocity = EntityUtils.movementInputToVelocity(movementInput, 1.0F, player.getYaw());

                        if (motionMode.get() == ElytraExtra.MotionMode.FIRE_WORKS) {
                            packetMotion = false;
                            shouldCheckRocket = true;
                            if (MovTasks.getElytraExtra().canFireworkControlMotion()) {
                                packetMotion = true;
                            }
                        }
                        controlMotion = velocity;
                        if (packetMotion) {
                            shouldControl = true;
                        }
                    }
                    case ROTATION -> {
                        // todo
                        boolean packetMotion = true;
                        Vec3d velocity = EntityUtils.lookCoordToPos(
                                player.getPitch(), player.getYaw(), input.sidewaysSpeed(), 0, input.forwardSpeed());
                        Vec3d vertical = new Vec3d(0, input.upwardSpeed(), 0);
                        velocity = velocity.add(vertical);
                        if (motionMode.get() == ElytraExtra.MotionMode.FIRE_WORKS) {
                            packetMotion = false;
                            shouldCheckRocket = true;
                            if (MovTasks.getElytraExtra().canFireworkControlMotion()) {
                                packetMotion = true;
                            }
                        }
                        controlMotion = velocity;
                        if (packetMotion) {
                            shouldControl = true;
                        }
                    }
                }
                if (controlMotion.lengthSquared() < 1E-6 && autoFly.get()) {
                    controlMotion = mc.player.getRotationVector();
                }
                Vec3d wayVector = controlMotion.normalize();

                Vec3d realVector = wayVector.multiply(motionAmount);
                double a = this.motionArg.get();
                if (a != 1.0D
                        && (motionArgLerpStarting.get()
                                || lastVelocity.lengthSquared() > 0.01 * motionAmount * motionAmount)) {
                    realVector = realVector.multiply(a).add(lastVelocity.multiply(1.0D - a));
                    // 当玩家在操纵的时候，永远保持最高速
                    // 否则以1 - a为系数衰减
                    if (wayVector.lengthSquared() > 1E-6) {
                        realVector = realVector.normalize().multiply(motionAmount);
                    }
                }
                // add custom elytra event for bot to control elytra
                FlightVelocity velocity =
                        new FlightVelocity(realVector, motionAmount, FlightVelocity.Mode.ELYTRA_FLIGHT);
                Listener.getCustomListener().broadcast(new EventContainer<>(FlightVelocity.class, velocity));
                realVector = velocity.toVelocity();
                lastVelocity = realVector;
                if (lastVelocity.length() < 0.1 * motionAmount) {
                    lastVelocity = Vec3d.ZERO;
                }
                // add FloatingUtils
                if (FloatingUtils.INSTANCE.workGrimFloatingThisTick()) {
                    realVector = Vec3d.ZERO;
                    shouldControl = true;
                    shouldCheckRocket = false;
                } else if (useFloatingUtils.get() && realVector.lengthSquared() < 1e-4) {
                    if (!MovTasks.getElytraExtra().canFireworkControlMotion()) {
                        if (PlayerStateManager.INSTANCE.lastPlayerOnGround) {
                            shouldControl = true;
                            shouldCheckRocket = false;
                        } else {
                            realVector = Vec3d.ZERO;
                            FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                            shouldControl = true;
                            shouldCheckRocket = false;
                        }
                    } else {
                        shouldControl = true;
                        shouldCheckRocket = false;
                    }
                }
                // ground check
                if (mc.player.isOnGround() && realVector.y < 0) {
                    realVector = realVector.withAxis(Direction.Axis.Y, 0);
                }
                if (shouldControl) {
                    if (Math.abs(realVector.y) <= 1e-2 && horizontalFlyNoGravity.get() && !mc.player.isOnGround()) {
                        modifyNoGravity = player.hasNoGravity();
                        player.setNoGravity(true);
                    }
                    mc.player.setVelocity(
                            useAutoRescale.get()
                                    ? ElytraExtra.INSTANCE.applyAxisLimit(
                                            realVector, realVector.normalize(), !mc.player.hasNoGravity())
                                    : realVector);
                }
                if (motionMode.get() == ElytraExtra.MotionMode.FIRE_WORKS) {
                    // fliter zero control
                    if (realVector.lengthSquared() > 5e-3 && !movementManagerEvent.context.hasImportantRotation()) {
                        if (realVector.horizontalLengthSquared() > 5E-3) {
                            movementManagerEvent.context.pushImportantRotation(true, true);
                            Vec2f py = EntityUtils.rotationToPitchYaw(realVector.normalize());
                            movementManagerEvent.context.markForResetRot();
                            EntityUtils.setEntityPitchSafe(mc.player, py.x);
                            EntityUtils.setEntityYawSafe(mc.player, py.y);
                        } else {
                            movementManagerEvent.context.pushImportantRotation(true, false);
                            float pitch = EntityUtils.rotationToPitch(realVector.normalize());
                            movementManagerEvent.context.markForResetRot();
                            EntityUtils.setEntityPitchSafe(mc.player, pitch);
                        }
                        // pitch reset to trigger grim lastPitch lastYaw update
                        if (useAutoRescale.get()) {
                            float yaw = mc.player.getYaw();
                            if (Tasks.getTick() % 2 == 0) {
                                EntityUtils.setEntityYawSafe(mc.player, yaw + 0.01F);
                            } else {
                                EntityUtils.setEntityYawSafe(mc.player, yaw - 0.01F);
                            }
                        }
                    }
                }
                if (shouldCheckRocket) {
                    MovTasks.getElytraExtra().launchFirework(mc.player.getPitch(), mc.player.getYaw());
                }
            } else {
                lastVelocity = null;
                if (autoFly.get() && autoFlyAutoJumpOff.get()) {
                    ElytraExtra.INSTANCE.autoTakeoff();
                }
            }
        } else {
            lastVelocity = null;
        }
        if (currentTakeOff && !mc.player.isFallFlying() && mc.player.isOnGround()) {
            if (enable.get() && landAutoClose.get()) {
                HotKeyUtils.wrapFlagAsToggle(
                                simpleFlightControl.add("enable-control").toPath(), enable)
                        .run();
            }
            if (autoFly.get() && autoFlyLandAutoClose.get()) {
                HotKeyUtils.wrapFlagAsToggle(simpleFlightControl.add("auto-fly").toPath(), autoFly)
                        .run();
            }
            currentTakeOff = false;
        }
    }

    Boolean modifyNoGravity = null;

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (modifyNoGravity != null) {
            movementManagerEvent.context().playerStatus.entity.setNoGravity(modifyNoGravity);
            modifyNoGravity = null;
        }

        return true;
    }

    public void onPresetLoad(Event<EventContainer<ModulePreset>> presetEvent) {
        switch (presetEvent.context.getValue()) {
            case HACKING, VANILLA, AC_COMMON -> {
                motionMode.set(ElytraExtra.MotionMode.VOID);
            }
            case AC_VULCAN -> {
                motionMode.set(ElytraExtra.MotionMode.VOID);
                if (packetMotion.get() > 2.5F) {
                    packetMotion.set(2.5F);
                }
            }
            case AC_GRIM_LEGACY, AC_GRIM, AC_MATRIX -> {
                motionMode.set(ElytraExtra.MotionMode.FIRE_WORKS);
            }
        }
    }

    public enum Mode implements ConfigEnum {
        CONTROL,
        ROTATION;

        @Override
        public String getConfigEnumType() {
            return "elytramode";
        }
    }

    public enum GravityMode implements ConfigEnum {}
}
