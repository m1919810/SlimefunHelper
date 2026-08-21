package me.matl114.hacks.modules.combat;

import com.google.common.base.Predicates;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.annotations.Broadcast;
import me.matl114.events.channels.EventChannel;
import me.matl114.events.impl.BlockUpdate;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.utils.algorithms.SerialExecutor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;

public class CombatManager extends BaseModule {
    public final ModulePath combat = makePath(Configs.COMBAT_CONFIG, "attack");
    public static CombatManager INSTANCE;

    public static final int SECTION_RADIUS = 1;
    private static final float BLAST_RESISTANCE_THRESHOLD = 600.0F;
    private static final Set<Block> MINEABLE_BLAST_RESISTANT_BLOCKS;
    private static final Set<Block> UNBREAKABLE_BLAST_RESISTANT_BLOCKS;

    static {
        Set<Block> blocks = new LinkedHashSet<>();
        for (Block block : Registries.BLOCK) {
            if (block.getBlastResistance() >= BLAST_RESISTANCE_THRESHOLD && block.getHardness() >= 0.0F) {
                blocks.add(block);
            }
        }
        MINEABLE_BLAST_RESISTANT_BLOCKS = Set.copyOf(blocks);
    }

    static {
        Set<Block> blocks = new LinkedHashSet<>();
        for (Block block : Registries.BLOCK) {
            if (block.getBlastResistance() >= BLAST_RESISTANCE_THRESHOLD && block.getHardness() < 0.0F) {
                blocks.add(block);
            }
        }
        UNBREAKABLE_BLAST_RESISTANT_BLOCKS = Set.copyOf(blocks);
    }

