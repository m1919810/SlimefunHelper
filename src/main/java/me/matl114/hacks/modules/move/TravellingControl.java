package me.matl114.hacks.modules.move;

import java.util.Optional;
import java.util.function.Consumer;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.EventContainer;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.OptionalPrimitive;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.hacks.utils.move.ElytraOptimizeUtils;
import me.matl114.hacks.utils.move.FlightVelocity;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.command.MainCommand;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.algorithms.StateMachine;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.types.ExecutePos;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.EnterReconfigurationS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public class TravellingControl extends BaseModule implements LegalMovementManager.MovementModifier {
    public final ModulePath travellingControl = makePath(Configs.MOV_CONFIG, "travelling-control");
    static LegalMovementManager.DelegateMovementModifier instance;

    public TravellingControl() {
        super("Travel");
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    //    public FlagRef enable = flagBuilder(travellingControl.add("enable")).build();
    private TravelDelegate travelDelegate = (v) -> true;
    public KeyBindRef hotkey = hotkey(travellingControl.add("toggle-auto-speed"))
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::toggleTravelAuto))
            .build();

    public EnumRef<Type> controlType = builder(travellingControl.add("control-type"), Type.class)
            .defaultValue(Type.MOV_VOID)
            .updateListener((tt) -> {
                if (this.travelDelegate == null || this.travelDelegate.getType() != tt) {
                    if (this.travelDelegate != null) {
                        this.travelDelegate.onStop();
                    }
                    this.travelDelegate = updateMode(tt);
                }
            })
            .build();

    public DoubleRef speed = builder(travellingControl.add("speed"), DoubleRef.TYPE)
            .show(() -> controlType.get().isIn(Type.MOV_VOID, Type.MOV_VOID_2))
            .defaultValue(9.9D)
            .build();


    public IntRef minHeight = builder(travellingControl.add("min-height"), IntRef.TYPE)
            .defaultValue(256)
            .build();

    public IntRef maxHeight = builder(travellingControl.add("max-height"), IntRef.TYPE)
            .defaultValue(400)
            .build();

    public IntRef maxFireworkHeight = builder(travellingControl.add("max-firework-height"), IntRef.TYPE)
            .defaultValue(400)
            .show(() -> controlType.get().isIn(Type.ELYTRA_FIREWORK_GLIDE))
            .build();

    public FlagRef useFireworksBoostAngle = flagBuilder(travellingControl.add("use-fireworks-boost-angle"))
            .show(() -> controlType.get().isIn(Type.ELYTRA_FIREWORK_GLIDE))
            .build();

    public IntRef glidePitch = builder(travellingControl.add("glide-pitch"), IntRef.TYPE)
            .defaultValue(15)
            .validator(Configs.INT_POSITIVE)
            .show(() -> controlType.get().isIn(Type.ELYTRA_FIREWORK_GLIDE))
            .build();

    public FlagRef autoExitWithoutFireworks = flagBuilder(travellingControl.add("auto-exit-without-fireworks"))
            .defaultValue(true)
            .show(() -> controlType.get().isIn(Type.ELYTRA_FIREWORK_GLIDE))
            .build();

    public FlagRef replenishWithoutFireworks = flagBuilder(travellingControl.add("replenish-without-fireworks"))
            .show(() -> controlType.get().isIn(Type.ELYTRA_FIREWORK_GLIDE))
            .build();

    public IntRef void2Arg = builder(travellingControl.add("void-2-dup-packet"), IntRef.TYPE)
            .defaultValue(4)
            .validator(Configs.INT_POSITIVE)
            .show(() -> controlType.get().isIn(Type.MOV_VOID_2))
            .build();

    public FlagRef lowHeightExit = flagBuilder(travellingControl.add("low-height-exit"))
            .defaultValue(true)
            .show(() -> controlType
                    .get()
                    .isIn(
                            Type.ELYTRA_PITCH40, Type.ELYTRA_PITCH40_OPTIMIZED,
                            Type.ELYTRA_FIREWORK_GLIDE, Type.ELYTRA_GRIM_FLY40))
            .build();

    public FlagRef safeKick = flagBuilder(travellingControl.add("flight-enter-reconfiguration-kick"))
            .show(() -> controlType
                    .get()
                    .isIn(
                            Type.ELYTRA_PITCH40, Type.ELYTRA_PITCH40_OPTIMIZED,
                            Type.ELYTRA_FIREWORK_GLIDE, Type.ELYTRA_GRIM_FLY40))
            .build();

    public FlagRef pitch40SafeEnd = flagBuilder(travellingControl.add("pitch-40-end-safety-2"))
            .show(() -> controlType.get().isIn(Type.ELYTRA_PITCH40, Type.ELYTRA_GRIM_FLY40))
            .build();

    public IntRef pitch40Pitch = builder(travellingControl.add("pitch-40-pitch-positive"), IntRef.TYPE)
            .defaultValue(15)
            .validator(Configs.INT_POSITIVE)
            .show(() -> controlType.get().isIn(Type.ELYTRA_PITCH40))
            .build();
    public IntRef pitch40Negative = builder(travellingControl.add("pitch-40-pitch-negative"), IntRef.TYPE)
            .defaultValue(60)
            .validator(Configs.INT_POSITIVE)
            .show(() -> controlType.get().isIn(Type.ELYTRA_PITCH40))
            .build();

    public DoubleRef negativeArgument = builder(travellingControl.add("pitch-40-negative-delta"), DoubleRef.TYPE)
            .defaultValue(0.0)
            .validator(Configs.doubleRange(0, 90))
            .show(() -> controlType.get().isIn(Type.ELYTRA_PITCH40))
            .build();

    public IntRef pitch40GrimPitch = builder(travellingControl.add("pitch-40-grim-pitch-positive"), IntRef.TYPE)
            .defaultValue(15)
            .validator(Configs.INT_POSITIVE)
            .show(() -> controlType.get().isIn(Type.ELYTRA_GRIM_FLY40))
            .build();
    public IntRef pitch40GrimNegative = builder(travellingControl.add("pitch-40-grim-pitch-negative"), IntRef.TYPE)
            .defaultValue(60)
            .validator(Configs.INT_POSITIVE)
            .show(() -> controlType.get().isIn(Type.ELYTRA_GRIM_FLY40))
            .build();

    public DoubleRef negativeArgumentGrim = builder(
                    travellingControl.add("pitch-40-negative-delta-grim"), DoubleRef.TYPE)
            .defaultValue(0.0)
            .validator(Configs.doubleRange(0, 90))
            .show(() -> controlType.get().isIn(Type.ELYTRA_GRIM_FLY40))
            .build();

    public final FlagRef limitSpeedForDangerousSpeed = flagBuilder(
                    travellingControl.add("limit-speed-for-dangerous-speed"))
            .show(() -> controlType.get().isIn(Type.ELYTRA_GRIM_FLY40))
            .build();

    public NBTRef<OptionalPrimitive<Double>> pitch40HeightLimit = builder(
                    travellingControl.add("pitch-40-height-limit"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 900.0D))
            .show(() -> controlType.get().isIn(Type.ELYTRA_PITCH40, Type.ELYTRA_GRIM_FLY40))
            .build();

    public FlagRef clearTargetWhenExit =
            flagBuilder(travellingControl.add("clear-target-when-exit")).build();

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        if (travelTask != null
                && !travelTask.shouldNotRun()
                && travelDelegate instanceof LegalMovementManager.MovementModifier movementModifier) {
            movementModifier.applyPreTickModify(movementManagerEvent);
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (travelTask != null
                && !travelTask.shouldNotRun()
                && travelDelegate instanceof LegalMovementManager.MovementModifier movementModifier) {
            movementModifier.postModify(movementManagerEvent, enabledThisTick);
        }
        return true;
    }

    public static enum Type implements ConfigEnum {
        ELYTRASKY, // 原 ELYTRA
        ELYTRA_PITCH40,
        ELYTRA_PITCH40_OPTIMIZED,
        ELYTRA_GRIM_FLY40,
        ELYTRA_FIREWORK_GLIDE,
        MOV_VOID,
        MOV_VOID_2,
        PEARL,
        TEST;

        @Override
        public String getConfigEnumType() {
            return "travel_control_type";
        }
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerCommandBootstrap(this::bootStrapTravelCommand);
        registerListener(Listener.getCustomListener().getChannel(FlightVelocity.class), this::onElytraVelocity);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(EnterReconfigurationS2CPacket.class),
                this::onReconfiguration);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(PlayerPositionLookS2CPacket.class),
                this::onPlayerPositionLook);
        registerListener(Listener.getPreTick(), this::onTick);
    }

    private TravelDelegate updateMode(Type type) {
        return switch (type) {
            case ELYTRASKY -> new TravelMoveVelocity();
            case ELYTRA_PITCH40 -> new TravelPitch40(this);
            case ELYTRA_PITCH40_OPTIMIZED -> new TravelPitch40Optimized(this);
            case ELYTRA_FIREWORK_GLIDE -> new TravelElytraFireworkGlide(this);
            case ELYTRA_GRIM_FLY40 -> new TravelPitch40Grim(this);
            case MOV_VOID -> new TravelMoveVoid();
            case MOV_VOID_2 -> new TravelMoveVoid2();
            default -> (eve) -> {
                return true;
            };
        };
    }

    private void onPlayerPositionLook(Event<PlayerPositionLookS2CPacket> eventPosition) {
        if (travelDelegate instanceof TravelMoveVoid2 void2) {
            void2.onPlayerPositionLook(eventPosition);
        }
    }

    boolean currentReconfiguration = false;

    private void onReconfiguration(Event<EnterReconfigurationS2CPacket> eventKick) {
        currentReconfiguration = true;
    }

    private void onTick(Event<Void> event) {
        if (currentReconfiguration && mc.player != null) {
            currentReconfiguration = false;
        }
        if (travelTask != null) {
            if (checkState(travelTask)) {
                return;
            }
            if (mc.player != null && travelDelegate != null) {
                var info = travelTask;
                if (info.pause) {
                    logI18NSub("Travel", "message.module.travelling-control.reloading-last-task");
                    info.onStart(this, mc.player.getPos());
                    travelDelegate.onStop();
                    travelDelegate.onStart(info);
                    logI18NSub("Travel", "message.module.travelling-control.reloading-last-task.success");
                }
                if (info.shouldNotRun()) return;
                if (travelDelegate.onTick(event)) {
                    onStop(info);
                }
            }
        }
    }

    public void bootStrapTravelCommand(MainCommand mainCommand) {
        TreeSubCommand main = mainCommand.mainBuilder().name("travel").build();
        {
            main.subBuilder(SubCommand.treeBuilder())
                    .name("travel")
                    .post(s -> s.subBuilder(SubCommand.taskBuilder())
                            .name("to")
                            .helper("message.command.travel.travel.to.help")
                            .arg(SimpleCommandArgs.argumentBuilder(MovTasks.TpaAndPosArgumentType::new)
                                    .name("target")
                                    .build())
                            .post(e -> e.executor(this::onTravelTo))
                            .complete()
                            .subBuilder(SubCommand.taskBuilder())
                            .name("auto")
                            .helper("message.command.travel.travel.auto.help")
                            .post(e -> e.executor(CommandContext.execute(this::onTravelAuto)))
                            .complete()
                            .subBuilder(SubCommand.taskBuilder())
                            .name("cancel")
                            .helper("message.command.travel.travel.cancel.help")
                            .post(e -> e.executor(CommandContext.run(this::onTravelCancel)))
                            .complete())
                    .complete();
        }
    }

    private void checkSelf() {
        if (travelTask != null && travelTask.instance != this) {
            travelTask.stop = true;
            travelTask = null;
        }
    }

    public boolean onTravelTo(CommandExecution var1, ArgumentInputStream streamArgs, ArgumentReader argsReader) {
        ExecutePos pos = streamArgs.nextArg();
        if (pos != null) {
            Vector3d vector3d = pos.getPosition(var1);
            onTravel(var1.getExecutor(), new Vec3d(vector3d.x, vector3d.y, vector3d.z));
        }
        return true;
    }

    public void onTravel(PlayerEntity var1, Vec3d parsedCoord) {
        checkSelf();
        if (travelTask != null) {
            logI18NSub("Travel", "message.module.travelling-control.start-new-task.cancel-current");
            onTravelCancel();
        }
        if (parsedCoord == null) return;
        travelMode(Optional.of(parsedCoord));
        if (travelTask == null) {

        } else {
        }
    }

    public void toggleTravelAuto() {
        if (checkNull()) return;
        checkSelf();
        if (travelTask == null) {
            logI18NSub("Travel", "message.module.travelling-control.start-new-task.start-auto");
            travelMode(Optional.empty());
        } else {
            logI18NSub("Travel", "message.module.travelling-control.start-new-task.cancel-current");
            onTravelCancel();
        }
    }

    public void onTravelAuto(CommandExecution var1) {
        checkSelf();
        if (travelTask == null) {
            travelMode(Optional.empty());
        } else {
            logI18NSub("Travel", "message.module.travelling-control.start-new-task.cancel-current");
        }
    }

    public void travelMode(Optional<Vec3d> traget) {
        Type type = controlType.get();
        logI18NSub(
                "Travel",
                "message.module.travelling-control.start-new-task.mode",
                type.getDisplay().getString());
        TravelInfo info = new TravelInfo();
        info.pos0 = traget;

        info.onStart(this, mc.player.getPos());

        travelTask = info;
        double initY = mc.player.getY();
        if (initY < minHeight.get()) {
            info.state = TravelState.TOO_LOW;
        } else if (initY > maxHeight.get()) {
            info.state = TravelState.TOO_HIGH;
        } else {
            info.state = TravelState.STABLE;
        }
        if (travelDelegate != null) {
            travelDelegate.onStart(info);
        } else {
            travelTask.stop = true;
            travelTask = null;
        }
    }

    private void outputTravelStats(TravelInfo ti) {
        if (ti == null || ti.startingTime == 0) return;
        logI18NSub("Travel", "message.module.travelling-control.finish-task");
        long usedSec = (System.currentTimeMillis() - ti.startingTime) / 1000L;
        if (mc.player != null) {
            double len = mc.player.getPos().distanceTo(ti.startPos);
            double avgSpeed = usedSec > 0 ? len / usedSec : 0;
            logI18NSub(
                    "Travel",
                    "message.module.travelling-control.finish-task.info",
                    String.valueOf(usedSec),
                    String.format("%.2f", len),
                    String.format("%.2f", avgSpeed));
            mc.player.setOnGround(false);
        }
    }

    private void onStop(TravelInfo info) {
        if (info != null) info.stop = true;
        travelTask = null;
        travelDelegate.onStop();
    }

    private void onPause(TravelInfo info) {
        if (info == null) {
            onStop(info);
        } else {
            info.pause = true;
            travelDelegate.onStop();
        }
    }

    private boolean checkState(TravelInfo ti) {
        if (ti != null && ti.pause) {
            return false;
        } else if (checkNull()) {
            if (clearTargetWhenExit.get()) {
                onStop(ti);
                return true;
            } else {
                onPause(ti);
                return true;
            }
        } else if (ti == null || ti.stop || ti.instance != this) {
            onStop(ti);
            return true;
        }
        return false;
    }

    private class TravelMoveVoid implements TravelDelegate {
        int delay = 0;
        int tickCNT;

        @Override
        public Type getType() {
            return Type.MOV_VOID;
        }

        @Override
        public boolean onTick(Event<Void> event) {
            MovTasks.doingTp = false;
            TravelInfo ti = travelTask;
            mc.player.setOnGround(false);
            if (++delay < 2) {
                return false;
            }
            delay = 0;
            tickCNT += 1;

            double currentY = mc.player.getY();
            updateState(ti, currentY);

            double horizontalSpeed = speed.get();

            if (ti.state == TravelState.STABLE) {
                Vec3d towards = ti.getCurrentFlyingTarget().subtract(mc.player.getPos());
                Vec3d towardsHorizontal = new Vec3d(towards.x, 0, towards.z).normalize();

                if (moveAndCheckFinish(
                        ti, towardsHorizontal.multiply(horizontalSpeed).add(0, -0.05, 0))) {
                    return true;
                }
                if (moveAndCheckFinish(
                        ti, towardsHorizontal.multiply(horizontalSpeed).add(0, -0.05, 0))) {
                    return true;
                }
                if (tickCNT % 3 == 0) {
                    if (moveAndCheckFinish(
                            ti, towardsHorizontal.multiply(horizontalSpeed).add(0, -0.05, 0))) {
                        return true;
                    }
                }
            } else {
                double targetY = ti.state == TravelState.TOO_LOW ? maxHeight.get() : minHeight.get();
                double deltaY;
                if ((tickCNT % 20) < 18) {
                    double direction = Math.signum(targetY - currentY);
                    deltaY = direction * speed.get();
                } else {
                    deltaY = -0.3;
                }

                Vec3d delta = new Vec3d(0, deltaY, 0);
                if (moveAndCheckFinish(ti, delta)) {
                    return true;
                }
            }

            MovTasks.doingTp = true;
            return false;
        }

        @Override
        public void onStop() {
            MovTasks.doingTp = false;
        }
    }

    private class TravelMoveVoid2 implements TravelDelegate {
        boolean catchResyncPackets = false;
        TravelInfo info;
        int tickCNT = 0;

        @Override
        public Type getType() {
            return Type.MOV_VOID_2;
        }

        @Override
        public void onStart(TravelInfo state) {
            this.info = state;
        }

        public void onPlayerPositionLook(Event<PlayerPositionLookS2CPacket> event) {
            catchResyncPackets = true;
        }

        int delay = 0;

        @Override
        public boolean onTick(Event<Void> event) {
            MovTasks.doingTp = false;
            TravelInfo ti = travelTask;

            mc.player.setOnGround(false);
            if (++delay < 2) {
                return false;
            }
            delay = 0;
            tickCNT += 1;

            double currentY = mc.player.getY();
            updateState(ti, currentY);

            double horizontalSpeed = speed.get() - 0.05;

            if (ti.state == TravelState.STABLE) {
                if (checkFinish(ti)) {
                    return true;
                }
                Vec3d towards = ti.getCurrentFlyingTarget().subtract(mc.player.getPos());
                towards = new Vec3d(towards.x, 0, towards.z);
                double len = towards.horizontalLengthSquared();
                Vec3d towardsHorizontal = towards.normalize();
                Vec3d delta = towardsHorizontal
                        .multiply(horizontalSpeed)
                        .add(0, -0.05, 0)
                        .multiply(void2Arg.get());
                if (delta.horizontalLengthSquared() > len) {
                    delta = towards;
                }
                Vec3d targetPos = mc.player.getPos().add(delta);
                if (catchResyncPackets) {
                    catchResyncPackets = false;
                } else {
                    MovTasks.executeTp(targetPos, 200, false, false);
                    MovTasks.setupAutoResync(targetPos);
                }

            } else {
                double targetY = ti.state == TravelState.TOO_LOW ? maxHeight.get() : minHeight.get();
                double deltaY;
                if ((tickCNT % 20) < 18) {
                    double direction = Math.signum(targetY - currentY);
                    deltaY = direction * speed.get();
                } else {
                    deltaY = -0.3;
                }

                Vec3d delta = new Vec3d(0, deltaY, 0);
                if (moveAndCheckFinish(ti, delta)) {
                    return true;
                }
            }

            MovTasks.doingTp = true;
            return false;
        }

        @Override
        public void onStop() {
            MovTasks.doingTp = false;
        }
    }

    private class TravelMoveVelocity implements TravelDelegate {
        private Vec3d elytraRotation = null;
        private boolean pullingUp;
        int tickCNT = 0;

        @Override
        public Type getType() {
            return Type.ELYTRASKY;
        }

        @Override
        public void onStart(TravelInfo state) {
            elytraRotation = null;
            pullingUp = false;
            tickCNT = 0;
        }

        @Override
        public void onStop() {
            elytraRotation = null;
        }

        @Override
        public boolean onTick(Event<Void> event) {
            TravelInfo ti = travelTask;
            mc.player.setOnGround(false);
            tickCNT += 1;

            double currentY = mc.player.getY();
            // 初始化持久化状态（如果为null）
            if (ti.state == null) {
                if (currentY < minHeight.get()) {
                    ti.state = TravelState.TOO_LOW;
                } else if (currentY > maxHeight.get()) {
                    ti.state = TravelState.TOO_HIGH;
                } else {
                    ti.state = TravelState.STABLE;
                }
            }

            // 状态更新逻辑（与 MOV_VOID 相同）
            updateState(ti, currentY);

            if (ti.state == TravelState.STABLE && checkFinish(ti)) {
                return true;
            }

            Vec3d towards = ti.getCurrentFlyingTarget().subtract(mc.player.getPos());
            Vec3d horizontalTowards = new Vec3d(towards.x, 0, towards.z);
            float yaw = horizontalTowards.lengthSquared() > 1E-6
                    ? EntityUtils.rotationToYaw(horizontalTowards)
                    : mc.player.getYaw();
            pullingUp = ti.state == TravelState.TOO_LOW;
            elytraRotation = EntityUtils.pitchYawToRotation(pullingUp ? -45 : 10, yaw);

            return false;
        }

        public void onElytra(Event<EventContainer<FlightVelocity>> event) {
            if (elytraRotation == null) {
                return;
            }
            EventContainer<FlightVelocity> eventContainer = event.context();
            if (eventContainer.getValue().mode() != FlightVelocity.Mode.ELYTRA_FLIGHT) return;
            FlightVelocity velocity = eventContainer.getValue();
            Vec3d rotation = elytraRotation;
            if (MovTasks.getElytraFlight().useAutoRescale.get() && ElytraExtra.INSTANCE.autoRescale.get()) {
                rotation = pullingUp
                        ? ElytraOptimizeUtils.calculateBestPullupSpeed(rotation)
                        : ElytraOptimizeUtils.calculateBestDownForwardSpeed(rotation, false);
            }
            velocity.velocity(rotation.normalize().multiply(velocity.maxVelocity()));
        }
    }

    private abstract static class AbstractElytraGlideProgress
            implements TravelDelegate, LegalMovementManager.MovementModifier {
        final TravellingControl control;
        TravelInfo ti;
        int dangerousNoFallFlyingTick;

        AbstractElytraGlideProgress(TravellingControl control) {
            this.control = control;
        }

        @Override
        public void onStart(TravelInfo state) {
            ti = state;
            dangerousNoFallFlyingTick = 0;
        }

        protected void maintainFlight() {
            if (mc.player.isFallFlying()) {
                dangerousNoFallFlyingTick = 0;
            } else {
                ElytraExtra.INSTANCE.autoTakeoff();
                handleReFly();
            }
        }

        protected void handleReFly() {
            if (dangerousNoFallFlyingTick > 20) {
                dangerousNoFallFlyingTick = 0;
            }
            if (dangerousNoFallFlyingTick == 0) {
                MovExtra.INSTANCE.sendPacketsForInventoryAction();
                if (mc.player.checkGliding()) {
                    MovExtra.INSTANCE.sendPacketsForPreStartFallFlying();
                    mc.getNetworkHandler()
                            .sendPacket(new ClientCommandC2SPacket(
                                    mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    MovExtra.INSTANCE.sendPacketsForPostStartFallFlying();
                }
                dangerousNoFallFlyingTick = 1;
            }
        }

        protected boolean handleLowHeightExit() {
            if (control.lowHeightExit.get()
                    && shouldCheckLowHeightExit()
                    && !isLanding()
                    && mc.player.getY() < control.minHeight.get() - 32) {
                control.logI18NSub("Pitch40", "message.module.travelling-control.out-of-control");
                MainTasks.scheduleDisconnect();
                control.onStop(ti);
                return false;
            }
            if (!mc.player.isFallFlying()) {
                if (dangerousNoFallFlyingTick > 0) {
                    dangerousNoFallFlyingTick++;
                }
            } else {
                dangerousNoFallFlyingTick = 0;
            }
            return true;
        }

        protected boolean isLanding() {
            return false;
        }

        protected boolean shouldCheckLowHeightExit() {
            return false;
        }

        @Override
        public void onStop() {
            ti = null;
        }

        @Override
        public int priority() {
            return PRIORITY_LOW;
        }

        @Override
        public boolean onTick(Event<Void> event) {
            if (mc.player != null && travelTask == null) {
                return true;
            }
            if (control.currentReconfiguration) {
                if (control.safeKick.get()) {
                    Tasks.scheduleRepeated(
                            () -> {
                                if (mc.player != null) {
                                    MainTasks.scheduleDisconnect();
                                    return true;
                                } else {
                                    return false;
                                }
                            },
                            1,
                            1);
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
            if (ti == null || ti.shouldNotRun()) return true;
            if (!handleLowHeightExit()) return true;
            handleFinishCheck();
            return true;
        }

        protected abstract void handleFinishCheck();
    }

    private abstract static class AbstractPitch40 extends AbstractElytraGlideProgress {
        public AbstractPitch40(TravellingControl control) {
            super(control);
            this.machine = new StateMachine(
                    0,
                    this::onStateUpdate,
                    this::onStateNone,
                    this::onStatePullup,
                    this::onStateGlide,
                    this::onStateEmergency);
        }

        boolean startWork = false;
        int counter = 0;
        int counter2 = 0;
        int lastFireworkAttemptTick = Integer.MIN_VALUE;
        double[] last3Y = {-999, -999, -999, -999, -999};
        int last3YIndex = 0;
        static final int STATE_NONE = 0;
        static final int STATE_PULL_UP = 1;
        static final int STATE_GLIDE = 2;
        static final int STATE_EMERGENCY = 3;
        StateMachine machine;

        @Override
        protected boolean shouldCheckLowHeightExit() {
            return startWork;
        }

        public int onStateUpdate(StateMachine machine, int state) {
            counter2 += 1;
            Vec3d currentPos = mc.player.getPos();
            Vec3d towards = ti.getCurrentFlyingTarget().subtract(currentPos);
            // anti afk

            float yaw = EntityUtils.rotationToPitchYaw(towards.normalize()).y;
            PlayerStateManager.setPlayerYawSafe(mc.player, yaw);

            if (mc.player.getY() < control.minHeight.get() - 16) {
                return STATE_EMERGENCY;
            }
            if (mc.player.getY() < control.minHeight.get()) {
                return STATE_PULL_UP;
            }
            return state;
        }

        public int onStateNone(StateMachine machine) {
            return STATE_GLIDE;
        }

        public abstract int onStatePullup(StateMachine machine);

        public abstract int onStateGlide(StateMachine machine);

        @Override
        public void onStart(TravelInfo state) {
            super.onStart(state);
            this.ti.state = TravelState.TOO_LOW;
            startWork = false;
            counter = 0;
            counter2 = 0;
            lastFireworkAttemptTick = Integer.MIN_VALUE;
            last3Y = new double[] {-999, -999, -999, -999, -999, -999, -999, -999, -999, -999};
            last3YIndex = 0;
        }

        protected void checkNotStart() {
            if (!startWork) {
                if (mc.player.isFallFlying()) {
                    if (ti.state == TravelState.TOO_HIGH
                            && PlayerStateManager.INSTANCE.lastKnownRealMovementSpeed.y < 0) {
                        startWork = true;
                        control.logI18NSub(
                                "Pitch40",
                                "message.module.travelling-control.start-working",
                                String.valueOf(control.maxHeight.get()));
                    } else {
                        handlePullUp();
                        if (ti.state != TravelState.TOO_HIGH && ++counter % 60 == 0) {
                            control.logI18NSub(
                                    "Pitch40",
                                    "message.module.travelling-control.pull-up-to-max-height",
                                    String.valueOf(control.maxHeight.get()));
                        }
                    }
                } else {
                    maintainFlight();
                }
            }
        }

        public int onStateEmergency(StateMachine machine) {
            handlePullUp();
            if (mc.player.getY() > control.maxHeight.get() && !ElytraExtra.INSTANCE.canFireworkControlMotion()) {
                machine.markForEndState();
                return STATE_PULL_UP;
            }
            if (ElytraExtra.INSTANCE.canFireworkControlMotion()) {
                EntityUtils.setEntityPitchSafe(mc.player, -45);
            }
            machine.markForEndState();
            return STATE_EMERGENCY;
        }

        protected void handlePullUp() {
            EntityUtils.setEntityPitchSafe(mc.player, -45);
            // fire only when down wards, make full use of travel
            if (PlayerStateManager.INSTANCE.lastKnownRealMovementSpeed.y < 0) {
                if (!ElytraExtra.INSTANCE.canFireworkControlMotion()
                        && Tasks.getTick() % 5 == 0
                        && !ElytraExtra.INSTANCE.isCurrentWaitingFireworkLaunch()) {
                    ElytraExtra.INSTANCE.sendCustomUseFireworkPacket();
                } else {
                    FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                }
            }
        }

        protected void launchFireworks(float pitch, float yaw) {
            int tick = Tasks.getTick();
            if (tick - lastFireworkAttemptTick < 10) return;
            lastFireworkAttemptTick = tick;
            ElytraExtra.INSTANCE.launchFirework(pitch, yaw);
        }

        @Override
        protected void handleFinishCheck() {
            if (control.checkFinish(ti)) {
                if (!ti.stopManually && control.pitch40SafeEnd.get()) {
                    control.logI18NSub("Pitch40", "message.module.travelling-control.auto-log-when-arrive");
                    MainTasks.scheduleDisconnect();
                }
                control.onStop(ti);
            }
            if (mc.player != null) {
                last3Y[last3YIndex] = mc.player.getY();
                last3YIndex = (last3YIndex + 1) % last3Y.length;
            }
        }

        @Override
        public void onStop() {
            super.onStop();
            startWork = false;
        }
    }

    private static class TravelPitch40 extends AbstractPitch40 {

        public TravelPitch40(TravellingControl control) {
            super(control);
        }

        @Override
        public Type getType() {
            return Type.ELYTRA_PITCH40;
        }

        @Override
        public int onStateGlide(StateMachine machine) {
            counter2 = 0;
            EntityUtils.setEntityPitchSafe(mc.player, control.pitch40Pitch.get());
            machine.markForEndState();
            return STATE_GLIDE;
        }

        @Override
        public int onStatePullup(StateMachine machine) {
            double y = mc.player.getY();
            if (control.pitch40HeightLimit.get().test(s -> y > s)) {
                return STATE_GLIDE;
            }
            double last3YY = this.last3Y[last3YIndex];
            boolean goingDown = (y < last3YY);
            if (goingDown && counter2 > 10 && mc.player.getY() > control.minHeight.get()) {
                return STATE_GLIDE;
            }
            EntityUtils.setEntityPitchSafe(
                    mc.player,
                    Math.min(
                            -control.pitch40Negative.get() + counter2 * (float) control.negativeArgument.get(),
                            control.pitch40Pitch.get()));
            machine.markForEndState();
            return STATE_PULL_UP;
        }

        @Override
        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
            if (ti == null || ti.shouldNotRun()) return;
            ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
            control.updateState(ti, player.getY());
            checkNotStart();
            if (startWork) {
                if (mc.player.isFallFlying()) {
                    movementManagerEvent.context.pushImportantRotation(true, true);
                    machine.step();
                } else {
                    maintainFlight();
                }
            } else {
                machine.setState(STATE_NONE);
            }
        }
    }

    private static class TravelPitch40Optimized extends AbstractPitch40 {
        // Fastest-horizontal-speed waveform from elytra-strategy-lab.
        // Values are already converted to Minecraft pitch convention.
        private static final float[] DESCEND_PITCH = {
            90f, 90f, 90f, 90f, 90f, 60.58f, 35.51f, 35.51f, 34.63f, 32.91f, 32.68f, 34.1f,
            34.15f, 33.95f, 35.32f, 32.8f, 34.16f, 34.37f, 34.44f, 33.33f, 33.1f, 33.36f, 33.45f, 34.25f,
            34.02f, 33.52f, 34.7f, 33.45f, 35.28f, 33.51f, 34.43f, 33.6f, 34.43f, 35.32f, 34.9f, 34.85f,
            34.49f, 33.82f, 34.1f, 34.67f, 34.62f, 34.56f, 34.11f, 35.66f, 34.99f, 34.59f, 36.51f, 33.2f,
            34.74f, 35.47f, 36.95f, 34.43f, 34.52f, 34.77f, 35.46f, 35.1f, 35.05f, 35.03f, 35.65f, 34.93f,
            35.3f, 35.5f, 36f, 36.78f, 36.43f, 35.34f, 35.52f, 35.46f, 34.98f, 36.05f, 35.62f, 37.01f,
            36f, 35.43f, 36.01f, 35.68f, 36.29f, 36.33f, 37.04f, 36.04f, 37.39f, 36.89f, 35.47f, 36.55f,
            36.18f, 36.89f, 36.99f, 36.85f, 36.32f, 35.7f, 36.72f, 38.21f, 36.74f, 37.49f, 37.22f, 37.56f,
            36.8f, 37.57f, 36.92f, 37.7f, 38.11f, 37.15f, 37.42f, 37.46f, 38.23f, 36.45f, 38.22f, 37.67f,
            37.92f, 38.04f, 38.4f, 38.06f, 38.03f, 38.15f, 37.95f, 38.59f, 38.92f, 37.45f, 38.56f, 38.85f,
            38.52f, 38.49f, 38.68f, 38.81f, 39.07f, 39.09f, 38.93f, 38.69f, 39.22f, 39.19f, 39.41f, 39.23f,
            38.45f, 40.08f, 39.56f, 39.73f, 38.62f, 39.65f, 39.87f, 39.9f, 40.2f, 39.88f, 39.42f, 39.84f,
            40f, 40.21f, 40.45f, 40.98f, 40.16f, 40.33f, 40.53f, 40.58f, 39.87f, 40.78f, 40.16f, 41.1f,
            40.49f, 41.57f, 41.03f, 41.22f, 41.33f, 41.32f, 40.56f, 41.54f, 41.57f, 41.28f, 41.72f, 41.42f,
            41.98f, 41.25f, 42.18f, 42.2f, 42.27f, 41.48f, 42.35f, 41.53f, 41.68f, 42.5f, 42.68f, 42.49f,
            42.55f, 42.47f, 42.61f, 42.81f, 42.77f, 42.75f, 42.84f, 42.89f, 43.06f, 43.18f, 43.04f, 43.17f,
            43.28f, 43.42f, 43.38f, 43.78f, 43.55f, 43.76f, 43.79f, 43.72f, 43.72f, 44.06f, 44.01f, 44.21f,
            44.02f, 44.54f, 44.42f, 44.51f, 44.57f, 44.54f, 44.72f, 44.82f, 44.82f, 44.84f, 44.94f, 44.89f,
            44.83f, 44.75f, 44.63f, 44.47f, 44.21f, 43.28f, 54.28f, 49.12f, 36.59f, 90f, 0.01f, 41.59f,
            49.55f, 48.29f, 47.38f, 46.89f, 46.49f, 46.23f, 46.11f, 46.01f, 46.09f, 46.16f, 46.29f, 46.76f,
            46.89f, 47.31f, 47.72f, 48.62f, 49.51f, 50.63f, 52.01f, 0.01f, 90f, 45.96f, 42.27f, 39.4f,
            33.53f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f
        };

        private static final float[] ASCEND_PITCH = {
            -20.21f, -42.72f, -89.95f, -89.74f, -88.58f, -86.5f, -83.26f, -78.78f, -73.57f, -68.2f, -64.59f, -61.57f,
            -58.96f, -56.66f, -54.51f, -52.47f, -50.73f, -49f, -47.36f, -45.91f, -44.42f, -43.2f, -41.95f, -40.75f,
            -39.65f, -38.54f, -37.52f, -36.52f, -35.55f, -34.64f, -33.7f, -32.9f, -32.09f, -31.27f, -30.5f, -29.8f,
            -29.06f, -28.35f, -27.65f, -27.01f, -26.36f, -25.66f, -25.09f, -24.47f, -23.9f, -23.32f, -22.71f, -22.16f,
            -21.59f, -21.04f, -20.51f, -19.95f, -19.48f, -18.81f, -18.38f, -17.84f, -17.34f, -16.74f, -16.11f, -15.67f,
            -15.13f, -14.52f, -13.95f, -13.34f, -12.7f, -12f, -11.36f, -10.64f, -9.73f, -9.02f, -8.11f, -7.03f, -5.97f,
            -4.76f, -3.41f, -1.81f, -0.25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 90f, 90f, 90f, 90f, 90f
        };

        private static final int STATE_DESCEND = STATE_GLIDE;
        private static final int STATE_ASCEND = STATE_PULL_UP;
        private int descendTick;
        private int ascendTick;

        TravelPitch40Optimized(TravellingControl control) {
            super(control);
            machine.registerListener(STATE_DESCEND, isOn -> {
                if (isOn) descendTick = 0;
            });
            machine.registerListener(STATE_ASCEND, isOn -> {
                if (isOn) ascendTick = 0;
            });
        }

        @Override
        public Type getType() {
            return Type.ELYTRA_PITCH40_OPTIMIZED;
        }

        @Override
        protected boolean shouldCheckLowHeightExit() {
            return false;
        }

        @Override
        public void onStart(TravelInfo state) {
            super.onStart(state);
            descendTick = 0;
            ascendTick = 0;
        }

        @Override
        public int onStateGlide(StateMachine machine) {
            EntityUtils.setEntityPitchSafe(mc.player, DESCEND_PITCH[descendTick]);
            descendTick++;
            machine.markForEndState();
            return descendTick >= DESCEND_PITCH.length ? STATE_ASCEND : STATE_DESCEND;
        }

        @Override
        public int onStatePullup(StateMachine machine) {
            EntityUtils.setEntityPitchSafe(mc.player, ASCEND_PITCH[ascendTick]);
            launchFireworks(ASCEND_PITCH[ascendTick], mc.player.getYaw());
            ascendTick++;
            machine.markForEndState();
            return ascendTick >= ASCEND_PITCH.length ? STATE_DESCEND : STATE_ASCEND;
        }

        @Override
        public int onStateUpdate(StateMachine machine, int state) {
            counter2++;
            Vec3d towards = ti.getCurrentFlyingTarget().subtract(mc.player.getPos());
            PlayerStateManager.setPlayerYawSafe(mc.player, EntityUtils.rotationToPitchYaw(towards.normalize()).y);

            if (mc.player.getY() < control.minHeight.get() - 64) {
                return STATE_EMERGENCY;
            }
            if (mc.player.getY() < control.minHeight.get() - 16) {
                return STATE_ASCEND;
            }
            return state;
        }

        @Override
        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
            if (ti == null || ti.shouldNotRun()) return;
            control.updateState(ti, movementManagerEvent.context.playerStatus.entity.getY());
            checkNotStart();
            if (!startWork) {
                machine.setState(STATE_NONE);
                return;
            }
            if (!mc.player.isFallFlying()) {
                maintainFlight();
                return;
            }
            movementManagerEvent.context.pushImportantRotation(true, true);
            machine.step();
        }
    }

    private static class TravelElytraFireworkGlide extends AbstractElytraGlideProgress {
        private static final int STATE_NONE = 0;
        private static final int STATE_ASCEND = 1;
        private static final int STATE_DESCEND = 2;
        private static final int STATE_REPLENISH = 3;
        private boolean rocketBoostTurnedOn;
        private int lastFireworkAttemptTick = Integer.MIN_VALUE;
        private boolean forcedSlowFall;
        private boolean started;
        private boolean recovering;
        private BlockPos landing;
        private StateMachine machine;

        TravelElytraFireworkGlide(TravellingControl control) {
            super(control);
            this.machine = new StateMachine(
                    STATE_NONE,
                    this::onStateUpdate,
                    this::onStateNone,
                    this::onStatePullup,
                    this::onStateGlide,
                    this::onStateReplenish);
        }

        private int onStateNone(StateMachine machine) {
            return STATE_ASCEND;
        }

        @Override
        public Type getType() {
            return Type.ELYTRA_FIREWORK_GLIDE;
        }

        @Override
        public void onStart(TravelInfo state) {
            super.onStart(state);
            lastFireworkAttemptTick = Integer.MIN_VALUE;
            forcedSlowFall = false;
            started = false;
            recovering = false;
            landing = null;
            rocketBoostTurnedOn = false;
            if (control.useFireworksBoostAngle.get()) {
                rocketBoostTurnedOn = !ElytraExtra.INSTANCE.rocketBoost.get();
                ElytraExtra.INSTANCE.rocketBoost.set(true);
            }
        }

        @Override
        public void onStop() {
            super.onStop();
            clearForcedSlowFall();
            if (rocketBoostTurnedOn) {
                ElytraExtra.INSTANCE.rocketBoost.set(false);
                rocketBoostTurnedOn = false;
            }
        }

        @Override
        protected boolean isLanding() {
            return machine.getState() == STATE_REPLENISH;
        }

        @Override
        protected boolean shouldCheckLowHeightExit() {
            return started && machine.getState() != STATE_REPLENISH;
        }

        @Override
        protected void maintainFlight() {
            if (machine.getState() != STATE_REPLENISH) {
                super.maintainFlight();
            }
        }

        private void launchFireworks(float pitch, float yaw) {
            int tick = Tasks.getTick();
            if (tick - lastFireworkAttemptTick < 10) return;
            lastFireworkAttemptTick = tick;
            ElytraExtra.INSTANCE.launchFirework(pitch, yaw);
        }

        private int onStateReplenish(StateMachine machine) {
            if (landing == null) {
                landing = findLandingColumn();
            }
            if (landing == null) {
                enableForcedSlowFall();
                Vec3d velocity = mc.player.getVelocity();
                Vec3d horizontal = new Vec3d(velocity.x, 0, velocity.z);
                if (horizontal.lengthSquared() > 1.0E-6) {
                    PlayerStateManager.setPlayerYawSafe(mc.player, EntityUtils.rotationToPitchYaw(horizontal).y);
                }
                EntityUtils.setEntityPitchSafe(mc.player, 0);
                machine.markForEndState();
                return STATE_REPLENISH;
            }
            clearForcedSlowFall();
            Vec3d velocity = mc.player.getVelocity();
            Vec3d horizontal = new Vec3d(velocity.x, 0, velocity.z);
            if (horizontal.lengthSquared() > 1.0E-6) {
                PlayerStateManager.setPlayerYawSafe(mc.player, EntityUtils.rotationToPitchYaw(horizontal).y);
            }
            double distance = mc.player.getY() - landing.getY();
            EntityUtils.setEntityPitchSafe(mc.player, distance > 32.0 ? 89 : 0);
            if (mc.player.getY() <= landing.getY() + 2.0 || mc.player.isOnGround()) {
                EntityUtils.setEntityPitchSafe(mc.player, -90);
                recovering = true;
                machine.markForEndState();
                return STATE_ASCEND;
            }
            machine.markForEndState();
            return STATE_REPLENISH;
        }

        private BlockPos findLandingColumn() {
            Vec3d predicted = mc.player.getPos().add(mc.player.getVelocity());
            BlockPos origin = BlockPos.ofFloored(predicted);
            if (!mc.world.getChunkManager().isChunkLoaded(origin.getX() >> 4, origin.getZ() >> 4)) {
                return null;
            }
            for (int y = origin.getY() - 1; y >= mc.world.getBottomY(); y--) {
                BlockPos ground = new BlockPos(origin.getX(), y, origin.getZ());
                var state = mc.world.getBlockState(ground);
                if (state.isAir() || state.isLiquid() || state.isReplaceable()) continue;
                if (mc.world.getBlockState(ground.up()).isAir()
                        && mc.world.getBlockState(ground.up(2)).isAir()) {
                    return ground;
                }
                break;
            }
            return null;
        }

        private void enableForcedSlowFall() {
            if (!FloatingUtils.INSTANCE.enableElytraSlowFall.get()) {
                FloatingUtils.INSTANCE.enableElytraSlowFall.set(true);
                forcedSlowFall = true;
            }
        }

        private void clearForcedSlowFall() {
            if (forcedSlowFall) {
                FloatingUtils.INSTANCE.enableElytraSlowFall.set(false);
                forcedSlowFall = false;
            }
        }

        @Override
        protected void handleFinishCheck() {
            if (control.checkFinish(ti)) {
                control.onStop(ti);
            }
        }

        public int onStateUpdate(StateMachine machine, int state) {
            if (ti == null || mc.player == null) return state;
            if (state == STATE_REPLENISH) return state;
            if (recovering && mc.player.getY() >= control.maxHeight.get()) {
                recovering = false;
                return STATE_DESCEND;
            }
            if (state == STATE_ASCEND && mc.player.getY() > control.maxFireworkHeight.get()) {
                return STATE_DESCEND;
            }
            if (state == STATE_DESCEND && mc.player.getY() < control.minHeight.get()) {
                return ElytraExtra.INSTANCE.findRocket() == null ? STATE_REPLENISH : STATE_ASCEND;
            }
            return state == STATE_NONE ? STATE_ASCEND : state;
        }

        private void checkNotStart() {
            if (!started && mc.player.isFallFlying()) {
                if (ti.state == TravelState.TOO_HIGH && PlayerStateManager.INSTANCE.lastKnownRealMovementSpeed.y < 0) {
                    started = true;
                } else {
                    handlePullUp();
                }
            } else if (!started) {
                maintainFlight();
            }
        }

        private float getBoostPitch() {
            if (!control.useFireworksBoostAngle.get()) {
                EntityUtils.setEntityPitchSafe(mc.player, -90);
                return -90;
            }
            double diagonalYaw = Math.floor((mc.player.getYaw() + 45.0) / 90.0) * 90.0 + 45.0;
            PlayerStateManager.setPlayerYawSafe(mc.player, (float) diagonalYaw);
            float pitch = ElytraOptimizeUtils.calculateBestPullUpAngle((float) diagonalYaw);
            EntityUtils.setEntityPitchSafe(mc.player, pitch);
            return pitch;
        }

        public int onStatePullup(StateMachine machine) {
            if (ElytraExtra.INSTANCE.findRocket() == null) {
                machine.markForEndState();
                return STATE_REPLENISH;
            }
            if (control.useFireworksBoostAngle.get()) {
                float pitch = getBoostPitch();
                launchFireworks(pitch, mc.player.getYaw());
            } else {
                EntityUtils.setEntityPitchSafe(mc.player, -90);
            }
            machine.markForEndState();
            return STATE_ASCEND;
        }

        public int onStateGlide(StateMachine machine) {
            Vec3d towards = ti.getCurrentFlyingTarget().subtract(mc.player.getPos());
            PlayerStateManager.setPlayerYawSafe(mc.player, EntityUtils.rotationToPitchYaw(towards).y);
            EntityUtils.setEntityPitchSafe(mc.player, control.glidePitch.get());
            machine.markForEndState();
            return STATE_DESCEND;
        }

        protected void handlePullUp() {
            float pitch = getBoostPitch();
            if (PlayerStateManager.INSTANCE.lastKnownRealMovementSpeed.y < 0) {
                launchFireworks(pitch, mc.player.getYaw());
            }
        }

        @Override
        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
            if (ti == null || ti.shouldNotRun()) return;
            control.updateState(ti, movementManagerEvent.context.playerStatus.entity.getY());
            checkNotStart();
            if (!started) {
                machine.setState(STATE_NONE);
                return;
            }
            if (machine.getState() == STATE_REPLENISH) {
                if (!mc.player.isFallFlying()) return;
                movementManagerEvent.context.pushImportantRotation(true, true);
                machine.step();
                return;
            }
            if (!mc.player.isFallFlying()) {
                maintainFlight();
                return;
            }
            movementManagerEvent.context.pushImportantRotation(true, true);
            machine.step();
        }
    }

    private static class TravelPitch40Grim extends AbstractPitch40 {

        public TravelPitch40Grim(TravellingControl control) {
            super(control);
        }

        @Override
        public Type getType() {
            return Type.ELYTRA_GRIM_FLY40;
        }

        boolean useGrimPacketFly = false;
        boolean currentDangerousVelocity = false;

        @Override
        public void onStart(TravelInfo state) {
            super.onStart(state);
            useGrimPacketFly = false;
        }

        @Override
        public int onStateUpdate(StateMachine machine, int state) {
            Vec3d velocity = PlayerStateManager.INSTANCE.lastKnownChangePosMovementSpeed;

            currentDangerousVelocity = ElytraGrimAccelerate.INSTANCE.fixOldVersionVelocityShit.get()
                    && (Math.abs(velocity.x) >= 3.8 || Math.abs(velocity.y) >= 3.8 || Math.abs(velocity.z) >= 3.8);
            return super.onStateUpdate(machine, state);
        }

        @Override
        public int onStateGlide(StateMachine machine) {
            counter2 = 0;
            EntityUtils.setEntityPitchSafe(mc.player, control.pitch40GrimPitch.get());
            if (control.limitSpeedForDangerousSpeed.get() && currentDangerousVelocity) {
                useGrimPacketFly = false;
            } else {
                if (mc.player.getY() > control.minHeight.get() || currentDangerousVelocity) {
                    useGrimPacketFly = true;
                } else {
                    useGrimPacketFly = false;
                }
            }
            machine.markForEndState();
            return STATE_GLIDE;
        }

        @Override
        public int onStatePullup(StateMachine machine) {
            if (useGrimPacketFly) {
                if (!currentDangerousVelocity) {
                    useGrimPacketFly = false;
                }
            }
            double y = mc.player.getY();
            if (control.pitch40HeightLimit.get().test(s -> y > s)) {
                return STATE_GLIDE;
            }
            double last3YY = this.last3Y[last3YIndex];
            boolean goingDown = (y < last3YY);
            if (!useGrimPacketFly && goingDown && counter2 > 10 && mc.player.getY() > control.minHeight.get()) {
                return STATE_GLIDE;
            }
            EntityUtils.setEntityPitchSafe(
                    mc.player,
                    Math.min(
                            -control.pitch40GrimNegative.get() + counter2 * (float) control.negativeArgumentGrim.get(),
                            control.pitch40GrimPitch.get()));
            machine.markForEndState();
            return STATE_PULL_UP;
        }

        @Override
        public int onStateEmergency(StateMachine machine) {
            useGrimPacketFly = false;
            return super.onStateEmergency(machine);
        }

        @Override
        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
            if (ti == null || ti.shouldNotRun()) return;
            ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
            control.updateState(ti, player.getY());
            checkNotStart();
            if (startWork) {
                if (mc.player.isFallFlying()) {
                    movementManagerEvent.context.pushImportantRotation(true, true);
                    machine.step();
                    if (AntiChunkLag.INSTANCE.currentMayFaceLagChunk && !currentDangerousVelocity) {
                        useGrimPacketFly = false;
                    }
                } else {
                    useGrimPacketFly = false;
                    maintainFlight();
                }
            } else {
                machine.setState(STATE_NONE);
            }
            if (useGrimPacketFly) {
                ElytraGrimAccelerate.INSTANCE.setTryWorkingTick();
            }
        }
    }

    // 封装原 move() 和 finish() 逻辑，返回 true 表示任务结束
    private boolean moveAndCheckFinish(TravelInfo ti, Vec3d delta) {
        if (delta.length() == 0) {
            MovTasks.moveToWithPackets(mc.player.getPos(), null);
            return false;
        } else {
            MovTasks.moveToWithPackets(mc.player.getPos().add(delta), false);
            return checkFinish(ti);
        }
    }

    private boolean checkFinish(TravelInfo ti) {
        if (travelTask != ti
                || ti.instance != this
                || mc.player.getPos().subtract(ti.getCurrentFlyingTarget()).horizontalLengthSquared() < 900) {
            outputTravelStats(ti);
            if (mc.player != null) {
                mc.player.setOnGround(false);
                ClientPlayerAccess.of(mc.player).setForceNoFall(true);
            }
            travelTask = null;
            MovTasks.doingTp = false; // 合并 cancel 清理
            return true;
        }
        return false;
    }

    private void updateState(TravelInfo ti, double currentY) {
        switch (ti.state) {
            case TOO_LOW:
                if (currentY > maxHeight.get()) {
                    ti.state = TravelState.TOO_HIGH;
                }
                break;
            case TOO_HIGH:
                if (currentY < maxHeight.get()) {
                    ti.state = TravelState.STABLE;
                }
                break;
            case STABLE:
                if (currentY < minHeight.get()) {
                    ti.state = TravelState.TOO_LOW;
                } else if (currentY > maxHeight.get()) {
                    ti.state = TravelState.TOO_HIGH;
                }
                break;
        }
    }

    private void onElytraVelocity(Event<EventContainer<FlightVelocity>> event) {
        if (travelDelegate instanceof TravelMoveVelocity velocity) {
            velocity.onElytra(event);
        }
    }

    public void onTravelCancel() {
        TravelInfo lastInfo = travelTask;
        if (lastInfo != null) {
            lastInfo.stop = true;
            lastInfo.stopManually = true;
        }
        onStop(travelTask);
        outputTravelStats(lastInfo);
        travelTask = null;
    }

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        acceptor.accept(createTitle("widget.travelling-control.command", 0, dblank, dx, dy));
    }

    public static TravelInfo travelTask;

    public static class TravelInfo {
        public Optional<Vec3d> pos0;
        public long startingTime;
        public Vec3d startPos;
        public TravelState state;
        public boolean stop = false;
        public TravellingControl instance;
        public boolean stopManually = false;
        public boolean pause;

        public void onStart(TravellingControl instance, Vec3d startPos) {
            startingTime = System.currentTimeMillis();
            stop = false;
            stopManually = false;
            pause = false;
            this.instance = instance;
            this.startPos = startPos;
        }

        public Vec3d getCurrentFlyingTarget() {
            return pos0.orElseGet(() -> {
                Vec3d playerPos = mc.player.getPos();
                Vec3d horizontal = EntityUtils.pitchYawToRotation(0, mc.player.getYaw());
                return playerPos.add(horizontal.multiply(10000)).withAxis(Direction.Axis.Y, playerPos.getY());
            });
        }

        public boolean shouldNotRun() {
            return mc.player == null || pause || stop;
        }
    }

    public static interface TravelDelegate {
        default void onStart(TravelInfo state) {}

        public boolean onTick(Event<Void> event);

        default void onStop() {}

        default Type getType() {
            return Type.TEST;
        }
    }

    public static enum TravelState {
        TOO_LOW,
        TOO_HIGH,
        STABLE;
    }
}
