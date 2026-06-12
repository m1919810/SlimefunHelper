package me.matl114.hacks.modules.move;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import me.matl114.commands.MainCommand;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.FileManager;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.file.FileStorage;
import me.matl114.utils.CommonUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.CommandExecution;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class PathManager extends BaseModule {
    public static PathManager INSTANCE;

    private static final double SAMPLE_PER_BLOCK = 4.0D;
    private static final int SNAPSHOT_ROLLBACK_TICKS = 20;
    private static final double SNAPSHOT_ROLLBACK_DISTANCE = 4.0D;
    private static final double SNAPSHOT_ROLLBACK_DISTANCE_SQUARED = SNAPSHOT_ROLLBACK_DISTANCE * SNAPSHOT_ROLLBACK_DISTANCE;
    private static final Vec3d SNAPSHOT_RENDER_FROM = new Vec3d(-0.35D, -0.35D, -0.35D);
    private static final Vec3d SNAPSHOT_RENDER_TO = new Vec3d(0.35D, 0.35D, 0.35D);

    public final ModulePath pathManager = makePath(Configs.MOV_CONFIG, "path-manager");

    public final DoubleRef autoWriteDistance = doubleBuilder(pathManager.add("auto-write-distance"))
            .defaultValue(80.0D)
            .validator(value -> value > 0.0D)
            .build();

    public final FlagRef render = flagBuilder(pathManager.add("render")).build();

    private FileStorage recordingStorage;
    private String recordingPathFile;
    private RecordPath recordingPath;
    private boolean recordingFlightStarted;
    private RecordSnapshot recordingSnapshot;

    private List<BlockPos> currentPath = List.of();
    private String currentPathFile;
    private boolean currentPathReversed;

    public PathManager() {}

    @Override
    public void registerAll() {
        super.registerAll();
        registerCommandBootstrap(this::bootStrapPathCommand);
        registerListener(Listener.getPreGameTick(), this::onPreTick);
        registerListener(Listener.getServerLeavePoint(), this::onDisconnect);
        registerListener(Listener.getPacketPoint().getChannel(PlayerRespawnS2CPacket.class), this::onRespawn);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRender);
    }

    @Override
    public void unregisterAll() {
        super.unregisterAll();
        finishPath("模块卸载", null);
    }

    public void bootStrapPathCommand(MainCommand mainCommand) {
        TreeSubCommand main = mainCommand.subMainBuilder().name("pathm").build();
        main.subBuilder(SubCommand.taskBuilder())
            .name("start")
            .helper("<path file> 开始录制鞘翅路径")
            .arg(SimpleCommandArgs.argumentBuilder().name("path_file").build())
            .post(e -> e.executor(this::onStart))
            .complete()
            .subBuilder(SubCommand.taskBuilder())
            .name("stop")
            .helper("停止当前路径录制")
            .post(e -> e.executor(this::onStop))
            .complete()
            .subBuilder(SubCommand.taskBuilder())
            .name("load")
            .helper("<path file> <reverse=false> 加载路径")
            .arg(SimpleCommandArgs.argumentBuilder().name("path_file").build())
            .arg(SimpleCommandArgs.argumentBuilder().name("reverse").bool(false).build())
            .post(e -> e.executor(this::onLoad))
            .complete()
            .subBuilder(SubCommand.taskBuilder())
            .name("rerun")
            .helper("重新执行当前路径")
            .post(e -> e.executor(this::onRerun))
            .complete();
    }

    private boolean onStart(CommandExecution context, ArgumentInputStream args, ArgumentReader rest) {
        if (checkNull()) {
            context.sendMessage("&c当前不在游戏内，无法开始路径录制");
            return true;
        }
        if (recordingStorage != null) {
            context.sendMessage("&c当前已经在录制路径: " + recordingPathFile);
            return true;
        }
        String pathFile = normalizePathFile(args.nextNonnullString());
        startPath(pathFile, FileManager.getInstance().getStorage(pathFile));
        context.sendMessage("&a开始等待鞘翅飞行，路径文件: " + pathFile);
        return true;
    }

    private boolean onStop(CommandExecution context, ArgumentInputStream args, ArgumentReader rest) {
        if (recordingStorage == null) {
            context.sendMessage("&c当前没有正在录制的路径");
            return true;
        }
        int size = finishPath("手动停止", context);
        context.sendMessage("&a路径录制已停止，已保存点数: " + size);
        return true;
    }

    private boolean onLoad(CommandExecution context, ArgumentInputStream args, ArgumentReader rest) {
        if (checkNull()) {
            context.sendMessage("&c当前不在游戏内，无法加载路径");
            return true;
        }
        String pathFile = normalizePathFile(args.nextNonnullString());
        boolean reverse = args.nextBoolean();
        FileStorage storage = FileManager.getInstance().getStorage(new File(FileManager.FOLDER, pathFile), true, false);
        if (storage == null) {
            context.sendMessage("&c路径文件不存在: " + pathFile);
            return true;
        }
        storage.read();
        RecordPath loadedPath = readPath(storage);
        if (loadedPath == null || loadedPath.bp().isEmpty()) {
            context.sendMessage("&c路径文件为空或格式不正确: " + pathFile);
            return true;
        }

        String currentServer = CommonUtils.getServerName();
        String currentWorld = currentWorldKey();
        if (!Objects.equals(loadedPath.server(), currentServer)) {
            context.sendMessage("&e路径服务器不一致: 文件=" + loadedPath.server() + " 当前=" + currentServer + "，仍继续加载");
        }
        if (!Objects.equals(loadedPath.world(), currentWorld)) {
            context.sendMessage("&c路径维度不一致: 文件=" + loadedPath.world() + " 当前=" + currentWorld + "，已取消加载");
            return true;
        }

        List<BlockPos> loaded = new ArrayList<>(loadedPath.bp());
        if (reverse) {
            Collections.reverse(loaded);
        }
        currentPath = alignPathToNearest(loaded);
        currentPathFile = pathFile;
        currentPathReversed = reverse;
        context.sendMessage("&a已加载路径: " + pathFile + "，剩余点数: " + currentPath.size());
        return true;
    }

    private boolean onRerun(CommandExecution context, ArgumentInputStream args, ArgumentReader rest) {
        return true;
    }

    private void onPreTick(Event<ClientPlayerEntity> event) {
        if (recordingStorage == null) {
            return;
        }
        try {
            if (checkNull()) {
                finishPath("录制任务意外退出", null);
                return;
            }
            ClientPlayerEntity player = mc.player;
            if (!recordingFlightStarted) {
                if (!player.isFallFlying()) {
                    return;
                }
                startSnapshot(player.getBlockPos());
                recordingFlightStarted = true;
                return;
            }
            if (!player.isFallFlying()) {
                finishPath("鞘翅飞行结束", null);
                return;
            }
            if (player.horizontalCollision || player.verticalCollision) {
                finishPath("玩家碰撞", null);
                return;
            }
            recordCurrentPosition(player.getBlockPos());
        } catch (Throwable e) {
            Debug.info("PathManager recording task failed");
            Debug.info(e);
            finishPath("录制任务意外退出", null);
        }
    }

    private void onDisconnect(Event<Void> event) {
        finishPath("断开连接", null);
    }

    private void onRespawn(Event<PlayerRespawnS2CPacket> event) {
        finishPath("玩家重生", null);
    }

    private void onRender(Event<MatrixStack> event) {
        if (!render.get() || recordingSnapshot == null || checkNull()) {
            return;
        }
        ClientPlayerEntity player = mc.player;
        Vec3d snapshotPos = recordingSnapshot.snapshotPos().toCenterPos();
        Vec3d feetPos = player.getPos();
        RenderUtils.startDrawVirtual(event.context());
        try {
            RenderUtils.drawOutlinedBox(
                    event.context(), snapshotPos.add(SNAPSHOT_RENDER_FROM), snapshotPos.add(SNAPSHOT_RENDER_TO), Color.CYAN);
            RenderUtils.drawLineVirtual(event.context(), snapshotPos, feetPos, Color.CYAN);
        } finally {
            RenderUtils.stopDrawVirtual(event.context());
        }
    }

    private void startPath(String pathFile, FileStorage storage) {
        recordingStorage = storage;
        recordingPathFile = pathFile;
        recordingPath = new RecordPath(new ArrayList<>());
        recordingFlightStarted = false;
        recordingSnapshot = null;
    }

    private void endPath(FileStorage storage) {
        if (storage != null) {
            storage.markDeprecated(true);
        }
        recordingStorage = null;
        recordingPathFile = null;
        recordingPath = null;
        recordingFlightStarted = false;
        recordingSnapshot = null;
    }

    private int finishPath(String reason, CommandExecution context) {
        if (recordingStorage == null) {
            return 0;
        }
        FileStorage storage = recordingStorage;
        String pathFile = recordingPathFile;
        int savedSize = recordingPath == null ? 0 : recordingPath.bp().size();
        try {
            if (recordingFlightStarted && recordingSnapshot != null) {
                addRecordingPoint(recordingSnapshot.lastPosition());
            }
            savedSize = recordingPath == null ? 0 : recordingPath.bp().size();
            writePath(storage, recordingPath == null ? new RecordPath(List.of()) : recordingPath);
            storage.write();
            if (context != null) {
                context.sendMessage("&a路径已保存: " + pathFile + "，原因: " + reason);
            }
        } catch (Throwable e) {
            Debug.info("PathManager failed to save path: " + pathFile);
            Debug.info(e);
            if (context != null) {
                context.sendMessage("&c路径保存失败: " + pathFile);
            }
        } finally {
            endPath(storage);
        }
        return savedSize;
    }

    private void startSnapshot(BlockPos pos) {
        addRecordingPoint(pos);
        recordingSnapshot = new RecordSnapshot(pos);
    }

    private void recordCurrentPosition(BlockPos current) {
        if (recordingSnapshot == null) {
            startSnapshot(current);
            return;
        }

        RecordSnapshot snapshot = recordingSnapshot;
        snapshot.tick();
        if (rollbackSnapshotIfNeeded(snapshot, current)) {
            if (recordingSnapshot != null) {
                recordingSnapshot.updateLastPosition(current);
            }
            return;
        }
        if (current.equals(snapshot.lastPosition())) {
            return;
        }
        if (shouldWriteBeforeCurrent(snapshot, current)) {
            BlockPos lastPosition = snapshot.lastPosition();
            startSnapshot(lastPosition);
            recordingSnapshot.updateLastPosition(current);
            return;
        }
        snapshot.updateLastPosition(current);
    }

    private boolean rollbackSnapshotIfNeeded(RecordSnapshot snapshot, BlockPos current) {
        if (recordingPath == null || recordingPath.bp().size() <= 1 || !snapshot.shouldRollback(current)) {
            return false;
        }
        List<BlockPos> path = recordingPath.bp();
        path.remove(path.size() - 1);
        recordingSnapshot = new RecordSnapshot(path.getLast());
        return true;
    }

    private boolean shouldWriteBeforeCurrent(RecordSnapshot snapshot, BlockPos current) {
        double writeDistance = autoWriteDistance.get();
        if (writeDistance > 0.0D && snapshot.snapshotPos().getSquaredDistance(current) >= writeDistance * writeDistance) {
            return true;
        }
        return !canSee(snapshot.snapshotPos(), current);
    }

    private void addRecordingPoint(BlockPos pos) {
        if (recordingPath == null || pos == null) {
            return;
        }
        List<BlockPos> path = recordingPath.bp();
        if (path.isEmpty() || !path.getLast().equals(pos)) {
            path.add(pos.toImmutable());
        }
    }

    private boolean canSee(BlockPos from, BlockPos to) {
        if (mc.world == null) {
            return false;
        }
        Vec3d start = Vec3d.ofBottomCenter(from);
        Vec3d end = Vec3d.ofBottomCenter(to);
        Vec3d delta = end.subtract(start);
        int steps = Math.max(1, (int) Math.ceil(delta.length() * SAMPLE_PER_BLOCK));
        for (int i = 1; i <= steps; ++i) {
            Vec3d pos = start.add(delta.multiply((double) i / steps));
            if (!mc.world.isSpaceEmpty(makeBodyBox(pos))) {
                return false;
            }
        }
        return true;
    }

    private Box makeBodyBox(Vec3d bottomCenter) {
        return new Box(
                bottomCenter.x - 0.5D,
                bottomCenter.y,
                bottomCenter.z - 0.5D,
                bottomCenter.x + 0.5D,
                bottomCenter.y + 2.0D,
                bottomCenter.z + 0.5D);
    }

    private void writePath(FileStorage storage, RecordPath path) {
        storage.write(RecordPath.CODEC.encodeStart(NbtOps.INSTANCE, path).getOrThrow(), NbtOps.INSTANCE);
    }

    private RecordPath readPath(FileStorage storage) {
        NbtCompound root = storage.asReadOnly(NbtOps.INSTANCE);
        DataResult<RecordPath> decoded = RecordPath.CODEC.parse(NbtOps.INSTANCE, root);
        if (decoded.isError()) {
            Debug.info("PathManager failed to decode path: "
                    + decoded.error().map(error -> error.message()).orElse("未知解码错误"));
            return null;
        }
        return decoded.result().orElse(null);
    }

    private List<BlockPos> alignPathToNearest(List<BlockPos> path) {
        if (checkNull()) {
            return List.copyOf(path);
        }
        BlockPos current = mc.player.getBlockPos();
        int nearest = 0;
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < path.size(); ++i) {
            double distance = path.get(i).getSquaredDistance(current);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = i;
            }
        }
        return List.copyOf(path.subList(nearest, path.size()));
    }

    private String normalizePathFile(String pathFile) {
        String normalized = pathFile.replace('\\', '/');
        if (!normalized.endsWith(".nbt") && !normalized.endsWith(".dat")) {
            normalized += ".nbt";
        }
        return normalized;
    }

    private static String currentWorldKey() {
        return mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
    }

    public List<BlockPos> getCurrentPath() {
        return currentPath;
    }

    public String getCurrentPathFile() {
        return currentPathFile;
    }

    public boolean isCurrentPathReversed() {
        return currentPathReversed;
    }

    public static class RecordPath {
        private static final Codec<List<BlockPos>> POS_CODEC = Codec.LONG.listOf().xmap(
                raw -> raw.stream().map(BlockPos::fromLong).toList(),
                pos -> pos.stream().map(BlockPos::asLong).toList());

        public static final Codec<RecordPath> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        POS_CODEC.fieldOf("pos").forGetter(RecordPath::bp),
                        Codec.STRING.fieldOf("world").forGetter(RecordPath::world),
                        Codec.STRING.fieldOf("server").forGetter(RecordPath::server))
                .apply(instance, RecordPath::new));

        private final List<BlockPos> bp;
        private final String world;
        private final String server;

        public RecordPath(List<BlockPos> bp) {
            this(bp, currentWorldKey(), CommonUtils.getServerName());
        }

        public RecordPath(List<BlockPos> bp, String world, String server) {
            this.bp = new ArrayList<>();
            for (BlockPos pos : bp) {
                if (pos != null) {
                    this.bp.add(pos.toImmutable());
                }
            }
            this.world = world == null ? "" : world;
            this.server = server == null ? "" : server;
        }

        public List<BlockPos> bp() {
            return bp;
        }

        public String world() {
            return world;
        }

        public String server() {
            return server;
        }
    }

    private static class RecordSnapshot {
        private final BlockPos snapshotPos;
        private BlockPos lastPosition;
        private int ticks;

        private RecordSnapshot(BlockPos snapshotPos) {
            this.snapshotPos = snapshotPos.toImmutable();
            this.lastPosition = this.snapshotPos;
        }

        private void tick() {
            ++ticks;
        }

        private boolean shouldRollback(BlockPos current) {
            return ticks > SNAPSHOT_ROLLBACK_TICKS
                    && snapshotPos.getSquaredDistance(current) <= SNAPSHOT_ROLLBACK_DISTANCE_SQUARED;
        }

        private void updateLastPosition(BlockPos pos) {
            lastPosition = pos.toImmutable();
        }

        private BlockPos snapshotPos() {
            return snapshotPos;
        }

        private BlockPos lastPosition() {
            return lastPosition;
        }
    }
}