package me.matl114.hacks.modules.move;

import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.utils.move.FlightVelocity;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public class ElytraFlight extends BaseModule implements LegalMovementManager.MovementModifier {
    public final ModulePath elytra = makePath(Configs.MOV_CONFIG, "elytra");
    public final ModulePath simpleFlightControl = elytra.add("simple-flight-control");
    public final ModulePath customFireworks = elytra.add("custom-fireworks");

    public FlagRef enable =
            flagBuilder(simpleFlightControl.add("enable-control")).build();

    public KeyBindRef hotkey = moduleEntry(
                    simpleFlightControl.add("enable-control-hotkey"),
                    new MultiKeyBind(),
                    simpleFlightControl.add("enable-control"))
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

    public final FlagRef motionAdjust = builder(simpleFlightControl.add("motion-adjust"), FlagRef.TYPE)
            .defaultValue(true)
            .build();

    public final FlagRef useFloatingUtils =
            flagBuilder(simpleFlightControl.add("use-floating-utils")).build();

    private static LegalMovementManager.DelegateMovementModifier instance;

    public ElytraFlight() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enable);
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

    @Override
    public boolean mayModifyRotation() {
        if (enable.get() && motionMode.get() == ElytraExtra.MotionMode.FIRE_WORKS) {
            switch (controlMode.get()) {
                case CONTROL:
                    return true;
                case ROTATION:
                    return false;
                case SIMPLE:
                    return ((mc.options.forwardKey.isPressed() != mc.options.backKey.isPressed()))
                            || ((mc.options.jumpKey.isPressed() != mc.options.sneakKey.isPressed()));
            }
        }
        return false;
    }

    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        //
        //    }
        //
        //    @Override
        //    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
        if (player.isFallFlying() && enable.get()) {
            Vec3d controlMotion = new Vec3d(0, 0, 0);
            boolean shouldControl = false;
            double motionAmount = this.packetMotion.get();
            boolean shouldCheckRocket = false;
            PlayerInputUtils.Input input = PlayerInputUtils.of(mc.options);
            switch (controlMode.get()) {
                case CONTROL -> {
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
                        if (MovTasks.getElytraExtra().canFireworkControlMotion() && motionAdjust.get()) {
                            packetMotion = true;
                        }
                    }
                    controlMotion = velocity;
                    if (packetMotion) {
                        shouldControl = true;
                    }
                }
                case SIMPLE -> {
                    // if (simpleControlM.get()) {
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
                        controlMotion = controlMotion.add(
                                mc.player.getRotationVector().normalize().multiply(forward ? 1 : -1));
                        if (packetMotion) {
                            shouldControl = true;
                        }
                    }
                    // }
                    //  if (simpleControlH.get()) {
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
                        controlMotion = controlMotion.add(0, upward ? 1 : -1, 0);
                        if (packetMotion) {
                            shouldControl = true;
                        }
                    }
                    // }
                }
            }
            Vec3d wayVector = controlMotion.normalize();
            Vec3d realVector = wayVector.multiply(motionAmount);
            // add custom elytra event for bot to control elytra
            boolean fakeGlideNoFall = MovTasks.getElytraExtra().shouldExcuteAntiKick();
            if (fakeGlideNoFall) {
                realVector = MovTasks.getFlight().processAntiKickMotion(realVector, true);
            }
            FlightVelocity velocity = new FlightVelocity(realVector, motionAmount, FlightVelocity.Mode.ELYTRA_FLIGHT);
            Listener.getCustomListener().broadcast(new EventContainer<>(FlightVelocity.class, velocity));
            realVector = velocity.toVelocity();

            // add FloatingUtils
            if (FloatingUtils.INSTANCE.workGrimFloatingThisTick()) {
                realVector = Vec3d.ZERO;
                shouldControl = true;
                shouldCheckRocket = false;
            } else if (useFloatingUtils.get() && realVector.lengthSquared() < 1e-4) {
                if (!MovTasks.getElytraExtra().canFireworkControlMotion()) {
                    realVector = Vec3d.ZERO;
                    FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                }
                shouldControl = true;
                shouldCheckRocket = false;
            }
            if (shouldControl) {
                mc.player.setVelocity(realVector);
                if (Math.abs(realVector.y) <= 1e-2) {
                    modifyNoGravity = player.hasNoGravity();
                    player.setNoGravity(true);
                }
                if (motionMode.get() == ElytraExtra.MotionMode.FIRE_WORKS) {
                    // fliter zero control
                    if (realVector.lengthSquared() > 5e-3 && !movementManagerEvent.context.hasImportantRotation()) {
                        movementManagerEvent.context.pushImportantRotation(true, true);
                        Vec2f py = EntityUtils.rotationToPitchYaw(realVector.normalize());
                        movementManagerEvent.context.markForResetRot();
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

    // boolean controllingTick = false;
    Boolean modifyNoGravity = null;

    @Override
    public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
    }

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
            case AC_GRIM, AC_GRIM_LEGACY, AC_MATRIX -> {
                motionMode.set(ElytraExtra.MotionMode.FIRE_WORKS);
                if (packetMotion.get() > 1.7F) {
                    packetMotion.set(1.7F);
                }
            }
        }
    }

    public enum Mode implements ConfigEnum {
        CONTROL,
        ROTATION,
        SIMPLE;

        @Override
        public String getConfigEnumType() {
            return "elytramode";
        }
    }
}
