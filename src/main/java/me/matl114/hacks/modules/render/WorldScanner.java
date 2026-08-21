package me.matl114.hacks.modules.render;

import static me.matl114.utils.ColorUtils.*;

import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiPredicate;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.hacks.WorldTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.*;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.Chunk;

public class WorldScanner extends BaseModule {
    public final ModulePath detectBlock = makePath(Configs.RENDER_CONFIG, "detect-block");
    public final ModulePath worldScanner = detectBlock.add("search");

    public WorldScanner() {
        super("BlockESP");
        bindFlag(enable);
    }

    public Set<Block> currentSearchingSet = new HashSet<>();
    boolean pendingRefreshWhenInGame = true;
    public Map<ChunkPos, Map<BlockPos, BlockState>> currentSearchingResult = new ConcurrentHashMap<>();

    public FlagRef enable = flagBuilder(worldScanner.add("enable")).build();

    public NBTRef<EntrySet<Block>> typeFilter = builder(worldScanner.add("search-type"), EntrySet.<Block>parameter())
            .defaultValue(new EntrySet<>(new Regex("^(.*_portal|end_gateway|end_portal_frame)$"), Registries.BLOCK))
            .updateListener(this::updateBlockTypeFilter)
            .build();

    public NBTRef<EntryPrimitiveMap<Block, TextColor>> color = builder(
                    worldScanner.add("search-color"), EntryPrimitiveMap.<Block, TextColor>parameter())
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

    public IntRef distanceChunk = builder(worldScanner.add("search-radius"), IntRef.TYPE)
            .defaultValue(12)
            .build();