    public CombatManager() {
        super("CombatManager");
        INSTANCE = this;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onPreTick);
        registerListener(Listener.getWorldSwitchPoint(), this::onWorldSwitch);
        registerListener(Listener.getServerLeavePoint(), this::onServerLeave);
        registerListener(Listener.getBlockUpdateListener(), this::onBlockUpdate);
        registerListener(Listener.getChunkUpdateListener(), this::onChunkData);
    }

    @Getter
    @Broadcast
    private static final EventChannel<Service> requestEnableEvent = new EventChannel<>();

    private final Executor executor = new SerialExecutor(CompletableFuture::runAsync);
    private Map<ChunkSectionPos, SectionSnapshot> sectionSnapshots = new ConcurrentHashMap<>();

    public volatile Map<BlockPos, BlockState> trackedObsidianLike = new ConcurrentHashMap<>();
    public volatile Map<BlockPos, BlockState> trackedBedrockLike = new ConcurrentHashMap<>();
    public volatile Map<BlockPos, BlockState> trackedExplosives = new ConcurrentHashMap<>();
    public volatile Set<BlockPos> trackedHoles = ConcurrentHashMap.newKeySet();
    private Set<ChunkSectionPos> dirtySections = new HashSet<>();
    private ChunkSectionPos lastSectionPos = ChunkSectionPos.from(0, 0, 0);
    public final Set<EndCrystalEntity> trackedEndCrystals = new HashSet<>();
    private volatile Service currentService = new Service();

    private void clearTrackedCaches() {
        trackedObsidianLike.clear();
        trackedExplosives.clear();
        trackedEndCrystals.clear();
        trackedBedrockLike.clear();
        trackedHoles.clear();
    }

    private void clearCaches() {
        clearTrackedCaches();
        sectionSnapshots = new ConcurrentHashMap<>();
    }

    private void clearUnusedTrackedCaches(Service service) {
        if (!service.enableBlockSearch()) {
            trackedObsidianLike.clear();
            trackedBedrockLike.clear();
        }
        if (!service.enableExplosiveSearch()) {
            trackedExplosives.clear();
            trackedEndCrystals.clear();
        }
        if (!service.enableHoleSearch()) {
            trackedHoles.clear();
        }
    }

    private synchronized void updateTrackedMaps(
            Map<ChunkSectionPos, SectionSnapshot> updateMap, boolean trust, Service service) {
        Map<BlockPos, BlockState> obsidianLike = new ConcurrentHashMap<>();
        Map<BlockPos, BlockState> explosives = new ConcurrentHashMap<>();
        Map<BlockPos, BlockState> bedrockLike = new ConcurrentHashMap<>();
        Set<BlockPos> holes = ConcurrentHashMap.newKeySet();
        for (var re : updateMap.values()) {
            if (service.enableBlockSearch()) {
                for (var pos : re.mineableBlastResistantPositions) {
                    BlockState state = mc.world.getBlockState(pos);
                    if (trust || MINEABLE_BLAST_RESISTANT_BLOCKS.contains(state.getBlock())) {
                        obsidianLike.put(pos, state);
                    }
                }
                for (var pos : re.unbreakableBlastResistantPositions) {
                    BlockState state = mc.world.getBlockState(pos);
                    if (trust || UNBREAKABLE_BLAST_RESISTANT_BLOCKS.contains(state.getBlock())) {
                        bedrockLike.put(pos, state);
                    }
                }
            }
            if (service.enableExplosiveSearch()) {
                for (var pos : re.respawnAnchorPositions) {
                    BlockState state = mc.world.getBlockState(pos);
                    if (trust || state.getBlock() instanceof RespawnAnchorBlock) {
                        explosives.put(pos, state);
                    }
                }
            }
            if (service.enableHoleSearch()) {
                for (var pos : re.holesPositions) {
                    if (trust || isHole(mc.world, pos)) {
                        holes.add(pos);
                    }
                }
            }
        }
        trackedObsidianLike = obsidianLike;
        trackedBedrockLike = bedrockLike;
        trackedExplosives = explosives;
        trackedHoles = holes;
    }

    public void onWorldSwitch(Event<World> event) {
        clearCaches();
    }

    public void onServerLeave(Event<Void> event) {
        clearCaches();
    }

    public void onPreTick(Event<ClientPlayerEntity> event) {
        if (checkNull()) {
            return;
        }
        Service lastService = currentService;
        currentService = new Service();
        requestEnableEvent.broadcast(currentService);
        if (!Objects.equals(currentService, lastService)) {
            dirtySections.addAll(sectionSnapshots.keySet());
        }
        if (currentService.isDisabled()) {
            clearCaches();
            return;
        }

        onUpdatePlayerPosition();
        Service service = currentService;
        clearUnusedTrackedCaches(service);
        updateTrackedMaps(sectionSnapshots, false, service);
        Set<ChunkSectionPos> sections = dirtySections;
        dirtySections = new HashSet<>();
        ClientWorld world = mc.world;
        Map<ChunkSectionPos, SectionSnapshot> sectionRef = new ConcurrentHashMap<>();
        executor.execute(() -> {
            for (var re : sections) {
                sectionRef.put(re, scanSection(world, re, service));
            }
            sectionSnapshots.putAll(sectionRef);
            if (Objects.equals(currentService, service)) {
                updateTrackedMaps(sectionSnapshots, true, service);
            }
        });
        if (service.enableExplosiveSearch()) {
            updateTrackedEntities();
        } else {
            trackedEndCrystals.clear();
        }
    }

    public void onUpdatePlayerPosition() {
        ChunkSectionPos currentPos = ChunkSectionPos.from(mc.player);
        sectionSnapshots
                .entrySet()
                .removeIf(re -> Math.abs(re.getKey().getX() - currentPos.getX()) > SECTION_RADIUS
                        || Math.abs(re.getKey().getZ() - currentPos.getZ()) > SECTION_RADIUS
                        || Math.abs(re.getKey().getY() - currentPos.getY()) > SECTION_RADIUS);
        dirtySections.removeIf(re -> Math.abs(re.getX() - currentPos.getX()) > SECTION_RADIUS
                || Math.abs(re.getZ() - currentPos.getZ()) > SECTION_RADIUS
                || Math.abs(re.getY() - currentPos.getY()) > SECTION_RADIUS);
        for (var i = -SECTION_RADIUS; i <= SECTION_RADIUS; ++i) {
            for (var j = -SECTION_RADIUS; j <= SECTION_RADIUS; ++j) {
                for (var k = -SECTION_RADIUS; k <= SECTION_RADIUS; ++k) {
                    ChunkSectionPos pos =
                            ChunkSectionPos.from(currentPos.getX() + i, currentPos.getY() + j, currentPos.getZ() + k);
                    if (!sectionSnapshots.containsKey(pos)) {
                        dirtySections.add(pos);
                    }
                }
            }
        }

        lastSectionPos = currentPos;
    }

    public void updateTrackedEntities() {
        BlockPos minPos = lastSectionPos.getMinPos();
        Box currentTrackedBox = new Box(
                minPos.getX() - 16,
                minPos.getY() - 16,
                minPos.getZ() - 16,
                minPos.getX() + 32,
                minPos.getY() + 32,
                minPos.getZ() + 32);
        trackedEndCrystals.clear();
        trackedEndCrystals.addAll(
                mc.world.getEntitiesByType(EntityType.END_CRYSTAL, currentTrackedBox, Predicates.alwaysTrue()));
    }

    public void onChunkData(Event<ChunkPos> event) {
        if (checkNull()) {
            return;
        }
        ChunkPos packet = event.context();
        scheduleDirtyChunks(packet.x, packet.z);
    }

    public void onBlockUpdate(Event<BlockUpdate> event) {
        if (mc.world == null || mc.player == null) {
            return;
        }
        onPosUpdate(event.context.pos(), event.context.newState());
    }

    public void onPosUpdate(BlockPos pos, BlockState state) {
        ChunkSectionPos sectionPos = ChunkSectionPos.from(pos);
        if (isTrackedSection(sectionPos)) {
            SectionSnapshot snapshot = sectionSnapshots.get(sectionPos);
            if (snapshot != null) {
                Block type = state.getBlock();
                if (currentService.enableBlockSearch() && MINEABLE_BLAST_RESISTANT_BLOCKS.contains(type)) {
                    snapshot.mineableBlastResistantPositions.add(pos);
                } else {
                    snapshot.mineableBlastResistantPositions.remove(pos);
                }
                if (currentService.enableBlockSearch() && UNBREAKABLE_BLAST_RESISTANT_BLOCKS.contains(type)) {
                    snapshot.unbreakableBlastResistantPositions.add(pos);
                } else {
                    snapshot.unbreakableBlastResistantPositions.remove(pos);
                }
                if (currentService.enableExplosiveSearch() && type instanceof RespawnAnchorBlock) {
                    snapshot.respawnAnchorPositions.add(pos);
                } else {
                    snapshot.respawnAnchorPositions.remove(pos);
                }
            } else {
                dirtySections.add(sectionPos);
            }
        }
        if (currentService.enableHoleSearch()) {
            updateHoleCandidates(pos);
        }
    }

    private void scheduleDirtyChunks(int chunkX, int chunkZ) {
        if (Math.abs(chunkX - lastSectionPos.getX()) <= 1 && Math.abs(chunkZ - lastSectionPos.getZ()) <= 1) {
            for (var i = -1; i <= 1; ++i) {
                dirtySections.add(ChunkSectionPos.from(chunkX, lastSectionPos.getY() + i, chunkZ));
            }
        }
    }

    private SectionSnapshot scanSection(ClientWorld world, ChunkSectionPos key, Service service) {
        WorldChunk chunk = world.getChunkManager().getWorldChunk(key.getX(), key.getZ());
        if (chunk == null) {
            return SectionSnapshot.empty();
        }

        int sectionIndex = key.getY() - world.getBottomSectionCoord();
        ChunkSection[] sections = chunk.getSectionArray();
        if (sectionIndex < 0 || sectionIndex >= sections.length) {
            return SectionSnapshot.empty();
        }
        ChunkSection section = sections[sectionIndex];
        if (section == null || section.isEmpty()) {
            return SectionSnapshot.empty();
        }

        PalettedContainer<BlockState> states = section.getBlockStateContainer();
        Set<BlockPos> mineableBlastResistantPositions = ConcurrentHashMap.newKeySet();
        Set<BlockPos> unbreakableBlastResistantPositions = ConcurrentHashMap.newKeySet();
        Set<BlockPos> respawnAnchorPositions = ConcurrentHashMap.newKeySet();
        Set<BlockPos> holesPositions = ConcurrentHashMap.newKeySet();
        int baseX = key.getX() << 4;
        int baseY = key.getY() << 4;
        int baseZ = key.getZ() << 4;
        for (int y = 0; y < 16; ++y) {
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 16; ++x) {
                    int localIndex = x | (z << 4) | (y << 8);
                    BlockState state = states.get(localIndex);
                    BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                    Block block = state.getBlock();
                    if (service.enableBlockSearch() && MINEABLE_BLAST_RESISTANT_BLOCKS.contains(block)) {
                        mineableBlastResistantPositions.add(pos);
                    }
                    if (service.enableBlockSearch() && UNBREAKABLE_BLAST_RESISTANT_BLOCKS.contains(block)) {
                        unbreakableBlastResistantPositions.add(pos);
                    }
                    if (service.enableExplosiveSearch() && block instanceof RespawnAnchorBlock) {
                        respawnAnchorPositions.add(pos);
                    }
                    if (service.enableHoleSearch() && isHole(world, pos)) {
                        holesPositions.add(pos);
                    }
                }
            }
        }

        return new SectionSnapshot(
                mineableBlastResistantPositions,
                unbreakableBlastResistantPositions,
                respawnAnchorPositions,
                holesPositions);
    }

    private boolean isTrackedSection(ChunkSectionPos sectionPos) {
        return Math.abs(sectionPos.getX() - lastSectionPos.getX()) <= SECTION_RADIUS
                && Math.abs(sectionPos.getZ() - lastSectionPos.getZ()) <= SECTION_RADIUS
                && Math.abs(sectionPos.getY() - lastSectionPos.getY()) <= SECTION_RADIUS;
    }

    private void updateHoleCandidates(BlockPos pos) {
        updateHoleState(pos);
        updateHoleState(pos.north());
        updateHoleState(pos.south());
        updateHoleState(pos.west());
        updateHoleState(pos.east());
    }

    private void updateHoleState(BlockPos pos) {
        ChunkSectionPos sectionPos = ChunkSectionPos.from(pos);
        if (!isTrackedSection(sectionPos)) {
            return;
        }
        SectionSnapshot snapshot = sectionSnapshots.get(sectionPos);
        if (snapshot == null) {
            dirtySections.add(sectionPos);
            return;
        }
        if (isHole(mc.world, pos)) {
            snapshot.holesPositions.add(pos);
        } else {
            snapshot.holesPositions.remove(pos);
        }
    }

    private boolean isHole(ClientWorld world, BlockPos pos) {
        if (!world.getBlockState(pos).isAir()) {
            return false;
        }
        return !world.getBlockState(pos.north()).isAir()
                && !world.getBlockState(pos.south()).isAir()
                && !world.getBlockState(pos.west()).isAir()
                && !world.getBlockState(pos.east()).isAir();
    }

    private record SectionSnapshot(
            Set<BlockPos> mineableBlastResistantPositions,
            Set<BlockPos> unbreakableBlastResistantPositions,
            Set<BlockPos> respawnAnchorPositions,
            Set<BlockPos> holesPositions) {
        public static SectionSnapshot empty() {
            return new SectionSnapshot(
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet());
        }
    }

    public record ExplosiveContext(BlockState state, Map<PlayerEntity, Double> damageCache) {}

    @Data
    @Getter
    @Setter
    @Accessors(fluent = true, chain = true)
    public static class Service {
        boolean enableBlockSearch;
        boolean enableExplosiveSearch;
        boolean enableHoleSearch;

        public boolean isDisabled() {
            return !enableBlockSearch && !enableExplosiveSearch && !enableHoleSearch;
        }
    }
}
