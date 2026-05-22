package me.matl114.hacks.modules.render;

import static me.matl114.utils.ColorUtils.*;

import java.awt.*;
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
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.utils.ColorUtils;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VRender;
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
import net.minecraft.world.chunk.ChunkStatus;

public class WorldScanner extends BaseModule {
    public final ModulePath detectBlock = makePath(Configs.RENDER_CONFIG, "detect-block");
    public final ModulePath worldScanner = detectBlock.add("search");

    public WorldScanner() {
        bindFlag(enable);
    }

    public FlagRef enable = flagBuilder(worldScanner.add("enable")).build();

    public NBTRef<RegistryRegex<Block>> typeFilter = builder(
                    worldScanner.add("search-type"), RegistryRegex.<Block>parameter())
            .defaultValue(
                    new RegistryRegex<>(new Regex("^(.*_portal|end_gateway|end_portal_frame)$"), Registries.BLOCK))
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

        registerListener(RenderListener.getRenderLayerTasks(), this::onRender);
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

    boolean pendingRefreshWhenInGame = true;
    public Set<Block> currentSearchingSet = new HashSet<>();
    public Map<ChunkPos, Map<BlockPos, BlockState>> currentSearchingResult = new ConcurrentHashMap<>();

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
                    if (stateMap.isEmpty()) {
                        currentSearchingResult.remove(chunkPos);
                    }
                }
            }
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
    }

    int resultUpdate = 0;
    List<IndexEntry<Box>> boxes = new ArrayList<>();

    public void onTick(Event<ClientPlayerEntity> event) {
        if (!checkNull()
                && pendingRefreshWhenInGame
                && (mc.currentScreen == null || mc.currentScreen instanceof HandledScreen<?>)) {
            // do not refresh when config is open or when player open exit menu
            pendingRefreshWhenInGame = false;
            WorldTasks.restartWorldScanner();
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
