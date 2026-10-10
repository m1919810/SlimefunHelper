package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.combat.ElytraBot;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.OptionalPrimitive;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.hacks.utils.move.ElytraMotion;
import me.matl114.hacks.utils.move.ElytraOptimizeUtils;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
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

    public final IntRef fireworksRotationLastingTicks = builder(
                    simpleFlightControl.add("firework-rotation-last-ticks"), Integer.class)
            .defaultValue(3)
            .validator(Configs.intRange(0, 10))
            .show(() -> motionMode.get().isIn(ElytraExtra.MotionMode.FIRE_WORKS))
            .build();

    public final FlagRef rotateWhenVoid = builder(simpleFlightControl.add("rotate-when-void-motion"), Boolean.class)
            .defaultValue(true)
            .show(() -> motionMode.get().isIn(ElytraExtra.MotionMode.VOID))
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

    public final FlagRef pauseWhenAccelerate = builder(simpleFlightControl.add("pause-when-accelerate"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef landAutoClose =
            flagBuilder(simpleFlightControl.add("land-auto-close")).build();

    public final NBTRef<OptionalPrimitive<Integer>> takeOffOptimize = builder(
                    simpleFlightControl.add("hold-jump-takeoff-ticks"), OptionalPrimitive.INT_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.INT_TYPE, 10))
            .build();

    public final FlagRef useAutoRescale = flagBuilder(simpleFlightControl.add("use-auto-rescale"))
            .show(() -> ElytraExtra.INSTANCE.autoRescale.get())
            .build();

    public final NBTRef<OptionalPrimitive<Double>> overridePullupAngle = builder(
                    simpleFlightControl.add("override-pullup-angle"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 45.0D))
            .validator(s -> s.getValue() > 0 && s.getValue() < 90)
            .show(() -> this.controlMode.get().isIn(Mode.CONTROL))
            .build();

    public final NBTRef<OptionalPrimitive<Double>> overrideDownwardAngle = builder(
                    simpleFlightControl.add("override-downward-angle"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 45.0D))
            .validator(s -> s.getValue() > 0 && s.getValue() < 90)
            .show(() -> this.controlMode.get().isIn(Mode.CONTROL))
            .build();

    public final NBTRef<OptionalPrimitive<Double>> overrideHorizontalFlyAngle = builder(
                    simpleFlightControl.add("override-horizontal-fly-angle"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 0.1))
            .validator(s -> s.getValue() > -90 && s.getValue() < 90)
            .show(() -> this.controlMode.get().isIn(Mode.CONTROL))
            .build();

    public final FlagRef autoRescaleBestClimbingSpeed = flagBuilder(
                    simpleFlightControl.add("use-auto-rescale-best-climbing-speed"))
            .show(() -> ElytraExtra.INSTANCE.autoRescale.get())
            .build();

    public final FlagRef autoPullupModeWhenPressBlankOnly = flagBuilder(
                    simpleFlightControl.add("auto-pull-up-mode-when-press-blank-only"))
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
        super("ElytraFlight");
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
        registerListener(Listener.getPreGameTick(), this::onPreGameTick);
    }

    private void onPreGameTick(Event<ClientPlayerEntity> event) {
        if (shouldControlFlight() && PlayerInputUtils.of(mc.options).hasMovementControl()) {
            if (motionMode.get().isIn(ElytraExtra.MotionMode.FIRE_WORKS)) {
                if (ElytraExtra.INSTANCE.canFireworkControlMotion(fireworksRotationLastingTicks.get())) {
                    RotationManager.INSTANCE.request(RotationManager.EMPTY);
                }
            } else if (motionMode.get().isIn(ElytraExtra.MotionMode.VOID)) {
                RotationManager.INSTANCE.request(RotationManager.EMPTY);
            }
        }
    }

    public boolean toggleMode() {
        controlMode.next();
        Debug.chat(
                ChatUtils.stringToText("&c[ElytraFlight] &fMode switch to"),
                controlMode.get().getDisplay());
        return false;
    }

    public boolean shouldControlFlight() {
        return enable.get() || (ElytraBot.INSTANCE.canControlFlight());
    }

    public boolean shouldFlyRocketOnFirstOff() {
        if (shouldControlFlight()) {
            if (autoFly.get()) return true;
            if (PlayerInputUtils.of(mc.options).hasMovementControl()) return true;
            return !useFloatingUtils.get();
        } else {
            return false;
        }
    }

    int holdJumpCounter = 0;
    boolean controllingThisTick = false;

    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (player.isFallFlying()) {
            currentTakeOff = true;
        }
        if (mc.options.jumpKey.isPressed()) {
            holdJumpCounter++;
        } else {
            holdJumpCounter = 0;
        }
        controllingThisTick = false;
        if (shouldControlFlight()) {
            if (!player.isFallFlying()
                    && takeOffOptimize.get().isPresent()
                    && holdJumpCounter >= takeOffOptimize.get().getValue()) {
                if (!mc.player.isOnGround() && mc.player.checkFallFlying()) {
                    MovExtra.INSTANCE.sendPacketsForPreStartFallFlying();
                    mc.getNetworkHandler()
                            .sendPacket(new ClientCommandC2SPacket(
                                    mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    MovExtra.INSTANCE.sendPacketsForPostStartFallFlying();
                }
            }
            if (player.isFallFlying()) {
                if (MovTasks.getElytraGrimAccelerate().enable.get()) {
                    // GrimAccelerate on
                    // close
                    return;
                }
                ElytraExtra.Al algorithm = ElytraExtra.INSTANCE.autoRescaleAl.get();
                Vec3d controlMotion;
                boolean shouldControl = false;
                double motionAmount = this.packetMotion.get();
                boolean shouldCheckRocket = false;
                boolean shouldControlRotation = false;
                PlayerInputUtils.Input input = PlayerInputUtils.of(mc.options);
                Vec2f motionRotation;
                switch (controlMode.get()) {
                    case CONTROL -> {
                        boolean packetMotion = true;

                        Vec3d movementInput =
                                new Vec3d(input.sidewaysSpeed(), input.upwardSpeed(), input.forwardSpeed());
                        if (movementInput.horizontalLength() < 1E-2
                                && movementInput.y > 0.5
                                && autoPullupModeWhenPressBlankOnly.get()) {
                            algorithm = ElytraOptimizeUtils.getPullupOnlyAl(algorithm);
                        }
                        Vec2f rotation = EntityUtils.movementInputToRotation(movementInput, player.getYaw());
                        motionRotation = rotation;
                        if (useAutoRescale.get() && autoRescaleBestClimbingSpeed.get()) {
                            if (movementInput.y > 0 && rotation.x < 0) {
                                Vec2f best =
                                        ElytraOptimizeUtils.calculateBestPullupSpeed(rotation.x, rotation.y, algorithm);
                                motionRotation = best;
                            } else if (movementInput.y < 0 && rotation.x > 0) {
                                Vec2f best = ElytraOptimizeUtils.calculateBestDownForwardSpeed(
                                        rotation.x, rotation.y, algorithm, false, true);
                                motionRotation = best;
                            }
                        }
                        if (movementInput.horizontalLengthSquared() > 0.0D) {
                            if (movementInput.y > 0) {
                                if (overridePullupAngle.get().isPresent()) {
                                    double angle = overridePullupAngle.get().getValue();
                                    motionRotation = new Vec2f((float) -angle, motionRotation.y);
                                }
                            } else if (movementInput.y < 0) {
                                if (overrideDownwardAngle.get().isPresent()) {
                                    double angle = overrideDownwardAngle.get().getValue();
                                    motionRotation = new Vec2f((float) angle, motionRotation.y);
                                }
                            } else {
                                if (overrideHorizontalFlyAngle.get().isPresent()) {
                                    double angle =
                                            overrideHorizontalFlyAngle.get().getValue();
                                    motionRotation = new Vec2f((float) angle, motionRotation.y);
                                }
                            }
                        }

                        if (motionMode.get() == ElytraExtra.MotionMode.FIRE_WORKS) {
                            packetMotion = false;
                            shouldCheckRocket = true;
                            if (MovTasks.getElytraExtra().canFireworkControlMotion()) {
                                packetMotion = true;
                            }
                            if (ElytraExtra.INSTANCE.canFireworkControlMotion(fireworksRotationLastingTicks.get())) {
                                shouldControlRotation = true;
                            }
                        } else if (motionMode.get() == ElytraExtra.MotionMode.VOID) {
                            if (rotateWhenVoid.get()) {
                                shouldControlRotation = true;
                            }
                        }
                        controlMotion = EntityUtils.pitchYawToRotation(motionRotation.x, motionRotation.y)
                                .multiply(movementInput.length())
                                .normalize();
                        if (packetMotion) {
                            shouldControl = true;
                        }
                    }
                    case ROTATION -> {
                        // todo
                        boolean packetMotion = true;
                        Vec3d velocity = EntityUtils.lookCoordToPos(
                                player.getPitch(), player.getYaw(), input.sidewaysSpeed(), 0, input.forwardSpeed());
                        Vec3d movementInput =
                                new Vec3d(input.sidewaysSpeed(), input.upwardSpeed(), input.forwardSpeed());

                        if (movementInput.horizontalLength() < 1E-2
                                && movementInput.y > 0.5
                                && autoPullupModeWhenPressBlankOnly.get()) {
                            algorithm = ElytraOptimizeUtils.getPullupOnlyAl(algorithm);
                        }
                        Vec3d vertical = new Vec3d(0, input.upwardSpeed(), 0);
                        velocity = velocity.add(vertical);
                        if (velocity.horizontalLength() < 1E-12) {
                            motionRotation =
                                    new Vec2f(EntityUtils.rotationToPitch(velocity.normalize()), mc.player.getYaw());
                        } else {
                            motionRotation = EntityUtils.rotationToPitchYaw(velocity.normalize());
                        }
                        if (input.upwardSpeed() > 0
                                && velocity.y > 0
                                && useAutoRescale.get()
                                && autoRescaleBestClimbingSpeed.get()) {
                            Vec2f best = ElytraOptimizeUtils.calculateBestPullupSpeed(
                                    motionRotation.x, motionRotation.y, algorithm);
                            motionRotation = best;
                            velocity = EntityUtils.pitchYawToRotation(best.x, best.y)
                                    .multiply(velocity.length());
                        }

                        if (motionMode.get() == ElytraExtra.MotionMode.FIRE_WORKS) {
                            packetMotion = false;
                            shouldCheckRocket = true;
                            if (MovTasks.getElytraExtra().canFireworkControlMotion()) {
                                packetMotion = true;
                            }
                            if (ElytraExtra.INSTANCE.canFireworkControlMotion(fireworksRotationLastingTicks.get())) {
                                shouldControlRotation = true;
                            }
                        } else if (motionMode.get() == ElytraExtra.MotionMode.VOID) {
                            if (rotateWhenVoid.get()) {
                                shouldControlRotation = true;
                            }
                        }
                        controlMotion = velocity;
                        if (packetMotion) {
                            shouldControl = true;
                        }
                    }
                    default -> {
                        controlMotion = new Vec3d(0, 0, 0);
                        motionRotation = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
                    }
                }
                if (controlMotion.lengthSquared() < 1E-6 && autoFly.get()) {
                    controlMotion = mc.player.getRotationVector();
                }
                Vec3d wayVector = controlMotion.normalize();

                Vec3d realVector0 = wayVector.multiply(motionAmount);
                // add custom elytra event for bot to control elytra
                ElytraMotion motion = new ElytraMotion(
                        ElytraExtra.INSTANCE.autoRescale.get() ? algorithm : null,
                        motionRotation,
                        realVector0.length(),
                        motionAmount);
                Listener.getCustomListener().broadcast(new EventContainer<>(ElytraMotion.class, motion));

                // add FloatingUtils
                if (FloatingUtils.INSTANCE.workGrimFloatingThisTick()) {
                    motion.speed(0);
                    shouldControl = true;
                    shouldCheckRocket = false;
                    shouldControlRotation = false;
                } else if (useFloatingUtils.get() && motion.speed() < 1E-4) {
                    if (!MovTasks.getElytraExtra().canFireworkControlMotion()) {
                        if (ElytraExtra.INSTANCE.shouldApplyOnGroundFly()
                                && PlayerStateManager.INSTANCE.lastHasGroundSupport) {
                            shouldControl = true;
                            shouldCheckRocket = false;
                        } else {
                            motion.speed(0);
                            FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                            shouldControl = true;
                            shouldCheckRocket = false;
                            shouldControlRotation = false;
                        }
                    } else {
                        shouldControl = true;
                        shouldCheckRocket = false;
                    }
                }
                // ground check
                if (ElytraExtra.INSTANCE.shouldApplyOnGroundFly()
                        && PlayerStateManager.INSTANCE.lastHasGroundSupport
                        && motion.pitch() > 0) {
                    motion.pitch(0);
                }
                if (useAutoRescale.get()) {
                    float yaw = motion.yaw();
                    yaw = (Tasks.getTick() % 2 == 0) ? yaw + 0.001F : yaw - 0.001F;
                    motion.yaw(yaw);
                }
                if (shouldControlRotation && motion.speed() > 1e-3) {
                    // fliter zero control
                    boolean hasYaw = useAutoRescale.get()
                            || Math.abs(EntityUtils.getSafeYawDiff(motion.yaw(), mc.player.getYaw())) > 1E-4;
                    movementManagerEvent.context.pushImportantRotation(true, hasYaw);
                    movementManagerEvent.context.markForResetRot();
                    EntityUtils.setEntityPitchSafe(mc.player, motion.pitch());
                    PlayerStateManager.setPlayerYawSafe(mc.player, motion.yaw());
                    // pitch reset to trigger grim lastPitch lastYaw update
                }
                if (shouldControl) {
                    if (Math.abs(motion.pitch()) <= 1e-1 && horizontalFlyNoGravity.get() && !mc.player.isOnGround()) {
                        modifyNoGravity = player.hasNoGravity();
                        player.setNoGravity(true);
                    }
                    mc.player.setVelocity(
                            (motionMode.get() == ElytraExtra.MotionMode.FIRE_WORKS && useAutoRescale.get())
                                    ? ElytraExtra.INSTANCE.applyAxisLimit(
                                            motion.algorithm(),
                                            motion.movement(),
                                            motion.pitch(),
                                            motion.yaw(),
                                            !mc.player.hasNoGravity())
                                    : motion.movement());
                }
                if (shouldControl || shouldControlRotation) {
                    controllingThisTick = true;
                }
                if (shouldCheckRocket) {
                    MovTasks.getElytraExtra().launchFirework(mc.player.getPitch(), mc.player.getYaw());
                }
            } else {
                if (autoFly.get() && autoFlyAutoJumpOff.get()) {
                    ElytraExtra.INSTANCE.autoTakeoff();
                }
            }
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

    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        LegalMovementManager.MovementModifier.super.applyAfterInputTick(movementManagerEvent);
        if (controllingThisTick) {
            // remove sneak because sneak is part of the WASD control
            PlayerInputUtils.of(mc.player).sneak(false).applyInput(mc.player);
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
        if (presetEvent.context().getValue().isOptionPreset()) return;
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
}
