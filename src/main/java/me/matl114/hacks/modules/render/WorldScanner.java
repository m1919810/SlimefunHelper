package me.matl114.hacks.modules.render;

import static me.matl114.utils.ColorUtils.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.config.*;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.CommonUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.WorldUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VRender;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

public class WorldScanner extends BaseModule {
    public static final String[] SEARCH_ENABLE = new String[] {"detect-block", "search", "enable"};
    public static final String[] SEARCH_ENABLE_TYPE = new String[] {"detect-block", "search", "search-type"};

    public static final String[] SEARCH_COLOR = new String[] {"detect-block", "search", "search-color"};

    public static final String[] SEARCH_RADIUS = new String[] {"detect-block", "search", "search-radius"};

    public static final String[] SEARCH_ESP = new String[] {"detect-block", "search", "esp-option"};

    public WorldScanner() {
        bindFlag(enable);
    }

    public FlagRef enable = flagBuilder(Configs.RENDER_CONFIG, SEARCH_ENABLE).build();

    public NBTRef<RegistryRegex<Block>> typeFilter = builder(
                    Configs.RENDER_CONFIG, SEARCH_ENABLE_TYPE, RegistryRegex.<Block>parameter())
            .defaultValue(
                    new RegistryRegex<>(new Regex("^(.*_portal|end_gateway|end_portal_frame)$"), Registries.BLOCK))
            .updateListener(this::updateBlockTypeFilter)
            .build();

    public NBTRef<EntryPrimitiveMap<Block, TextColor>> color = builder(
                    Configs.RENDER_CONFIG, SEARCH_COLOR, EntryPrimitiveMap.<Block, TextColor>parameter())
            .defaultValue(new EntryPrimitiveMap<>(
                    Registries.BLOCK,
                    NBTTypes.COLOR_TYPE,
                    Map.of(
                            Blocks.NETHER_PORTAL, color(Formatting.RED),
                            Blocks.END_PORTAL, color(Formatting.YELLOW),
                            Blocks.END_PORTAL_FRAME, color(Formatting.BLUE),
                            Blocks.END_GATEWAY, color(Formatting.YELLOW),
                            Blocks.COMMAND_BLOCK, color(Formatting.WHITE)),
                    color(Formatting.GREEN)))
            .build();

    public IntRef distanceChunk = builder(Configs.RENDER_CONFIG, SEARCH_RADIUS, IntRef.TYPE)
            .defaultValue(12)
            .build();

