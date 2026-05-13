package me.matl114.hacks;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.channels.ListenerPoint;
import me.matl114.utils.CommonUtils;
import me.matl114.utils.WorldUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

public class WorldTasks {
    public static void init() {}

    public static Map<ChunkPos, Queue<BooleanSupplier>> pendingUpdateTasks = new ConcurrentHashMap<>();
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    // optimize, do not block main thread
    public static Executor executeThread = Executors.newSingleThreadExecutor();

    public static void onTick(Event<ClientPlayerEntity> eventUpdate) {
        Set<ChunkPos> chunkPoses = new HashSet<>(pendingUpdateTasks.keySet());
        for (var key : chunkPoses) {
            if (!mc.world.getChunkManager().isChunkLoaded(key.x, key.z)) {
                cancelPendingChunkTask(key);
            }
        }
    }

    public static void cancelPendingChunkTask(ChunkPos chunkPos) {
        executeThread.execute(() -> pendingUpdateTasks.remove(chunkPos));
    }

    public static void cancelAllPendingChunkTasks() {
        executeThread.execute(() -> pendingUpdateTasks.clear());
    }

    public static void onWorldChange(Event<World> event) {
        cancelAllPendingChunkTasks();
    }

    public static void onGameExit(Event<Void> event) {
        cancelAllPendingChunkTasks();
    }

    public static void scheduleChunkTask(ChunkPos pos, Runnable runnable, boolean async) {
        BooleanSupplier asyncTask = async
                ? () -> {
                    // note that there is async task running, capturing tasks in the queue
                    pendingUpdateTasks.computeIfAbsent(pos, (v) -> new ConcurrentLinkedDeque<>());
                    CompletableFuture.runAsync(runnable)
                            .thenRunAsync(
                                    () -> {
                                        Queue<BooleanSupplier> runnables = pendingUpdateTasks.get(pos);
                                        if (runnables != null) {
                                            while (!runnables.isEmpty()) {
                                                var task = runnables.poll();
                                                if (task.getAsBoolean()) {
                                                    // wait until next async task finish to pull the rest of the task
                                                    return;
                                                } else {
                                                    continue;
                                                }
                                            }
                                            // all task finished
                                            pendingUpdateTasks.remove(pos);
                                        }
                                    },
                                    executeThread);
                    return true;
                }
                : () -> {
                    runnable.run();
                    return false;
                };
        executeThread.execute(() -> {
            // all "pendingUpdateTasks map" was modified on Main Thread (mc)
            if (pendingUpdateTasks.computeIfPresent(pos, (k, v) -> {
                        v.add(asyncTask);
                        return v;
                    })
                    == null) {
                asyncTask.getAsBoolean();
            }
        });
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
            Chunk chunk = mc.world.getChunkManager().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false);
            if (chunk != null) {
                List<BiPredicate<BlockPos, BlockState>> statePredicates = new ArrayList<>();
                Listener.getWorldScannChunkBlockFilterList().broadcast(statePredicates);
                if (statePredicates.isEmpty()) {
                    return;
                }
                BiPredicate<BlockPos, BlockState> predicate =
                        (b, s) -> statePredicates.stream().anyMatch(s1 -> s1.test(b, s));
                Map<BlockPos, BlockState> stateMap = WorldUtils.scannChunk(chunk, predicate);
                Event<Map<BlockPos, BlockState>> chunkUpdate = new Event<>(stateMap, false, false, chunkPos);
                Listener.getWorldScannChunkResult().handleValue(chunkUpdate);
            }
        }
    }

    public static void onPostBlockStateUpdate(Event<BlockUpdateS2CPacket> updateS2CPacketEvent) {
        if (mc.world == null || mc.player == null) return;
        if (shouldExecuteWorldScan()) {
            BlockUpdateS2CPacket blockUpdateS2CPacket = updateS2CPacketEvent.context();
            BlockPos blockPos = blockUpdateS2CPacket.getPos();
            ChunkPos chunkPos = CommonUtils.toChunk(blockPos);
            scheduleChunkTask(chunkPos, () -> onSingleBlockValueChange(blockPos.toImmutable()), false);
        }
    }

    private static void onSingleBlockValueChange(BlockPos pos) {
        // (checkNull()) return;
        ChunkPos chunkPos = CommonUtils.toChunk(pos);
        if (mc.world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
            BlockState state = mc.world.getBlockState(pos);
            Event<BlockState> stateUpdate = new Event<>(state, false, false, pos, chunkPos);
            Listener.getWorldScannBlockResult().handleValue(stateUpdate);
        }
    }

    public static void onChunkUpdate(Event<ChunkDataS2CPacket> chunkDataS2CPacketEvent) {
        if (mc.world == null || mc.player == null) return;
        if (shouldExecuteWorldScan()) {
            ChunkDataS2CPacket packet = chunkDataS2CPacketEvent.context();
            Chunk updatedChunk = mc.world.getChunk(packet.getChunkX(), packet.getChunkZ(), ChunkStatus.FULL, false);
            if (updatedChunk != null) {
                ChunkPos chunkPos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());
                // because of chunk update, cancel all the last
                cancelPendingChunkTask(chunkPos);
                scheduleChunkTask(chunkPos, () -> onChunkReScann(chunkPos), true);
            }
        }
    }

    public static void onChunkDeltaUpdate(Event<ChunkDeltaUpdateS2CPacket> chunkDeltaUpdateS2CPacketEvent) {
        if (mc.world == null || mc.player == null) return;
        if (shouldExecuteWorldScan()) {
            ChunkDeltaUpdateS2CPacket packet = chunkDeltaUpdateS2CPacketEvent.context();
            ChunkSectionPos chunkSecPos = packet.sectionPos;
            // Chunk updateChunk = mc.world.getChunk(chunkPos.getX(), chunkPos.getZ(), ChunkStatus.FULL, false);
            ChunkPos chunkPos = new ChunkPos(chunkSecPos.getX(), chunkSecPos.getZ());
            scheduleChunkTask(
                    chunkPos,
                    () -> {
                        packet.visitUpdates((bp, bs) -> {
                            onSingleBlockValueChange(bp.toImmutable());
                        });
                    },
                    false);
        }
    }

    private static <W> void registerListener(ListenerPoint<W> listener, Consumer<W> handler) {
        listener.registerHandler(handler);
    }

    static {
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(BlockUpdateS2CPacket.class),
                WorldTasks::onPostBlockStateUpdate);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(ChunkDataS2CPacket.class), WorldTasks::onChunkUpdate);
        registerListener(Listener.getWorldSwitchPoint(), WorldTasks::onWorldChange);
        registerListener(Listener.getServerLeavePoint(), WorldTasks::onGameExit);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(ChunkDeltaUpdateS2CPacket.class),
                WorldTasks::onChunkDeltaUpdate);
        registerListener(Listener.getPostGameTick(), WorldTasks::onTick);
    }
}
