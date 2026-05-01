package me.matl114.hacks.modules.move;

import com.google.common.util.concurrent.AtomicDouble;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.commands.MainCommand;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.events.catchers.PacketCatcherImpl;
import me.matl114.events.catchers.TimedPacketCatcherImpl;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.move.ElytraVelocity;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
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
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public class TravellingControl extends BaseModule {
    public static final String[] ENABLE = makePath("travelling-control.enable");
    public static final String[] CONTROL_TYPE = makePath("travelling-control.control-type");
    public static final String[] SPEED = makePath("travelling-control.speed");
    public static final String[] ELYTRA_SPEED = makePath("travelling-control.elytra-speed");
    public static final String[] MIN_HEIGHT = makePath("travelling-control.min-height");
    public static final String[] MAX_HEIGHT = makePath("travelling-control.max-height");

    public TravellingControl() {}

    //    public FlagRef enable = flagBuilder(Configs.MOV_CONFIG, ENABLE).build();

    public EnumRef<TravelControlType> controlType = builder(Configs.MOV_CONFIG, CONTROL_TYPE, TravelControlType.class)
            .defaultValue(TravelControlType.MOV_VOID)
            .build();

    public DoubleRef speed = builder(Configs.MOV_CONFIG, SPEED, DoubleRef.TYPE)
            .defaultValue(9.9D)
            .build();

    public DoubleRef elytraSpeed = builder(Configs.MOV_CONFIG, ELYTRA_SPEED, DoubleRef.TYPE)
            .defaultValue(1.7D)
            .build();

    public IntRef minHeight = builder(Configs.MOV_CONFIG, MIN_HEIGHT, IntRef.TYPE)
            .defaultValue(256)
            .build();

    public IntRef maxHeight = builder(Configs.MOV_CONFIG, MAX_HEIGHT, IntRef.TYPE)
            .defaultValue(400)
            .build();

    public IntRef void2Arg = builder(Configs.MOV_CONFIG, makePath("travelling-control.void-2-dup-packet"), IntRef.TYPE)
            .defaultValue(4)
            .validator(Configs.INT_POSITIVE)
            .build();

    public FlagRef pitch40SafeHeight = flagBuilder(
                    Configs.MOV_CONFIG, makePath("travelling-control.pitch-40-end-safety"))
            .build();

    public IntRef pitch40Pitch = builder(
                    Configs.MOV_CONFIG, makePath("travelling-control.pitch-40-pitch-positive"), IntRef.TYPE)
            .defaultValue(15)
            .validator(Configs.INT_POSITIVE)
            .build();
    public IntRef pitch40Negative = builder(
                    Configs.MOV_CONFIG, makePath("travelling-control.pitch-40-pitch-negative"), IntRef.TYPE)
            .defaultValue(60)
            .validator(Configs.INT_POSITIVE)
            .build();

    public DoubleRef negativeArgument = builder(
                    Configs.MOV_CONFIG, makePath("travelling-control.pitch-40-negative-delta"), DoubleRef.TYPE)
            .defaultValue(0.0)
            .validator(Configs.doubleRange(0, 90))
            .build();

    private boolean doingTp = false;
    private boolean exempt = false;

    public static enum TravelControlType implements ConfigEnum {
        ELYTRASKY, // 原 ELYTRA
        ELYTRA_PITCH40,
        ELYTRA_GRIM_FLY40,
        MOV_VOID,
        MOV_VOID_2,
        PEARL,
        TEST;

        @Override
        public Text getDisplay() {
            return Text.translatable(
                    "configenum.travel-control-type." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerCommandBootstrap(this::bootStrapTravelCommand);
        registerListener(Listener.getPacketPoint().getChannel(PlayerMoveC2SPacket.class), this::onPlayerMove);
        registerListener(Listener.getCustomListener().getChannel(ElytraVelocity.class), this::onElytraVelocity);
        registerListener(Listener.getPacketPoint().getChannel(TeleportConfirmC2SPacket.class), this::onTeleportConfirm);
    }

    private void onPlayerMove(Event<PlayerMoveC2SPacket> event) {
        if (doingTp) {
            if (exempt) {
                exempt = false;
            } else {
                event.cancel();
            }
        }
    }

    public void onTeleportConfirm(Event<TeleportConfirmC2SPacket> packetEvent) {
        exempt = true;
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
                            .name("cancel")
                            .helper("中断传送旅行")
                            .post(e -> e.executor(CommandContext.run(this::onTravelCancelCommand)))
                            .complete())
                    .complete();
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
        if (travelTask != null && travelTask.instance != this) {
            travelTask.stop = true;
            travelTask = null;
        }
        if (travelTask == null) {
            if (parsedCoord == null) return;
            TravelControlType type = controlType.get();
            Debug.chat("当前运动类型: " + type.getDisplay().getString());
            TravelInfo info = new TravelInfo();
            info.pos0 = parsedCoord;
            info.currentPlayer = mc.player;
            info.startingTime = System.currentTimeMillis();
            info.startPos = mc.player.getPos();
            info.stop = false;
            info.instance = this;
            travelTask = info;
            double initY = mc.player.getY();
            if (initY < minHeight.get()) {
                info.state = TravelState.TOO_LOW;
            } else if (initY > maxHeight.get()) {
                info.state = TravelState.TOO_HIGH;
            } else {
                info.state = TravelState.STABLE;
            }
            if (type == TravelControlType.ELYTRASKY) {
                Debug.chat("注意: Elytra_sky 模式需要配合启用鞘翅飞行控制才能正常 travel");
                Tasks.scheduleRepeated(this::onTravelTickElytra, 20, 2);
            } else if (type == TravelControlType.MOV_VOID) {
                Tasks.scheduleRepeated(this::onTravelTickMovVoid, 20, 2);
            } else if (type == TravelControlType.MOV_VOID_2) {
                catchResyncPackets = false;
                Listener.addPostPacketCatcher(new PacketCatcherImpl<>(PlayerPositionLookS2CPacket.class, (event -> {
                    catchResyncPackets = true;
                    return travelTask != info || info.stop;
                })));
                Tasks.scheduleRepeated(this::onTravelTickMovVoid2, 20, 2);
            } else if (type == TravelControlType.ELYTRA_PITCH40) {
                ClientPlayerAccess.of(mc.player)
                        .getLegalMovementManager()
                        .addMovementModifier(this.createTravelPitch40Controller(info));
                // fuck...
                Tasks.scheduleRepeated(this::onTravelPitch40DaemonTask, 20, 1);
            } else if (type == TravelControlType.ELYTRA_GRIM_FLY40) {
                ClientPlayerAccess.of(mc.player)
                        .getLegalMovementManager()
                        .addMovementModifier(this.createTravelGrimFly40Controller(info));
                // fuck...
                Tasks.scheduleRepeated(this::onTravelPitch40DaemonTask, 20, 1);
            } else {
                travelTask.stop = true;
                travelTask = null;
            }
        } else {
            Debug.chat("上一个travel task仍旧在执行,使用travel cancel取消");
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

    private boolean checkState(TravelInfo ti) {
        if (checkNull() || ti == null || ti.stop || ti.instance != this) {
            if (ti != null) ti.stop = true;
            travelTask = null;
            MovTasks.doingTp = false; // 原 cancel() 中的逻辑
            elytraPos = null;
            return true;
        }
        return false;
    }

    boolean catchResyncPackets = false;

    private boolean onTravelTickMovVoid() {
        MovTasks.doingTp = false;
        TravelInfo ti = travelTask;
        if (checkState(ti)) {
            return true;
        }
        mc.player.setOnGround(false);
        ti.tickCNT += 1;

        double currentY = mc.player.getY();
        updateState(ti, currentY);

        double horizontalSpeed = speed.get();

        if (ti.state == TravelState.STABLE) {
            Vec3d towards = ti.pos0.subtract(mc.player.getPos());
            Vec3d towardsHorizontal = new Vec3d(towards.x, 0, towards.z).normalize();

            if (moveAndCheckFinish(
                    ti, towardsHorizontal.multiply(horizontalSpeed).add(0, -0.05, 0))) {
                return true;
            }
            if (moveAndCheckFinish(
                    ti, towardsHorizontal.multiply(horizontalSpeed).add(0, -0.05, 0))) {
                return true;
            }
            if (ti.tickCNT % 3 == 0) {
                if (moveAndCheckFinish(
                        ti, towardsHorizontal.multiply(horizontalSpeed).add(0, -0.05, 0))) {
                    return true;
                }
            }
        } else {
            double targetY = ti.state == TravelState.TOO_LOW ? maxHeight.get() : minHeight.get();
            double deltaY;
            if ((ti.tickCNT % 20) < 18) {
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

    private boolean onTravelTickMovVoid2() {
        MovTasks.doingTp = false;
        TravelInfo ti = travelTask;
        if (checkState(ti)) {
            return true;
        }
        mc.player.setOnGround(false);
        ti.tickCNT += 1;

        double currentY = mc.player.getY();
        updateState(ti, currentY);

        double horizontalSpeed = speed.get() - 0.05;

        if (ti.state == TravelState.STABLE) {
            if (checkFinish(ti)) {
                return true;
            }
            Vec3d towards = ti.pos0.subtract(mc.player.getPos());
            towards = new Vec3d(towards.x, 0, towards.z);
            double len = towards.horizontalLengthSquared();
            Vec3d towardsHorizontal = towards.normalize();
            Vec3d delta =
                    towardsHorizontal.multiply(horizontalSpeed).add(0, -0.05, 0).multiply(void2Arg.get());
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
            if ((ti.tickCNT % 20) < 18) {
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
                || mc.player != ti.currentPlayer
                || mc.player.getPos().subtract(ti.pos0).horizontalLengthSquared() < 900) {
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

    private Vec3d elytraPos = null;

    private boolean onTravelTickElytra() {
        TravelInfo ti = travelTask;
        if (checkState(ti)) {
            return true;
        }
        mc.player.setOnGround(false);
        ti.tickCNT += 1;

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
            Vec3d towards = ti.pos0.subtract(currentPos);
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
            if ((ti.tickCNT % 20) < 18) {
                double direction = Math.signum(targetY - currentY);
                deltaY = direction * elytraSpeed.get() * 10; // 使用鞘翅速度
            } else {
                deltaY = -0.3;
            }

            elytraPos = mc.player.getPos().add(0, deltaY, 0);
        }

        return false;
    }
    // 总结 一定要 1. 及时断线 2. 断线之后要开自动鞘翅或者甲飞+平飞拉回来 3. 看情况 可以考虑不下降， 继续飞
    private LegalMovementManager.MovementModifier createTravelPitch40Controller(TravelInfo state) {
        state.state = TravelState.TOO_LOW;
        return new LegalMovementManager.MovementModifier() {

            final TravelInfo ti = state;
            boolean startWork = false;
            boolean stillWork = true;
            int counter = 0;
            int counter2 = 0;
            float randomOffsetPitch = 0.0F;
            float randomOffsetYaw = 0.0F;
            Random rand = new Random();
            int dangerousNoFallFlyingTick = 0;
            double[] last3Y = {-999, -999, -999, -999, -999};
            int last3YIndex = 0;

            @Override
            public int priority() {
                return PRIORITY_LOW;
            }

            @Override
            public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
                updateState(ti, player.getY());
                if (!startWork && mc.player.isFallFlying()) {
                    if (ti.state == TravelState.TOO_HIGH) {
                        startWork = true;
                        Debug.chat("[Pitch440] 开始工作!");
                    } else if (++counter % 60 == 0) {
                        Debug.chat("[Pitch40] 请拉升到MaxHeight以启动:", maxHeight.get());
                    }
                }
                if (startWork) {
                    if (mc.player.isFallFlying()) {
                        movementManagerEvent.context.pushImportantRotation(true, true);
                        Vec3d currentPos = mc.player.getPos();
                        Vec3d towards = ti.pos0.subtract(currentPos);
                        // anti afk
                        if (Tasks.getTick() % 40 == 0) {
                            randomOffsetPitch = (float) rand.nextDouble(-2.5, 2.5);
                            randomOffsetYaw = (float) rand.nextDouble(1.0F);
                        }
                        float yaw = EntityUtils.rotationToPitchYaw(towards.normalize()).y + randomOffsetPitch;
                        counter2 += 1;

                        switch (ti.state) {
                            case STABLE, TOO_HIGH -> {
                                EntityUtils.setEntityYawSafe(player, yaw);
                                double y = mc.player.getY();
                                double last3YY = this.last3Y[last3YIndex];
                                boolean goingDown = (y < last3YY);
                                if (goingDown) {
                                    if (counter2 > 1) {
                                        Debug.chat("[Pitch40] Current Height", player.getY());
                                    }
                                    counter2 = 0;
                                    EntityUtils.setEntityPitchSafe(player, pitch40Pitch.get() + randomOffsetYaw);
                                } else {
                                    // fly higher..
                                    EntityUtils.setEntityPitchSafe(
                                            player,
                                            Math.min(
                                                            -pitch40Negative.get()
                                                                    + counter2 * (float) negativeArgument.get(),
                                                            pitch40Pitch.get())
                                                    + randomOffsetYaw);
                                }
                            }
                            case TOO_LOW -> {
                                EntityUtils.setEntityYawSafe(player, yaw);
                                EntityUtils.setEntityPitchSafe(
                                        player,
                                        Math.min(
                                                        -pitch40Negative.get()
                                                                + counter2 * (float) negativeArgument.get(),
                                                        pitch40Pitch.get())
                                                + randomOffsetYaw);
                            }
                        }
                    } else {
                        if (dangerousNoFallFlyingTick > 20) {
                            dangerousNoFallFlyingTick = 0;
                        }
                        if (dangerousNoFallFlyingTick == 0) {
                            // reset fucking jump input
                            MovTasks.getMovExtra().sendPacketsForInventoryAction();
                            // launch event from this method
                            if (mc.player.checkGliding()) {
                                mc.getNetworkHandler()
                                        .sendPacket(new ClientCommandC2SPacket(
                                                mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                            }

                            // start counting down, if not startflying in 20 tick(1sec), auto logout
                            dangerousNoFallFlyingTick = 1;
                            MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();
                        }
                    }
                }
            }

            @Override
            public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                if (startWork) {
                    // main logic, just logout for safety
                    // movementManagerEvent.context.playerStatus.restoreRotation();
                    ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
                    if (pitch40SafeHeight.get() && player.getY() < minHeight.get() - 16) {
                        // emergency
                        Debug.chat("[Pitch40] 滑翔失控了");
                        if (pitch40SafeHeight.get()) {
                            Debug.info("Pitch40 out of control!");
                            MainTasks.scheduleDisconnect();
                        }
                        startWork = false;
                        stillWork = false;
                        // return immediately.
                        return false;
                    }
                    if (mc.player != null && !mc.player.isFallFlying()) {
                        // start counting down
                        if (dangerousNoFallFlyingTick > 0) {
                            dangerousNoFallFlyingTick += 1;
                        }
                    } else {
                        dangerousNoFallFlyingTick = 0;
                    }
                }
                if (checkFinish(ti)) {
                    if (!ti.stopManually && pitch40SafeHeight.get()) {
                        Debug.chat("[Pitch40] 当前处于虚空维度, 我们需要确保你不会掉下去!");
                        Debug.chat("[Pitch40] 我们需要自动断线");
                        MainTasks.scheduleDisconnect();
                    }
                    stillWork = false;
                }
                if (mc.player != null) {
                    last3Y[last3YIndex] = mc.player.getY();
                    last3YIndex = (last3YIndex + 1) % last3Y.length;
                }
                return stillWork;
            }
        };
    }

    private LegalMovementManager.MovementModifier createTravelGrimFly40Controller(TravelInfo state) {
        state.state = TravelState.TOO_LOW;
        return new LegalMovementManager.MovementModifier() {

            final TravelInfo ti = state;
            boolean startWork = false;
            boolean stillWork = true;
            int counter = 0;
            int counter2 = 0;
            float randomOffsetPitch = 0.0F;
            float randomOffsetYaw = 0.0F;
            Random rand = new Random();
            int dangerousNoFallFlyingTick = 0;

            @Override
            public int priority() {
                return PRIORITY_LOW;
            }

            Packet<?> storedPacket = null;
            // avoid setbacks
            double[] last3Y = {-999, -999, -999, -999, -999, -999, -999, -999, -999, -999};
            int last3YIndex = 0;
            boolean currentFlyingHigh = false;
            boolean useGrimPacketFly = false;

            @Override
            public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
                updateState(ti, player.getY());
                if (!startWork && mc.player.isFallFlying()) {
                    if (ti.state == TravelState.TOO_HIGH) {
                        startWork = true;
                        Debug.chat("[Pitch440] 开始工作!");
                    } else if (++counter % 60 == 0) {
                        Debug.chat("[Pitch40] 请拉升到MaxHeight以启动:", maxHeight.get());
                    }
                }
                final int pitch40 = pitch40Pitch.get();
                final int pitchn40 = -pitch40Negative.get();
                if (startWork) {
                    if (mc.player.isFallFlying()) {
                        movementManagerEvent.context.pushImportantRotation(true, true);
                        Vec3d currentPos = mc.player.getPos();
                        Vec3d towards = ti.pos0.subtract(currentPos);
                        // anti afk
                        if (Tasks.getTick() % 40 == 0) {
                            randomOffsetPitch = (float) rand.nextDouble(-2.5, 2.5);
                            randomOffsetYaw = (float) rand.nextDouble(1.0F);
                        }
                        float yaw = EntityUtils.rotationToPitchYaw(towards.normalize()).y + randomOffsetPitch;
                        counter2 += 1;

                        switch (ti.state) {
                            case STABLE, TOO_HIGH -> {
                                EntityUtils.setEntityYawSafe(player, yaw);
                                double y = mc.player.getY();
                                double last3YY = this.last3Y[last3YIndex];
                                boolean goingDown = (y < last3YY);
                                if (currentFlyingHigh && goingDown) {
                                    currentFlyingHigh = false;
                                    Debug.chat("[Pitch40] Current Height", last3YY);
                                }
                                if (!currentFlyingHigh) {
                                    counter2 = 0;
                                    useGrimPacketFly = true;
                                    EntityUtils.setEntityPitchSafe(player, pitch40 + randomOffsetYaw);
                                } else {
                                    EntityUtils.setEntityPitchSafe(
                                            player,
                                            Math.min(
                                                            -pitch40Negative.get()
                                                                    + counter2 * (float) negativeArgument.get(),
                                                            pitch40Pitch.get())
                                                    + randomOffsetYaw);
                                }
                            }
                            case TOO_LOW -> {
                                if (!currentFlyingHigh) {
                                    // at this tick, we launch a PacketCatcher
                                    AtomicInteger counter = new AtomicInteger(20);
                                    AtomicDouble max = new AtomicDouble(-999);
                                    Listener.addPostPacketCatcher(new TimedPacketCatcherImpl<>(
                                            EntityVelocityUpdateS2CPacket.class, 200, (event) -> {
                                                Vec3d velocity = event.context.getVelocity();
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
                                                        -pitch40Negative.get()
                                                                + counter2 * (float) negativeArgument.get(),
                                                        pitch40Pitch.get())
                                                + randomOffsetYaw);
                            }
                        }
                    } else {
                        if (dangerousNoFallFlyingTick > 20) {
                            dangerousNoFallFlyingTick = 0;
                        }
                        if (dangerousNoFallFlyingTick == 0) {
                            // reset fucking jump input
                            MovTasks.getMovExtra().sendPacketsForInventoryAction();
                            // launch event from this method
                            if (mc.player.checkGliding()) {
                                mc.getNetworkHandler()
                                        .sendPacket(new ClientCommandC2SPacket(
                                                mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                            }

                            // start counting down, if not startflying in 20 tick(1sec), auto logout
                            dangerousNoFallFlyingTick = 1;
                            MovTasks.getMovExtra().sendPacketsForPostStartFallFlying();
                        }
                    }
                }
            }

            @Override
            public void applyBeforeMovementPacketModify(Event<LegalMovementManager> movementManagerEvent) {
                if (startWork
                        && mc.player != null
                        && mc.player.isFallFlying()
                        && useGrimPacketFly
                        && !MovTasks.getElytraExtra().canFireworkControlMotion()) {
                    // working tick
                    if (true) {
                        movementManagerEvent.context.playerStatus.restorePos();
                        movementManagerEvent.cancel();
                        storedPacket = VPacket.newFull(
                                mc.player.getX(),
                                mc.player.getY() + 0.25 * ((Tasks.getTick() % 3) + 1),
                                mc.player.getZ(),
                                mc.player.getYaw(),
                                mc.player.getPitch(),
                                mc.player.isOnGround(),
                                mc.player.horizontalCollision);
                    }
                }
            }

            @Override
            public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                if (storedPacket != null) {
                    // avoid bad packet fix
                    Listener.sendPacketNoEvents(storedPacket);
                    storedPacket = null;
                }
                if (startWork) {
                    // main logic, just logout for safety
                    // movementManagerEvent.context.playerStatus.restoreRotation();
                    ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;
                    if (player.getY() < minHeight.get() - 16) {
                        // emergency
                        Debug.chat("[Pitch40] 滑翔失控了");
                        if (pitch40SafeHeight.get()) {
                            Debug.info("Pitch40 out of control!");
                            MainTasks.scheduleDisconnect();
                        }
                        startWork = false;
                        stillWork = false;
                        // return immediately.
                        return false;
                    }

                    if (mc.player != null && !mc.player.isFallFlying()) {
                        // start counting down
                        if (dangerousNoFallFlyingTick > 0) {
                            dangerousNoFallFlyingTick += 1;
                        }
                    } else {
                        dangerousNoFallFlyingTick = 0;
                    }
                }
                if (checkFinish(ti)) {
                    if (!ti.stopManually && pitch40SafeHeight.get()) {
                        Debug.chat("[Pitch40] 当前处于虚空维度, 我们需要确保你不会掉下去!");
                        Debug.chat("[Pitch40] 我们需要自动断线");
                        MainTasks.scheduleDisconnect();
                    }
                    stillWork = false;
                }
                if (mc.player != null) {
                    last3Y[last3YIndex] = mc.player.getY();
                    last3YIndex = (last3YIndex + 1) % last3Y.length;
                }
                return stillWork;
            }
        };
    }

    private boolean onTravelPitch40DaemonTask() {
        // player cancel it by hand
        if (mc.player != null && travelTask == null) {
            return true;
        }
        if (mc.player == null) {
            if (pitch40SafeHeight.get()) {
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
            // cancel the task automatically
            if (travelTask != null) {
                travelTask.stop = true;
                travelTask = null;
            }
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

    private void onElytraVelocity(Event<EventContainer<ElytraVelocity>> event) {
        if (elytraPos == null) {
            return;
        }
        EventContainer<ElytraVelocity> eventContainer = event.context();
        ElytraVelocity velocity = eventContainer.getValue();
        Vec3d towards = elytraPos.subtract(mc.player.getPos()).normalize().multiply(speed.get());
        velocity.x(towards.x).y(towards.y).z(towards.z);
    }

    public void onTravelCancelCommand() {
        if (travelTask != null) {
            travelTask.stop = true;
            travelTask.stopManually = true;
            travelTask = null;
        }
        doingTp = false;
        elytraPos = null;
    }

    public static TravelInfo travelTask;

    public static class TravelInfo {
        public Vec3d pos0;
        public ClientPlayerEntity currentPlayer;
        public long startingTime;
        public Vec3d startPos;
        public int tickCNT = 0;
        public TravelState state;
        public boolean stop = false;
        public TravellingControl instance;
        public boolean stopManually = false;
    }

    public static enum TravelState {
        TOO_LOW,
        TOO_HIGH,
        STABLE;
    }
}