    public NBTRef<TracingOption> option = builder(Configs.RENDER_CONFIG, SEARCH_ESP, TracingOption.class)
            .defaultValue(new TracingOption(true, false))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onTick);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(BlockUpdateS2CPacket.class),
                this::onPostBlockStateUpdate);
        registerListener(Listener.getPacketPostHandlePoint().getChannel(ChunkDataS2CPacket.class), this::onChunkUpdate);
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldChange);
        registerListener(Listener.getServerDisconnectPoint(), this::onGameExit);
        registerListener(RenderListener.getRenderLayerTasks(), this::onRender);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(ChunkDeltaUpdateS2CPacket.class),
                this::onChunkDeltaUpdate);
    }

    public void onEnableModule() {
        super.onEnableModule();
        if (!checkNull()) {
            pendingRefreshWhenInGame = true;
        }
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        cancelAllPendingChunkTasks();
    }

    public void onWorldChange(Event<World> event) {
        cancelAllPendingChunkTasks();
    }

    public void onGameExit(Event<Void> event) {
        cancelAllPendingChunkTasks();
    }

    boolean pendingRefreshWhenInGame = true;
    public Set<Block> currentSearchingSet = new HashSet<>();
    public Map<ChunkPos, Map<BlockPos, BlockState>> currentSearchingResult = new ConcurrentHashMap<>();
    public Map<ChunkPos, Queue<BooleanSupplier>> pendingUpdateTasks = new ConcurrentHashMap<>();

    public void restartWorldScanner() {
        currentSearchingResult.clear();
        cancelAllPendingChunkTasks();
        if (checkNull()) return;
        refreshAllChunks();
    }

    public void refreshAllChunks() {
        RegistryRegex<Block> regex = typeFilter.get();
        for (Chunk chunk : CommonUtils.chunks(false)) {
            ChunkPos chunkPos = chunk.getPos();
            scheduleChunkTask(chunkPos, () -> onChunkReScann(chunkPos, regex), true);
        }
    }

    public void updateBlockTypeFilter(RegistryRegex<Block> typeFilter) {
        Set<Block> update = typeFilter.getFilterValue();
        if (!Objects.equals(update, currentSearchingSet)) {
            currentSearchingSet = update;
            if (!checkNull()) {
                pendingRefreshWhenInGame = true;
            }
        }
    }

    public void validateAndClearSearchResult(boolean strict) {
        if (checkNull()) return;
        Set<ChunkPos> blocks = new HashSet<>(currentSearchingResult.keySet());
        for (var key : blocks) {
            Chunk chunk = mc.world.getChunkManager().getChunk(key.x, key.z, ChunkStatus.FULL, false);
            if (chunk == null) {
                currentSearchingResult.remove(key);
            } else {
                Map<BlockPos, BlockState> stateMap = currentSearchingResult.get(key);
                if (stateMap == null || stateMap.isEmpty()) {
                    currentSearchingResult.remove(key);
                } else {
                    if (strict) {
                        // should we add this?
                    }
                }
            }
        }
        Set<ChunkPos> chunkPoses = new HashSet<>(pendingUpdateTasks.keySet());
        for (var key : chunkPoses) {
            if (!mc.world.getChunkManager().isChunkLoaded(key.x, key.z)) {
                cancelPendingChunkTask(key);
            }
        }
    }

    public void scheduleChunkTask(ChunkPos pos, Runnable runnable, boolean async) {
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
                                    mc);
                    return true;
                }
                : () -> {
                    runnable.run();
                    return false;
                };
        mc.execute(() -> {
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

    public void cancelPendingChunkTask(ChunkPos chunkPos) {
        mc.execute(() -> pendingUpdateTasks.remove(chunkPos));
    }

    public void cancelAllPendingChunkTasks() {
        mc.execute(() -> pendingUpdateTasks.clear());
    }

    private void onSingleBlockValueChange(BlockPos pos) {
        if (checkNull()) return;
        ChunkPos chunkPos = CommonUtils.toChunk(pos);
        if (mc.world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
            BlockState state = mc.world.getBlockState(pos);
            boolean accept = currentSearchingSet.contains(state.getBlock());
            if (accept) {
                Map<BlockPos, BlockState> stateMap =
                        currentSearchingResult.computeIfAbsent(chunkPos, k -> new ConcurrentHashMap<>());
                stateMap.put(pos, state);
            } else {
                Map<BlockPos, BlockState> stateMap = currentSearchingResult.get(chunkPos);
                if (stateMap != null) {
                    stateMap.remove(pos);
                    if (stateMap.isEmpty()) {
                        currentSearchingResult.remove(chunkPos);
                    }
                }
            }
        }
    }

    private void onChunkReScann(ChunkPos chunkPos, RegistryRegex<Block> oldRegex) {
        if (checkNull()) return;
        if (mc.world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
            Chunk chunk = mc.world.getChunkManager().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false);
            if (chunk != null) {
                BiPredicate<BlockPos, BlockState> predicate = (b, s) -> oldRegex.test(s.getBlock());
                Map<BlockPos, BlockState> stateMap = WorldUtils.scannChunk(chunk, predicate);
                if (typeFilter.get() == oldRegex
                        || typeFilter.get().getFilterValue().equals(oldRegex.getFilterValue())) {
                    // accepted
                    currentSearchingResult.put(chunkPos, new ConcurrentHashMap<>(stateMap));
                }
            }
        }
    }

    public void onPostBlockStateUpdate(Event<BlockUpdateS2CPacket> updateS2CPacketEvent) {
        if (checkNull()) return;
        if (enable.get()) {
            BlockUpdateS2CPacket blockUpdateS2CPacket = updateS2CPacketEvent.context();
            BlockPos blockPos = blockUpdateS2CPacket.getPos();
            ChunkPos chunkPos = CommonUtils.toChunk(blockPos);
            scheduleChunkTask(chunkPos, () -> onSingleBlockValueChange(blockPos.toImmutable()), false);
        }
    }

    public void onChunkUpdate(Event<ChunkDataS2CPacket> chunkDataS2CPacketEvent) {
        if (checkNull()) return;
        if (enable.get()) {
            ChunkDataS2CPacket packet = chunkDataS2CPacketEvent.context();
            Chunk updatedChunk = mc.world.getChunk(packet.getChunkX(), packet.getChunkZ(), ChunkStatus.FULL, false);
            if (updatedChunk != null) {
                ChunkPos chunkPos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());
                RegistryRegex<Block> regex = typeFilter.get();
                // because of chunk update, cancel all the last
                cancelPendingChunkTask(chunkPos);
                scheduleChunkTask(chunkPos, () -> onChunkReScann(chunkPos, regex), true);
            }
        }
    }

    public void onChunkDeltaUpdate(Event<ChunkDeltaUpdateS2CPacket> chunkDeltaUpdateS2CPacketEvent) {
        if (checkNull()) return;
        if (enable.get()) {
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

    int resultUpdate = 0;
    List<IndexEntry<Box>> boxes = new ArrayList<>();

    public void onTick(Event<ClientPlayerEntity> event) {
        if (!checkNull()
                && pendingRefreshWhenInGame
                && (mc.currentScreen == null || mc.currentScreen instanceof HandledScreen<?>)) {
            // do not refresh when config is open or when player open exit menu
            pendingRefreshWhenInGame = false;
            restartWorldScanner();
        }
        if (enable.get()) {
            if (resultUpdate < 50) {
                resultUpdate++;
                validateAndClearSearchResult(false);
            } else {
                resultUpdate = 0;
                validateAndClearSearchResult(true);
            }
            if (!boxes.isEmpty()) {
                boxes = new ArrayList<>();
            }
            if (!checkNull()) {
                if (!currentSearchingResult.isEmpty()) {
                    int radius = distanceChunk.get();
                    ChunkPos chunkPos = mc.player.getChunkPos();
                    Set<ChunkPos> chunkKeys = new HashSet<>(currentSearchingResult.keySet());
                    for (ChunkPos chunkKey : chunkKeys) {
                        if (Math.abs(chunkPos.x - chunkKey.x) <= radius
                                && Math.abs(chunkPos.z - chunkKey.z) <= radius) {
                            Map<BlockPos, BlockState> stateMap = currentSearchingResult.get(chunkKey);
                            for (var entry : stateMap.entrySet()) {
                                BlockState state = entry.getValue();
                                TextColor color = this.color.get().getOrDefault(state.getBlock());
                                if (color != null) {
                                    VoxelShape shape = entry.getValue().getOutlineShape(mc.world, entry.getKey());
                                    if (!shape.isEmpty()) {
                                        Box box = shape.getBoundingBox();
                                        boxes.add(new IndexEntry<>(color.getRgb(), box.offset(entry.getKey())));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void onRender(Event<MatrixStack> event) {
        if (checkNull()) return;
        if (enable.get() && !boxes.isEmpty()) {
            MatrixStack stack = event.context();
            RenderUtils.startDrawVirtual(stack);
            try {
                TracingOption option = this.option.get();
                Vec3d cameraNeg = RenderUtils.getCameraPos().negate();
                if (option.box()) {
                    VRender.getInstance()
                            .createQuadsLayer(
                                    ((operation, vertexConsumer) -> {
                                        for (IndexEntry<Box> entry : boxes) {
                                            Box box = entry.val().offset(cameraNeg);
                                            operation.drawSolidBoxQuad(
                                                    stack,
                                                    vertexConsumer,
                                                    box.getMinPos(),
                                                    box.getMaxPos(),
                                                    ColorUtils.withAlphaInt(entry.index(), 0.25F));
                                        }
                                    }),
                                    true);
                }

                VRender.getInstance().createLinesLayer(((operation, vertexConsumer) -> {
                    if (option.box()) {
                        for (IndexEntry<Box> entry : boxes) {
                            Box box = entry.val().offset(cameraNeg);
                            operation.drawOutlinedBox(
                                    stack,
                                    vertexConsumer,
                                    box.getMinPos(),
                                    box.getMaxPos(),
                                    ColorUtils.withAlphaInt(entry.index(), 0.5F));
                        }
                    }
                    if (option.line()) {
                        Vec3d traceOrigin = RenderUtils.getTracerOrigin(0.0F);
                        for (IndexEntry<Box> entry : boxes) {
                            Box box = entry.val();
                            Vec3d pos = box.getCenter().add(cameraNeg);
                            operation.drawLine(
                                    stack,
                                    vertexConsumer,
                                    traceOrigin,
                                    pos,
                                    ColorUtils.withAlphaInt(entry.index(), 1.0F));
                        }
                    }
                }));
            } finally {
                RenderUtils.stopDrawVirtual(stack);
            }
        }
    }
}
