package me.matl114.hacks.modules.move;

import com.google.common.util.concurrent.AtomicDouble;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.commands.MainCommand;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.events.catchers.TimedPacketCatcherImpl;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.OptionalPrimitive;
import me.matl114.hacks.utils.move.FlightVelocity;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.types.ExecutePos;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.versioned.api.VPacket;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.EnterReconfigurationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
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

    public DoubleRef elytraSpeed = builder(travellingControl.add("elytra-speed"), DoubleRef.TYPE)
            .defaultValue(1.7D)
            .show(() -> controlType.get().isIn(Type.ELYTRASKY))
            .build();

    public IntRef minHeight = builder(travellingControl.add("min-height"), IntRef.TYPE)
            .defaultValue(256)
            .build();

    public IntRef maxHeight = builder(travellingControl.add("max-height"), IntRef.TYPE)
            .defaultValue(400)
            .build();

    public IntRef void2Arg = builder(travellingControl.add("void-2-dup-packet"), IntRef.TYPE)
            .defaultValue(4)
            .validator(Configs.INT_POSITIVE)
            .show(() -> controlType.get().isIn(Type.MOV_VOID_2))
            .build();

    public FlagRef pitch40SafeHeight = flagBuilder(travellingControl.add("pitch-40-end-safety"))
            .show(() -> controlType.get().isIn(Type.ELYTRA_PITCH40, Type.ELYTRA_GRIM_FLY40))
            .build();

    public FlagRef pitch40SafeKick = flagBuilder(travellingControl.add("pitch-40-end-kick"))
            .show(() -> controlType.get().isIn(Type.ELYTRA_PITCH40, Type.ELYTRA_GRIM_FLY40))
            .build();

    public FlagRef pitch40SafeEnd = flagBuilder(travellingControl.add("pitch-40-end-safety-2"))
            .show(() -> controlType.get().isIn(Type.ELYTRA_PITCH40, Type.ELYTRA_GRIM_FLY40))
            .build();

    public FlagRef pitch40SafeHeightAutoPullup = flagBuilder(travellingControl.add("pitch-40-auto-pull-up"))
            .show(() -> controlType.get().isIn(Type.ELYTRA_PITCH40, Type.ELYTRA_GRIM_FLY40))
            .build();

    public IntRef pitch40Pitch = builder(travellingControl.add("pitch-40-pitch-positive"), IntRef.TYPE)
            .defaultValue(15)
            .validator(Configs.INT_POSITIVE)
            .show(() -> controlType.get().isIn(Type.ELYTRA_PITCH40, Type.ELYTRA_GRIM_FLY40))
            .build();
    public IntRef pitch40Negative = builder(travellingControl.add("pitch-40-pitch-negative"), IntRef.TYPE)
            .defaultValue(60)
            .validator(Configs.INT_POSITIVE)
            .show(() -> controlType.get().isIn(Type.ELYTRA_PITCH40, Type.ELYTRA_GRIM_FLY40))
            .build();

    public DoubleRef negativeArgument = builder(travellingControl.add("pitch-40-negative-delta"), DoubleRef.TYPE)
            .defaultValue(0.0)
            .validator(Configs.doubleRange(0, 90))
            .show(() -> controlType.get().isIn(Type.ELYTRA_PITCH40, Type.ELYTRA_GRIM_FLY40))
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
        ELYTRA_GRIM_FLY40,
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
                    Debug.chat("重新加载上一个Travel task中...");
                    info.onStart(this, mc.player.getPos());
                    travelDelegate.onStop();
                    travelDelegate.onStart(info);
                    Debug.chat("上一个travel task重新加载完成,使用travel cancel取消");
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
                            .helper("<coord> 自动传送旅行")
                            .arg(SimpleCommandArgs.argumentBuilder(MovTasks.TpaAndPosArgumentType::new)
                                    .name("target")
                                    .build())
                            .post(e -> e.executor(this::onTravelTo))
                            .complete()
                            .subBuilder(SubCommand.taskBuilder())
                            .name("auto")
                            .helper("执行可手动控制的定向飞行")
                            .post(e -> e.executor(CommandContext.execute(this::onTravelAuto)))
                            .complete()
                            .subBuilder(SubCommand.taskBuilder())
                            .name("cancel")
                            .helper("中断传送旅行")
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
        if (travelTask == null) {
            if (parsedCoord == null) return;
            travelMode(Optional.of(parsedCoord));
        } else {
            Debug.chat("上一个travel task仍旧在执行,使用travel cancel取消");
        }
    }

    public void toggleTravelAuto() {
        if (checkNull()) return;
        checkSelf();
        if (travelTask == null) {
            Debug.chat("启动Auto飞行模式");
            travelMode(Optional.empty());
        } else {
            Debug.chat("上一个travel task仍旧在执行,自动取消中...");
            onTravelCancel();
        }
    }

    public void onTravelAuto(CommandExecution var1) {
        checkSelf();
        if (travelTask == null) {
            travelMode(Optional.empty());
        } else {
            Debug.chat("上一个travel task仍旧在执行,自动取消中...");
        }
    }

    public void travelMode(Optional<Vec3d> traget) {
        Type type = controlType.get();
        Debug.chat("当前运动类型: " + type.getDisplay().getString());
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
        Debug.chat("当前travel task已完成或者终止");
        long usedSec = (System.currentTimeMillis() - ti.startingTime) / 1000L;
        Debug.info("using time", usedSec);
        if (mc.player != null) {
            double len = mc.player.getPos().distanceTo(ti.startPos);
            double avgSpeed = usedSec > 0 ? len / usedSec : 0;
            Debug.chat(
                    "时间开销:",
                    usedSec,
                    "s, 运行距离: ",
                    String.format("%.2f", len),
                    ", 平均速度: ",
                    String.format("%.2f", avgSpeed),
                    "m/s");
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
        private Vec3d elytraPos = null;
        int tickCNT = 0;

        @Override
        public Type getType() {
            return Type.ELYTRASKY;
        }

        @Override
        public boolean onTick(Event<Void> event) {
            TravelInfo ti = travelTask;
            mc.player.setOnGround(false);
            tickCNT += 1;

            double currentY = mc.player.getY();
            double elySpeed = elytraSpeed.get() * 10;

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

            // 根据状态计算目标位置 elytraPos
            if (ti.state == TravelState.STABLE) {
                if (checkFinish(ti)) {
                    return true;
                }
                Vec3d currentPos = mc.player.getPos();
                Vec3d towards = ti.getCurrentFlyingTarget().subtract(currentPos);
                Vec3d direction = towards.normalize()
                        .withAxis(Direction.Axis.Y, 0)
                        .multiply(elySpeed)
                        .add(0, -0.05, 0);
                elytraPos = currentPos.add(direction.multiply(10));
            } else {
                // 高度修正目标
                double targetY;
                if (ti.state == TravelState.TOO_LOW) {
                    targetY = maxHeight.get();
                } else { // TOO_HIGH
                    targetY = minHeight.get();
                }

                double deltaY;
                if ((tickCNT % 20) < 18) {
                    double direction = Math.signum(targetY - currentY);
                    deltaY = direction * elytraSpeed.get() * 10; // 使用鞘翅速度
                } else {
                    deltaY = -0.3;
                }

                elytraPos = mc.player.getPos().add(0, deltaY, 0);
            }

            return false;
        }

        public void onElytra(Event<EventContainer<FlightVelocity>> event) {
            if (elytraPos == null) {
                return;
            }
            EventContainer<FlightVelocity> eventContainer = event.context();
            if (eventContainer.getValue().mode() != FlightVelocity.Mode.ELYTRA_FLIGHT) return;
            FlightVelocity velocity = eventContainer.getValue();
            Vec3d towards = elytraPos.subtract(mc.player.getPos()).normalize().multiply(speed.get());
            velocity.x(towards.x).y(towards.y).z(towards.z);
        }
    }

    private abstract static class AbstractPitch40 implements TravelDelegate, LegalMovementManager.MovementModifier {
        TravellingControl control;

        public AbstractPitch40(TravellingControl control) {
            this.control = control;
        }

        TravelInfo ti = null;
        boolean startWork = false;
        int counter = 0;
        int counter2 = 0;
        int dangerousNoFallFlyingTick = 0;
        double[] last3Y = {-999, -999, -999, -999, -999};
        int last3YIndex = 0;

        @Override
        public void onStart(TravelInfo state) {
            this.ti = state;
            this.ti.state = TravelState.TOO_LOW;
            startWork = false;
            counter = 0;
            counter2 = 0;
            dangerousNoFallFlyingTick = 0;
            last3Y = new double[] {-999, -999, -999, -999, -999, -999, -999, -999, -999, -999};
            last3YIndex = 0;
        }

        protected void checkNotStart() {
            if (!startWork) {
                if (mc.player.isFallFlying()) {
                    if (ti.state == TravelState.TOO_HIGH) {
                        startWork = true;
                        Debug.chat("[Pitch440] 开始工作!");
                    } else {
                        if (control.pitch40SafeHeightAutoPullup.get()) {
                            handlePullUp();
                        }
                        if (++counter % 60 == 0) {
                            Debug.chat("[Pitch40] 请拉升到MaxHeight以启动:", control.maxHeight.get());
                        }
                    }
                } else {
                    if (control.pitch40SafeHeightAutoPullup.get()) {
                        ElytraExtra.INSTANCE.autoTakeoff();
                    }
                }
            }
        }

        protected void handlePullUp() {
            EntityUtils.setEntityPitchSafe(mc.player, -control.pitch40Negative.get());
            // fire only when down wards, make full use of travel
            if (!ElytraExtra.INSTANCE.canFireworkControlMotion()
                    && Tasks.getTick() % 5 == 0
                    && mc.player.getVelocity().y < 0) {
                ElytraExtra.INSTANCE.sendCustomUseFireworkPacket();
            }
        }

        protected void handleReFly() {
            if (dangerousNoFallFlyingTick > 20) {
                dangerousNoFallFlyingTick = 0;
            }
            if (dangerousNoFallFlyingTick == 0) {
                // reset fucking jump input
                MovTasks.getMovExtra().sendPacketsForInventoryAction();
                // launch event from this method
                if (mc.player.checkFallFlying()) {
                    mc.getNetworkHandler()
                            .sendPacket(new ClientCommandC2SPacket(
                                    mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                }

                // start counting down, if not startflying in 20 tick(1sec), auto logout
                dangerousNoFallFlyingTick = 1;
                MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();
            }
        }

        protected boolean handlePostHeight() {
            if (control.pitch40SafeHeightAutoPullup.get() && mc.player.getY() < control.minHeight.get() - 16) {
                handlePullUp();
            }
            if (control.pitch40SafeHeight.get() && mc.player.getY() < control.minHeight.get() - 32) {
                // emergency
                Debug.chat("[Pitch40] 滑翔失控了");
                if (control.pitch40SafeHeight.get()) {
                    Debug.info("Pitch40 out of control!");
                    MainTasks.scheduleDisconnect();
                }
                startWork = false;
                control.onStop(ti);
                return false;
                // return immediately.
            }
            if (mc.player != null && !mc.player.isFallFlying()) {
                // start counting down
                if (dangerousNoFallFlyingTick > 0) {
                    dangerousNoFallFlyingTick += 1;
                }
            } else {
                dangerousNoFallFlyingTick = 0;
            }
            return true;
        }

        protected void handleFinishCheck() {
            if (control.checkFinish(ti)) {
                if (!ti.stopManually && control.pitch40SafeEnd.get()) {
                    Debug.chat("[Pitch40] 我们到达了目的地了");
                    Debug.chat("[Pitch40] 我们需要自动断线");
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
            ti = null;
            startWork = false;
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
                if (control.pitch40SafeKick.get()) {
                    Tasks.scheduleRepeated(
                            () -> {
                                if (mc.player != null) {
                                    Debug.chat("Disconnect because of safety");
                                    MainTasks.scheduleDisconnect();
                                    return true;
                                } else {
                                    return false;
                                }
                            },
                            1,
                            1);
                }
                // cancel or pause the task here
                return true;
            }
            return false;
        }

        @Override
        public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
            if (ti == null || ti.shouldNotRun()) return true;
            if (startWork) {
                // main logic, just logout for safety
                // movementManagerEvent.context.playerStatus.restoreRotation();
                if (!handlePostHeight()) {
                    return true;
                }
            }
            handleFinishCheck();
            return true;
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
        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
            if (ti == null || ti.shouldNotRun()) return;
            ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
            control.updateState(ti, player.getY());
            checkNotStart();
            if (startWork) {
                if (mc.player.isFallFlying()) {
                    movementManagerEvent.context.pushImportantRotation(true, true);
                    Vec3d currentPos = mc.player.getPos();
                    Vec3d towards = ti.getCurrentFlyingTarget().subtract(currentPos);
                    // anti afk

                    float yaw = EntityUtils.rotationToPitchYaw(towards.normalize()).y;
                    counter2 += 1;

                    switch (ti.state) {
                        case STABLE, TOO_HIGH -> {
                            EntityUtils.setEntityYawSafe(player, yaw);
                            double y = mc.player.getY();
                            double last3YY = this.last3Y[last3YIndex];
                            boolean goingDown = (y < last3YY);
                            // height limit,
                            if (control.pitch40HeightLimit.get().test(s -> y > s)) {
                                goingDown = true;
                            }
                            if (goingDown) {
                                if (counter2 > 1) {
                                    Debug.chat("[Pitch40] Current Height", player.getY());
                                }
                                counter2 = 0;
                                EntityUtils.setEntityPitchSafe(player, control.pitch40Pitch.get());
                            } else {
                                // fly higher..
                                EntityUtils.setEntityPitchSafe(
                                        player,
                                        Math.min(
                                                -control.pitch40Negative.get()
                                                        + counter2 * (float) control.negativeArgument.get(),
                                                control.pitch40Pitch.get()));
                            }
                        }
                        case TOO_LOW -> {
                            EntityUtils.setEntityYawSafe(player, yaw);
                            EntityUtils.setEntityPitchSafe(
                                    player,
                                    Math.min(
                                            -control.pitch40Negative.get()
                                                    + counter2 * (float) control.negativeArgument.get(),
                                            control.pitch40Pitch.get()));
                        }
                    }
                } else {
                    handleReFly();
                }
            }
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

        boolean currentFlyingHigh = false;
        boolean useGrimPacketFly = false;

        @Override
        public void onStart(TravelInfo state) {
            super.onStart(state);
            currentFlyingHigh = false;
            useGrimPacketFly = false;
        }

        @Override
        public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
            if (ti == null || ti.shouldNotRun()) return;
            ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
            control.updateState(ti, player.getY());
            checkNotStart();
            final int pitch40 = control.pitch40Pitch.get();
            if (startWork) {
                if (mc.player.isFallFlying()) {
                    movementManagerEvent.context.pushImportantRotation(true, true);
                    Vec3d currentPos = mc.player.getPos();
                    Vec3d towards = ti.getCurrentFlyingTarget().subtract(currentPos);
                    // anti afk
                    float yaw = EntityUtils.rotationToPitchYaw(towards.normalize()).y;
                    counter2 += 1;

                    switch (ti.state) {
                        case STABLE, TOO_HIGH -> {
                            EntityUtils.setEntityYawSafe(player, yaw);
                            double y = mc.player.getY();
                            double last3YY = this.last3Y[last3YIndex];
                            boolean goingDown = (y < last3YY);
                            // height limit,
                            if (control.pitch40HeightLimit.get().test(s -> y > s)) {
                                goingDown = true;
                            }
                            if (currentFlyingHigh && goingDown) {
                                currentFlyingHigh = false;
                                Debug.chat("[Pitch40] Current Height", last3YY);
                            }
                            if (!currentFlyingHigh) {
                                counter2 = 0;
                                useGrimPacketFly = true;
                                EntityUtils.setEntityPitchSafe(player, pitch40);
                            } else {
                                EntityUtils.setEntityPitchSafe(
                                        player,
                                        Math.min(
                                                -control.pitch40Negative.get()
                                                        + counter2 * (float) control.negativeArgument.get(),
                                                control.pitch40Pitch.get()));
                            }
                        }
                        case TOO_LOW -> {
                            if (!currentFlyingHigh) {
                                // at this tick, we launch a PacketCatcher
                                AtomicInteger counter = new AtomicInteger(20);
                                AtomicDouble max = new AtomicDouble(-999);
                                Listener.addPostPacketCatcher(new TimedPacketCatcherImpl<>(
                                        EntityVelocityUpdateS2CPacket.class, 200, (event) -> {
                                            Vec3d velocity = VPacket.getVelocity(event.context);
                                            //  Debug.chat("check velocity", velocity);
                                            if (velocity.y <= max.get()
                                                    || velocity.y > 3.0F
                                                    || counter.getAndDecrement() < 0) {
                                                Tasks.scheduleDelayed(() -> useGrimPacketFly = false, 0);
                                                // useGrimPacketFly = false;

                                                return true;
                                            }
                                            max.set(velocity.y);
                                            return false;
                                        }));
                            }
                            currentFlyingHigh = true;
                            EntityUtils.setEntityYawSafe(player, yaw);
                            EntityUtils.setEntityPitchSafe(
                                    player,
                                    Math.min(
                                            -control.pitch40Negative.get()
                                                    + counter2 * (float) control.negativeArgument.get(),
                                            control.pitch40Pitch.get()));
                        }
                    }
                } else {
                    handleReFly();
                }
            }
            if (useGrimPacketFly) {
                MovTasks.getElytraGrimAccelerate().setTryWorkingTick();
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
        if (travelTask != null) {
            travelTask.stop = true;
            travelTask.stopManually = true;
        }
        onStop(travelTask);
        outputTravelStats(travelTask);
        travelTask = null;
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
                return playerPos.add(horizontal.multiply(100000)).withAxis(Direction.Axis.Y, playerPos.getY());
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
