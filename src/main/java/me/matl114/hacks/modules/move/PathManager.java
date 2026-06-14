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
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.file.FileStorage;
import me.matl114.utils.*;
import me.matl114.utils.commands.CommandUtils;
import me.matl114.utils.commands.commandGroup.CommandContext;
import me.matl114.utils.commands.commandGroup.SubCommand;
import me.matl114.utils.commands.commandGroup.TreeSubCommand;
import me.matl114.utils.commands.params.ArgumentInputStream;
import me.matl114.utils.commands.params.ArgumentReader;
import me.matl114.utils.commands.params.SimpleCommandArgs;
import me.matl114.utils.commands.params.api.CommandExecution;
import me.matl114.utils.commands.params.api.TabResult;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public class PathManager extends BaseModule {
    public static PathManager INSTANCE;

    private static final int SNAPSHOT_ROLLBACK_TICKS = 20;
    private static final double SNAPSHOT_ROLLBACK_DISTANCE = 3.0D;
    private static final double SNAPSHOT_ROLLBACK_DISTANCE_SQUARED =
            SNAPSHOT_ROLLBACK_DISTANCE * SNAPSHOT_ROLLBACK_DISTANCE;
    private static final Vec3d SNAPSHOT_RENDER_FROM = new Vec3d(-0.25D, -0.25D, -0.25D);
    private static final Vec3d SNAPSHOT_RENDER_TO = new Vec3d(0.25D, 0.25D, 0.25D);
    private static final String PATH_PATH = "path_storage";

    public final ModulePath pathManager = makePath(Configs.MOV_CONFIG, "path-manager");

    public final DoubleRef autoWriteDistance = doubleBuilder(pathManager.add("auto-write-distance"))
            .defaultValue(80.0D)
            .validator(value -> value > 0.0D)
            .build();

    public final FlagRef render = flagBuilder(pathManager.add("render")).build();

    public final EnumRef<Mode> rerunMode = builder(pathManager.add("rerun-mode"), Mode.class)
            .defaultValue(Mode.ELYTRA_FLIGHT)
            .build();

    private FileStorage recordingStorage;
    private String recordingPathFile;
    private RecordPath recordingPath;
    private boolean recordingFlightStarted;
    private RecordSnapshot recordingSnapshot;

    private List<BlockPos> currentPath = List.of();
    private String currentPathFile;
    private boolean currentPathReversed;
    public File SAVE_FILE = FileManager.getInstance().getAndCreateFile(PATH_PATH);

    public PathManager() {}

    @Override
    public void registerAll() {
        super.registerAll();
        registerCommandBootstrap(this::bootStrapPathCommand);
        registerListener(Listener.getPreGameTick(), this::onPreTick);
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
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
                .name("restart")
                .helper("<path file> 继续录制之前录制的路径")
                .arg(SimpleCommandArgs.argumentBuilder()
                        .name("path_file")
                        .tabCompletor(TabResult.ofStreamSupplier(CommandUtils.fileSupplier(SAVE_FILE, (ex) -> {
                            return ex.endsWith(".nbt") || ex.endsWith(".dat");
                        })))
                        .build())
                .post(e -> e.executor(this::onReStart))
                .complete()
                .subBuilder(SubCommand.treeBuilder())
                .name("modify")
                .helper("<type> 修改当前加载中的路径")
                .post(s -> s.subBuilder(SubCommand.taskBuilder())
                        .name("push")
                        .helper(" 加入当前坐标点")
                        .post(s1 -> s1.executor(CommandContext.execute(this::onPush)))
                        .complete()
                        .subBuilder(SubCommand.taskBuilder())
                        .name("pop")
                        .helper(" 移除上一个坐标点")
                        .post(s1 -> s1.executor(CommandContext.execute(this::onPop)))
                        .complete()
                        .subBuilder(SubCommand.taskBuilder())
                        .name("pause")
                        .helper(" 暂停当前记录")
                        .post(s1 -> s1.executor(CommandContext.execute(this::onPause)))
                        .complete()
                        .subBuilder(SubCommand.taskBuilder())
                        .name("continue")
                        .helper(" 继续记录")
                        .post(s1 -> s1.executor(CommandContext.execute(this::onContinue)))
                        .complete())
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("stop")
                .helper("停止当前路径录制")
                .post(e -> e.executor(this::onStop))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("load")
                .helper("<path file> <reverse=false> 加载路径")
                .arg(SimpleCommandArgs.argumentBuilder()
                        .name("path_file")
                        .tabCompletor(TabResult.ofStreamSupplier(CommandUtils.fileSupplier(SAVE_FILE, (ex) -> {
                            return ex.endsWith(".nbt") || ex.endsWith(".dat");
                        })))
                        .build())
                .arg(SimpleCommandArgs.argumentBuilder()
                        .name("reverse")
                        .bool(false)
                        .build())
                .post(e -> e.executor(this::onLoad))
                .complete()
                .subBuilder(SubCommand.taskBuilder())
                .name("rerun")
                .helper("重新执行当前路径")
                .post(e -> e.executor(this::onRerun))
                .complete();
    }

    boolean pauseRecord = false;

    private boolean onStart(CommandExecution context, ArgumentInputStream args, ArgumentReader rest) {
        if (checkNull()) {
            context.sendMessage("&c当前不在游戏内，无法开始路径录制");
            return true;
        }
        if (recordingStorage != null) {
            context.sendMessage("&c当前已经在录制路径: " + recordingPathFile);
            return true;
        }
        String pathFile = args.nextNonnullString();
        if (!pathFile.endsWith(".nbt")) {
            pathFile = pathFile + ".nbt";
        }
        startPath(pathFile, FileManager.getInstance().getStorage(new File(SAVE_FILE, pathFile)));
        context.sendMessage("&a开始等待鞘翅飞行，路径文件: " + pathFile);
        return true;
    }

    private boolean onReStart(CommandExecution context, ArgumentInputStream args, ArgumentReader rest) {
        if (checkNull()) {
            context.sendMessage("&c当前不在游戏内，无法开始路径录制");
            return true;
        }
        if (recordingStorage != null) {
            context.sendMessage("&c当前已经在录制路径: " + recordingPathFile);
            return true;
        }
        String pathFile = args.nextNonnullString();
        FileStorage storage = FileManager.getInstance().getStorage(new File(SAVE_FILE, pathFile), true, false);
        if (storage == null) {
            context.sendMessage("&c路径文件不存在: " + pathFile);
            return true;
        }
        var loadedPath = readPath(storage);
        if (loadedPath == null || loadedPath.bp().isEmpty()) {
            context.sendMessage("&c路径文件为空或格式不正确: " + pathFile);
            storage.markDeprecated(true);
            return true;
        }
        String currentServer = CommonUtils.getServerName();
        String currentWorld = currentWorldKey();
        if (!Objects.equals(loadedPath.server(), currentServer)) {
            context.sendMessage("&e路径服务器不一致: 文件=" + loadedPath.server() + " 当前=" + currentServer + "，仍继续加载");
        }
        if (!Objects.equals(loadedPath.world(), currentWorld)) {
            context.sendMessage("&c路径维度不一致: 文件=" + loadedPath.world() + " 当前=" + currentWorld + "，已取消加载");
            storage.markDeprecated(true);
            return true;
        }
        restartPath(pathFile, storage, loadedPath.bp());
        context.sendMessage("&a已载入历史路线记录，路径文件: " + pathFile);
        onPause(context);
        return true;
    }

    private void onPush(CommandExecution context) {
        if (recordingStorage == null || recordingPath == null) {
            context.sendMessage("&c当前没有正在录制的路径");
            return;
        }
        startSnapshot(mc.player.getBlockPos());
        context.sendMessage("&a当前位置以添加");
        return;
    }

    public void onPop(CommandExecution context) {
        if (recordingStorage == null || recordingPath == null) {
            context.sendMessage("&c当前没有正在录制的路径");
            return;
        }
        if (recordingPath.bp.isEmpty()) {
            context.sendMessage("&c当前没有多余的路径点");
            return;
        }

        recordingPath.bp.remove(recordingPath.bp.size() - 1);
        restartSnapshot();
        context.sendMessage("&a当前位置以添加");
        return;
    }

    public void onPause(CommandExecution context) {
        if (recordingStorage == null || recordingPath == null) {
            context.sendMessage("&c当前没有正在录制的路径");
            return;
        }
        pauseRecord = true;
        context.sendMessage(Text.literal("&a当前记录已暂停, 输入!!pathm modify continue (点击该文本以补全)继续录制")
                .styled(s -> s.withClickEvent(ChatUtils.getSuggestCommand("/!!pathm modify continue"))));
    }

    public void onContinue(CommandExecution context) {
        if (recordingStorage == null || recordingPath == null) {
            context.sendMessage("&c当前没有正在录制的路径");
            return;
        }
        pauseRecord = false;
        context.sendMessage("&a当前记录已继续");
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
        String pathFile = args.nextNonnullString();
        boolean reverse = args.nextBoolean();
        RecordPath loadedPath;
        try (FileStorage storage = FileManager.getInstance().getStorage(new File(SAVE_FILE, pathFile), true, false)) {
            if (storage == null) {
                context.sendMessage("&c路径文件不存在: " + pathFile);
                return true;
            }
            storage.read();
            loadedPath = readPath(storage);
        }

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
        if (recordingStorage != null) {
            if (checkNull()) {
                finishPath("录制任务意外退出", null);
            } else {
                ClientPlayerEntity player = mc.player;
                if (!recordingFlightStarted) {
                    if (!player.isFallFlying()) {
                        return;
                    }
                    startSnapshot(player.getBlockPos());
                    recordingFlightStarted = true;
                    return;
                }
                recordCurrentPosition(player.getBlockPos());
            }
        }
    }

    private void onWorldSwitch(Event<World> event) {
        finishPath("切换世界", null);
    }

    private void onDisconnect(Event<Void> event) {
        finishPath("断开连接", null);
    }

    private void onRespawn(Event<PlayerRespawnS2CPacket> event) {
        finishPath("玩家重生", null);
    }

    private static final int POSITION_FLAG = VRender.createTextPositionFlag(0, 1);

    private void onRender(Event<MatrixStack> event) {
        if (!render.get() || recordingSnapshot == null || checkNull()) {
            return;
        }
        ClientPlayerEntity player = mc.player;
        Vec3d snapshotPos = recordingSnapshot.snapshotPos().toCenterPos();
        Vec3d feetPos = RenderUtils.getCameraPos();
        RenderUtils.startDrawVirtual(event.context());
        try {
            RenderUtils.drawOutlinedBox(
                    event.context(),
                    snapshotPos.add(SNAPSHOT_RENDER_FROM),
                    snapshotPos.add(SNAPSHOT_RENDER_TO),
                    Color.CYAN);
            Vec3d delta = snapshotPos.subtract(feetPos);
            RenderUtils.drawLineVirtualCameraCoord(
                    event.context(), delta, RenderUtils.getTracerOrigin(0.0F), Color.CYAN);
            var stack = event.context;
            stack.push();
            stack.translate(delta.x, delta.y + 0.25, delta.z);
            // title的高度是9 我们希望这个9在 0.75 ~ 1.0之间
            // 我希望他看向我
            float scaling = (float) delta.length();
            stack.multiply(RenderUtils.getBillboardRotation(DisplayEntity.BillboardMode.CENTER, 0, 0));
            stack.scale(0.002F * scaling, 0.002F * scaling, 1);
            VRender.getInstance()
                    .drawTextCameraCoord(
                            Text.literal("距离: %.1f".formatted(scaling)).asOrderedText(),
                            stack,
                            Vec3d.ZERO,
                            VRender.createTextPositionFlag(0, 1),
                            Color.WHITE,
                            VRender.DEFAULT_TEXT);

            stack.pop();
            Vec3d lastPos = snapshotPos;
            if (recordingPath != null) {
                var lst = recordingPath.bp();
                var size = lst.size();
                for (var i = size - 1; i >= 0; --i) {
                    var bbb = lst.get(i);

                    Vec3d ppp = bbb.toCenterPos();
                    RenderUtils.drawOutlinedBox(
                            event.context(), ppp.add(SNAPSHOT_RENDER_FROM), ppp.add(SNAPSHOT_RENDER_TO), Color.CYAN);
                    RenderUtils.drawLineVirtual(event.context(), ppp, lastPos, Color.CYAN);
                    lastPos = ppp;
                    if (bbb.getSquaredDistance(feetPos) > MathUtils.s2(autoWriteDistance.get() * 2)) {
                        break;
                    }
                }
            }
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

    private void restartPath(String pathFile, FileStorage storage, List<BlockPos> blockPos) {
        recordingStorage = storage;
        recordingPathFile = pathFile;
        recordingPath = new RecordPath(new ArrayList<>(blockPos));

        recordingFlightStarted = true;
        recordingSnapshot = null;
        restartSnapshot();
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
        if (recordingPath == null || pos == null) {
            return;
        }
        List<BlockPos> path = recordingPath.bp();
        if (path.isEmpty() || !path.getLast().equals(pos)) {
            path.add(pos.toImmutable());
        }
        //
        if (recordingStorage != null) {
            recordingStorage.write(RecordPath.CODEC, recordingPath);
        }
        recordingSnapshot = new RecordSnapshot(pos);
    }

    private void restartSnapshot() {
        if (recordingPath != null && !recordingPath.bp.isEmpty()) {
            BlockPos pos = recordingPath.bp().get(recordingPath.bp.size() - 1);
            recordingSnapshot = new RecordSnapshot(pos);
            recordingSnapshot.lastPosition = mc.player.getBlockPos();
        } else {
            startSnapshot(mc.player.getBlockPos());
        }
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
        BlockPos previousPoint = path.get(path.size() - 2); // 回滚后会变成最后一个点
        if (!canSee(previousPoint, current)) {
            // 上一个点与当前位置不可见，不回滚（避免丢失关键转弯点）
            return false;
        }
        path.remove(path.size() - 1);
        recordingSnapshot = new RecordSnapshot(path.getLast());
        return true;
    }

    private boolean shouldWriteBeforeCurrent(RecordSnapshot snapshot, BlockPos current) {
        if (pauseRecord) {
            return false;
        }
        double writeDistance = autoWriteDistance.get();
        double disSqr = snapshot.snapshotPos().getSquaredDistance(current);
        if (disSqr < MathUtils.s2(writeDistance)) {
            return !canSee(snapshot.snapshotPos(), current);
        }
        return true;
    }

    private boolean canSee(BlockPos from, BlockPos to) {
        if (mc.world == null || mc.player == null) {
            return false;
        }
        if (from.getSquaredDistance(to) > MathUtils.s2(autoWriteDistance.get() + 10)) {
            return false;
        }
        Vec3d start = Vec3d.ofBottomCenter(from);
        Box box = makeBodyBox(Vec3d.ofBottomCenter(to));
        Vec3d[] corners = new Vec3d[] {
            new Vec3d(box.minX, box.minY, box.minZ),
            new Vec3d(box.maxX, box.minY, box.minZ),
            new Vec3d(box.minX, box.maxY, box.minZ),
            new Vec3d(box.maxX, box.maxY, box.minZ),
            new Vec3d(box.minX, box.minY, box.maxZ),
            new Vec3d(box.maxX, box.minY, box.maxZ),
            new Vec3d(box.minX, box.maxY, box.maxZ),
            new Vec3d(box.maxX, box.maxY, box.maxZ)
        };
        for (Vec3d corner : corners) {
            HitResult result = mc.world.raycast(new RaycastContext(
                    start, corner, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            // do not raycast entities
            if (result.getType() == HitResult.Type.BLOCK) {
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
        DataResult<?> encoded = storage.write(RecordPath.CODEC, path);
        if (encoded.isError()) {
            throw new IllegalArgumentException(
                    encoded.error().map(error -> error.message()).orElse("未知编码错误"));
        }
    }

    private RecordPath readPath(FileStorage storage) {
        DataResult<RecordPath> decoded = storage.read(RecordPath.CODEC);
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

    public enum Mode implements ConfigEnum {
        BARITONE,
        ELYTRA_FLIGHT;

        @Override
        public String getConfigEnumType() {
            return "path_manager_flight_mode";
        }
    }

    public static class RecordPath {
        private static final Codec<List<BlockPos>> POS_CODEC =
                Codec.LONG.xmap(BlockPos::fromLong, BlockPos::asLong).listOf();

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