    public NBTRef<TracingOption> option = builder(worldScanner.add("esp-option"), TracingOption.class)
            .defaultValue(new TracingOption(true, false))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onTick);

        registerListener(RenderListener.getRender3DEvent(), this::onRender);
        registerListener(Listener.getPreWorldScannListener(), this::onRequestScann);
        registerListener(Listener.getResetWorldScannListener(), this::onResetWorldScanner);
        registerListener(Listener.getWorldScannChunkBlockFilterList(), this::onChunkScannPredicate);
        registerListener(Listener.getWorldScannChunkResult(), this::onChunkScannResult);
        registerListener(Listener.getWorldScannBlockResult(), this::onBlockScannResult);
    }

    public void onRequestScann(Event<Boolean> event) {
        if (enable.get()) {
            event.context(Boolean.TRUE);
        }
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
    }

    public void onResetWorldScanner(Event<Void> event) {
        currentSearchingResult.clear();
    }

    public void onChunkScannPredicate(Event<List<BiPredicate<BlockPos, BlockState>>> event) {
        if (enable.get()) {
            event.context.add((s, b) -> currentSearchingSet.contains(b.getBlock()));
        }
    }

    public void onChunkScannResult(Event<Map<BlockPos, BlockState>> chunkScannResultEvent) {
        if (enable.get()) {
            // accepted
            ChunkPos chunkPos = chunkScannResultEvent.getArgs(0);
            ConcurrentHashMap<BlockPos, BlockState> stateMap =
                    new ConcurrentHashMap<>(chunkScannResultEvent.context.size());
            for (var entry : chunkScannResultEvent.context.entrySet()) {
                if (currentSearchingSet.contains(entry.getValue().getBlock())) {
                    stateMap.put(entry.getKey(), entry.getValue());
                }
            }
            currentSearchingResult.put(chunkPos, stateMap);
        }
    }

    public void onBlockScannResult(Event<BlockState> stateUpdate) {
        if (enable.get()) {
            BlockState state = stateUpdate.context;
            BlockPos pos = stateUpdate.getArgs(0);
            ChunkPos chunkPos = stateUpdate.getArgs(1);
            boolean accept = currentSearchingSet.contains(state.getBlock());
            if (accept) {
                Map<BlockPos, BlockState> stateMap =
                        currentSearchingResult.computeIfAbsent(chunkPos, k -> new ConcurrentHashMap<>());
                stateMap.put(pos, state);
            } else {
                Map<BlockPos, BlockState> stateMap = currentSearchingResult.get(chunkPos);
                if (stateMap != null) {
                    stateMap.remove(pos);
                }
            }
        }
    }

    public void updateBlockTypeFilter(EntrySet<Block> typeFilter) {
        Set<Block> update = typeFilter.set();
        if (!Objects.equals(update, currentSearchingSet)) {
            currentSearchingSet = update;
            if (!checkNull()) {
                pendingRefreshWhenInGame = true;
            }
        }
    }

    public void validateAndClearSearchResult(boolean strict) {
        if (checkNull()) return;
        var iter = currentSearchingResult.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            var key = entry.getKey();
            Chunk chunk = mc.world.getChunkManager().getWorldChunk(key.x, key.z);
            if (chunk == null) {
                iter.remove();
            } else {
                Map<BlockPos, BlockState> stateMap = entry.getValue();
                if (stateMap == null || stateMap.isEmpty()) {
                    iter.remove();
                } else {
                    if (strict) {
                        // should we add this?
                    }
                }
            }
        }
    }

    int resultUpdate = 0;
    // List<IndexEntry<Box>> boxes = new ArrayList<>();
    final RenderCollector<Box> boxOutlineCollector = RenderCollectors.createOutlineCollector();
    final RenderCollector<Box> boxSolidCollector = RenderCollectors.createFaceCollector();
    final RenderCollector<Vec3d> traceLineCollector = RenderCollectors.createTracerCollector();
    int lastLogTick = 0;
    final int MAX_RENDER_BLOCKS = 10_000;

    public void onTick(Event<ClientPlayerEntity> event) {
        if (!checkNull()
                && pendingRefreshWhenInGame
                && (mc.currentScreen == null || mc.currentScreen instanceof HandledScreen<?>)) {
            // do not refresh when config is open or when player open exit menu
            pendingRefreshWhenInGame = false;
            WorldTasks.restartWorldScanner();
        }
        boxOutlineCollector.clear();
        boxSolidCollector.clear();
        traceLineCollector.clear();
        if (enable.get()) {
            if (resultUpdate < 50) {
                resultUpdate++;
                validateAndClearSearchResult(false);
            } else {
                resultUpdate = 0;
                validateAndClearSearchResult(true);
            }

            if (!checkNull()) {
                if (!currentSearchingResult.isEmpty()) {
                    int radius = distanceChunk.get();
                    ChunkPos chunkPos = mc.player.getChunkPos();
                    Set<ChunkPos> chunkKeys = new HashSet<>(currentSearchingResult.keySet());
                    int cnt = 0;
                    TracingOption option = this.option.get();
                    for (ChunkPos chunkKey : chunkKeys) {
                        if (Math.abs(chunkPos.x - chunkKey.x) <= radius
                                && Math.abs(chunkPos.z - chunkKey.z) <= radius) {
                            Map<BlockPos, BlockState> stateMap = currentSearchingResult.get(chunkKey);
                            for (var entry : stateMap.entrySet()) {
                                BlockState state = entry.getValue();
                                TextColor color = this.color.get().getEntryValue(state.getBlock());
                                if (color != null) {
                                    VoxelShape shape = entry.getValue().getOutlineShape(mc.world, entry.getKey());
                                    if (!shape.isEmpty()) {
                                        Box box = shape.getBoundingBox();
                                        if (cnt < MAX_RENDER_BLOCKS) {
                                            if (option.box()) {
                                                boxOutlineCollector.submit(
                                                        box.offset(entry.getKey()),
                                                        ColorUtils.withAlphaInt(color.getRgb(), 128));
                                                boxSolidCollector.submit(
                                                        box.offset(entry.getKey()),
                                                        ColorUtils.withAlphaInt(color.getRgb(), 64));
                                            }
                                            if (option.line()) {
                                                traceLineCollector.submit(
                                                        box.offset(entry.getKey())
                                                                .getCenter(),
                                                        ColorUtils.withAlphaInt(color.getRgb(), 255));
                                            }
                                        }
                                        cnt += 1;
                                    }
                                }
                            }
                        }
                    }
                    if (cnt > MAX_RENDER_BLOCKS) {
                        // 10 s one warn
                        if (lastLogTick < Tasks.getTick() - 10 * 20) {
                            lastLogTick = Tasks.getTick();
                            logI18N("message.module.world-scanner.too-many-targets", cnt, MAX_RENDER_BLOCKS);
                        }
                    }
                }
            }
        }
    }

    public void onRender(Event<MatrixStack> event) {
        if (checkNull()) return;
        if (enable.get()) {
            MatrixStack stack = event.context();
            RenderUtils.startDrawVirtual(stack);
            try {
                boxSolidCollector.render3D(stack);
                boxOutlineCollector.render3D(stack);
                traceLineCollector.render3D(stack);
            } finally {
                RenderUtils.stopDrawVirtual(stack);
            }
        }
    }
}
