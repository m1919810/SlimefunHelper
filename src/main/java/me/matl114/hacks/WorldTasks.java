package me.matl114.hacks;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.channels.ListenerPoint;
import me.matl114.events.impl.BlockUpdate;
import me.matl114.utils.CommonUtils;
import me.matl114.utils.WorldUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

public class WorldTasks {
    public static void init() {}

    public static Map<ChunkPos, CompletableFuture<Void>> pendingUpdateTasks = new ConcurrentHashMap<>();
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    // optimize, do not block main thread
    private static final ExecutorService scanExecutor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() / 2,
            Runtime.getRuntime().availableProcessors() / 2,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(2500),
            new ThreadPoolExecutor.CallerRunsPolicy());
    private static final Executor processExecutor = Executors.newSingleThreadExecutor();
    static int tickCounter = 0;

    public static void onTick(Event<ClientPlayerEntity> eventUpdate) {
        if (++tickCounter > 5) {
            tickCounter = 0;
            Set<ChunkPos> chunkPoses = pendingUpdateTasks.keySet();
            List<ChunkPos> removal = new ArrayList<>(32);
            for (var key : chunkPoses) {
                if (!mc.world.getChunkManager().isChunkLoaded(key.x, key.z)) {
                    removal.add(key);
                }
            }
            for (ChunkPos chunkPos : removal) {
                cancelPendingChunkTask(chunkPos);
            }
        }
    }

    public static void cancelPendingChunkTask(ChunkPos chunkPos) {
        mc.execute(() -> pendingUpdateTasks.remove(chunkPos));
    }

    public static void cancelAllPendingChunkTasks() {
        mc.execute(() -> pendingUpdateTasks.clear());
    }

    public static void onWorldChange(Event<World> event) {
        cancelAllPendingChunkTasks();
    }

    public static void onGameExit(Event<Void> event) {
        cancelAllPendingChunkTasks();
    }

    public static void scheduleChunkTask(ChunkPos pos, Runnable runnable, boolean async) {
        if (async) {
            pendingUpdateTasks.compute(pos, (v, t) -> {
                if (t == null) {
                    return CompletableFuture.runAsync(runnable, scanExecutor);
                } else {
                    return t.thenRunAsync(runnable, scanExecutor);
                }
            });
        } else {
            pendingUpdateTasks.compute(pos, (v, t) -> {
                if (t == null) {
                    return CompletableFuture.runAsync(runnable, mc);
                } else {
                    return t.thenRunAsync(runnable, mc);
                }
            });
        }
    }

    public static boolean shouldExecuteWorldScan() {
        if (!Listener.getPreWorldScannListener().isEmpty()) {
            Event<Boolean> requestEvent = new Event<>(false, false, true);
            Listener.getPreWorldScannListener().handleValue(requestEvent);
            return requestEvent.context == Boolean.TRUE;
        }
        return false;
    }

    public static void restartWorldScanner() {
        Listener.getResetWorldScannListener().broadcast(null);
        cancelAllPendingChunkTasks();
        if (mc.player == null || mc.world == null) {
            return;
        }
        refreshAllChunks();
    }

    public static void refreshAllChunks() {
        if (shouldExecuteWorldScan()) {
            for (Chunk chunk : CommonUtils.chunks(false)) {
                ChunkPos chunkPos = chunk.getPos();
                scheduleChunkTask(chunkPos, () -> onChunkReScann(chunkPos), true);
            }
        }
    }

    private static void onChunkReScann(ChunkPos chunkPos) {
        if (mc.player == null || mc.world == null) return;
        if (mc.world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
            Chunk chunk = mc.world.getChunkManager().getWorldChunk(chunkPos.x, chunkPos.z);
            if (chunk != null) {
                List<BiPredicate<BlockPos, BlockState>> statePredicates = new ArrayList<>();
                Listener.getWorldScannChunkBlockFilterList().broadcast(statePredicates);
                if (statePredicates.isEmpty()) {
                    return;
                }
                BiPredicate<BlockPos, BlockState> predicate =
                        (b, s) -> statePredicates.stream().anyMatch(s1 -> s1.test(b, s));
                Map<BlockPos, BlockState> stateMap = WorldUtils.scannChunk(chunk, predicate);
                processExecutor.execute(() -> {
                    Event<Map<BlockPos, BlockState>> chunkUpdate = new Event<>(stateMap, false, false, chunkPos);
                    Listener.getWorldScannChunkResult().handleValue(chunkUpdate);
                });
            }
        }
    }

    private static void onBlockStateUpdate(Event<BlockUpdate> event) {
        if (shouldExecuteWorldScan()) {
            BlockPos pos = event.context.pos().toImmutable();
            ChunkPos chunkPos = new ChunkPos(pos);
            scheduleChunkTask(chunkPos, () -> onSingleBlockValueChange(pos), true);
        }
    }

    private static void onSingleBlockValueChange(BlockPos pos) {
        // (checkNull()) return;
        ChunkPos chunkPos = CommonUtils.toChunk(pos);
        if (mc.world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
            BlockState state = mc.world.getBlockState(pos);
            scanExecutor.execute(() -> {
                Event<BlockState> stateUpdate = new Event<>(state, false, false, pos, chunkPos);
                Listener.getWorldScannBlockResult().handleValue(stateUpdate);
            });
        }
    }

    public static void onChunkUpdate(Event<ChunkPos> chunkDataS2CPacketEvent) {
        if (mc.world == null || mc.player == null) return;
        if (shouldExecuteWorldScan()) {
            ChunkPos pos = chunkDataS2CPacketEvent.context;
            Chunk updatedChunk = mc.world.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
            if (updatedChunk != null) {
                // because of chunk update, cancel all the last
                cancelPendingChunkTask(pos);
                scheduleChunkTask(pos, () -> onChunkReScann(pos), true);
            }
        }
    }

    private static <W> void registerListener(ListenerPoint<W> listener, Consumer<W> handler) {
        listener.registerHandler(handler);
    }

    static {
        registerListener(Listener.getBlockUpdateListener(), WorldTasks::onBlockStateUpdate);
        registerListener(Listener.getChunkUpdateListener(), WorldTasks::onChunkUpdate);
        registerListener(Listener.getWorldSwitchPoint(), WorldTasks::onWorldChange);
        registerListener(Listener.getServerLeavePoint(), WorldTasks::onGameExit);
        registerListener(Listener.getPostGameTick(), WorldTasks::onTick);
    }
}
