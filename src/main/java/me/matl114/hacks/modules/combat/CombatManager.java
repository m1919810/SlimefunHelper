package me.matl114.hacks.modules.combat;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.accessors.access.PlayerInteractEntityC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.annotations.Broadcast;
import me.matl114.events.channels.EventChannel;
import me.matl114.events.impl.BlockUpdate;
import me.matl114.events.impl.UseItemOnBlock;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.interact.SequencedActionManager;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.utils.algorithms.SerialExecutor;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EndCrystalItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

public class CombatManager extends BaseModule {
    public final ModulePath combat = makePath(Configs.COMBAT_CONFIG, "attack");
    public static CombatManager INSTANCE;

    public static final int SECTION_RADIUS = 1;
    private static final double NEARBY_ENTITY_CACHE_RADIUS = 16.0D;
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
        registerListener(Listener.getPlayerRespawnPoint(), this::onWorldSwitch);
        registerListener(Listener.getServerLeavePoint(), this::onServerLeave);
        registerListener(Listener.getBlockUpdateListener(), this::onBlockUpdate);
        registerListener(Listener.getChunkUpdateListener(), this::onChunkData);
        registerListener(
                SequencedActionManager.getSequencedActionResponse().getChannel(UseItemOnBlock.class),
                this::onUseOnBlockAck);
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractEntityC2SPacket.class),
                this::onPlayerAttackCrystal,
                Integer.MAX_VALUE);
        registerListener(Listener.getEntityRemoveListener().getChannel(EntityType.END_CRYSTAL), this::onEntityRemoval);
        registerListener(Listener.getPostPlayerUseItemOnBlock(), this::onPlayerPlaceCrystalOnBlock);
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

    private volatile Service currentService = new Service();

    public final Map<Entity, Integer> pendingCrystalRemovals = new HashMap<>();
    public final Set<IndexEntry<BlockPos>> pendingCrystalSummons = new HashSet<>();

    private void clearCombatState() {
        clearPendingCrystalOperations();
        clearNearbyEntityCache();
    }

    public void clearPendingCrystalOperations() {
        pendingCrystalRemovals.clear();
        pendingCrystalSummons.clear();
    }

    public void onUseOnBlockAck(Event<UseItemOnBlock> event) {
        pendingCrystalSummons.removeIf(
                entry -> Objects.equals(entry.val(), event.context.hitResult().getBlockPos()));
    }

    private void onPlayerAttackCrystal(Event<PlayerInteractEntityC2SPacket> event) {
        if (checkNull()) return;
        if (event.isCancelled()) return;
        if (PlayerInteractEntityC2SPacketAccess.of(event.context).isAttack()
                && mc.world.getEntityById(event.context.entityId) instanceof EndCrystalEntity crystal) {
            pendingCrystalRemovals.put(crystal, Tasks.getTick());
        }
    }

    private void onPlayerPlaceCrystalOnBlock(Event<UseItemOnBlock> eventUse) {
        if (checkNull()) return;
        if (eventUse.context().actionResult().isAccepted()
                && eventUse.context().handItem().getItem() instanceof EndCrystalItem
                && !eventUse.context().blockPlace()) {
            BlockPos hitResult = eventUse.context.hitResult().getBlockPos();
            BlockState state = mc.world.getBlockState(hitResult);
            if ((state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK)) && mc.world.isAir(hitResult.up())) {
                pendingCrystalSummons.add(new IndexEntry<>(Tasks.getTick(), hitResult));
            }
        }
    }

    private void onEntityRemoval(Event<Entity> entityEvent) {
        if (entityEvent.context instanceof EndCrystalEntity end) {
            pendingCrystalRemovals.remove(end);
        }
    }

    public boolean attackCrystal(Entity entity) {
        if (!Attack.INSTANCE.attackEntity(entity)) {
            pendingCrystalRemovals.put(entity, Tasks.getTick());
            return true;
        }
        return false;
    }

    public void markCrystalPlace(BlockPos pos) {
        pendingCrystalSummons.add(new IndexEntry<>(Tasks.getTick(), pos));
    }

    public boolean isPendingCrystalRemoval(Entity entity) {
        return pendingCrystalRemovals.containsKey(entity);
    }

    public boolean isPendingCrystalSummon(BlockPos pos) {
        return pendingCrystalSummons.stream().anyMatch(entry -> entry.val().equals(pos));
    }

    public boolean hasPendingCrystalSummon() {
        return !pendingCrystalSummons.isEmpty();
    }

    private List<Entity> currentTickCache = List.of();
    private Box currentTickCacheBox = null;

    public List<Entity> getNearbyEntities(Box queryBox) {
        if (contains(currentTickCacheBox, queryBox)) {
            return currentTickCache.stream()
                    .filter(s -> s.getBoundingBox().intersects(queryBox))
                    .filter(this::isNotPendingRemove)
                    .toList();
        }
        return mc.world.getOtherEntities(null, queryBox, this::isNotPendingRemove);
    }

    private boolean isNotPendingRemove(Entity s) {
        if (s instanceof EndCrystalEntity) {
            return !pendingCrystalRemovals.containsKey(s);
        } else {
            return true;
        }
    }

    public boolean canCubePlace(PlayerEntity player, BlockPos pos) {
        return canBlockPlace(player, pos, Blocks.OBSIDIAN.getDefaultState());
    }

    public boolean canBlockPlace(PlayerEntity player, BlockPos pos, BlockState state) {
        World world = player.getEntityWorld();
        if (!state.canPlaceAt(world, pos)) {
            return false;
        }
        ShapeContext context = ShapeContext.of(player);
        VoxelShape shape = state.getCollisionShape(world, pos, context);
        if (shape.isEmpty()) {
            return true;
        }
        VoxelShape worldShape = shape.offset(pos.getX(), pos.getY(), pos.getZ());
        return doesNotIntersectPendingCrystals(worldShape) && doesNotIntersectCachedEntities(worldShape);
    }

    private boolean doesNotIntersectCachedEntities(VoxelShape shape) {
        if (shape.isEmpty()) return true;
        Box box = shape.getBoundingBox();
        if (contains(currentTickCacheBox, box)) {
            return doesNotIntersectEntities(currentTickCache, shape);
        }
        return mc.world.doesNotIntersectEntities(null, shape);
    }

    private boolean doesNotIntersectEntities(List<Entity> entities, VoxelShape shape) {
        if (shape.isEmpty()) {
            return true;
        }
        Box shapeBox = shape.getBoundingBox();
        for (Entity entity : entities) {
            if (entity.isRemoved()
                    || entity.isSpectator()
                    || !entity.intersectionChecked
                    || !entity.getBoundingBox().intersects(shapeBox)
                    || isPendingCrystalRemoval(entity)) {
                continue;
            }
            if (VoxelShapes.matchesAnywhere(
                    shape, VoxelShapes.cuboid(entity.getBoundingBox()), BooleanBiFunction.AND)) {
                return false;
            }
        }

        return true;
    }

    private boolean doesNotIntersectPendingCrystals(VoxelShape shape) {
        if (shape.isEmpty()) return true;
        Box shapeBox = shape.getBoundingBox();
        for (var pending : pendingCrystalSummons) {
            Vec3d crystalBottom = pending.val().toBottomCenterPos().add(0, 1, 0);
            Box expectingBox = new Box(
                    crystalBottom.x - 1,
                    crystalBottom.y,
                    crystalBottom.z - 1,
                    crystalBottom.x + 1,
                    crystalBottom.y + 2,
                    crystalBottom.z + 1);
            if (!expectingBox.intersects(shapeBox)) {
                continue;
            }
            if (VoxelShapes.matchesAnywhere(shape, VoxelShapes.cuboid(expectingBox), BooleanBiFunction.AND)) {
                return false;
            }
        }
        return true;
    }

    @Nonnull
    public CrystalResult isCrystalConditionedBlockedByEntity(BlockPos crystalPos) {
        return isCrystalConditionedBlockedByEntity(crystalPos, null);
    }

    @Nonnull
    public CrystalResult isCrystalConditionedBlockedByEntity(
            BlockPos crystalPos, @Nullable Function<CrystalResult, Boolean> acceptingCondition) {
        return isCrystalBlockedByEntity(crystalPos, (result) -> {
            if (acceptingCondition != null) {
                var result2 = acceptingCondition.apply(result);
                if (result2 != null) {
                    return result2;
                }
            }
            if (result instanceof PendingItemDrop drop
                    && drop.dropState().getBlock().getBlastResistance() < 600) {
                // only obsidian like, other block will be destroyed by explosion and we dont have to consider about
                // dropping
                return false;
            } else {
                return true;
            }
        });
    }

    @Nonnull
    public CrystalResult isCrystalBlockedByEntity(
            BlockPos crystalPos, @Nonnull Predicate<CrystalResult> acceptingCondition) {
        Box crystalEntityBox = new Box(crystalPos).stretch(0, 1, 0);
        var result = getNearbyEntities(crystalEntityBox).stream()
                .filter(s -> EntityUtils.isEntityValid(s) && !isPendingCrystalRemoval(s))
                .map(EntityBlock::new)
                .filter(acceptingCondition)
                .findFirst();
        if (result.isPresent()) {
            return result.get();
        }
        for (var re : pendingCrystalSummons) {
            Vec3d center = re.val().toBottomCenterPos().add(0, 1, 0);
            Box summonBox = new Box(center.x - 1, center.y, center.z - 1, center.x + 1, center.y + 2, center.z + 1);
            if (summonBox.intersects(crystalEntityBox)) {
                CrystalResult result2 = new PendingCrystal(re.val());
                if (acceptingCondition.test(result2)) {
                    return result2;
                }
            }
        }
        BlockPos currentPos = crystalPos;
        BlockPos upPos = crystalPos.up();
        var lastBreak = SequencedActionManager.INSTANCE.getBeforeBreakPredictionState(currentPos);
        if (lastBreak.isPresent()) {
            BlockState state = lastBreak.get();
            CrystalResult result3 = new PendingItemDrop(currentPos, state);
            if (acceptingCondition.test(result3)) {
                return result3;
            }
        }
        lastBreak = SequencedActionManager.INSTANCE.getBeforeBreakPredictionState(upPos);
        if (lastBreak.isPresent()) {
            BlockState state = lastBreak.get();
            CrystalResult result4 = new PendingItemDrop(currentPos, state);
            if (acceptingCondition.test(result4)) {
                return result4;
            }
        }
        return new Success();
    }

    private void clearNearbyEntityCache() {
        currentTickCache = List.of();
        currentTickCacheBox = null;
    }

    private static boolean contains(Box outer, Box inner) {
        return outer != null
                && outer.minX <= inner.minX
                && outer.minY <= inner.minY
                && outer.minZ <= inner.minZ
                && outer.maxX >= inner.maxX
                && outer.maxY >= inner.maxY
                && outer.maxZ >= inner.maxZ;
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

    public void onWorldSwitch(Event<ClientPlayerEntity> event) {
        clearCaches();
        clearCombatState();
    }

    public void onServerLeave(Event<Void> event) {
        clearCaches();
        clearCombatState();
    }

    public void onPreTick(Event<ClientPlayerEntity> event) {
        if (checkNull()) {
            return;
        }
        // for most ping < 50, 3 ticks are ok for responses
        pendingCrystalRemovals
                .entrySet()
                .removeIf(
                        entry -> !EntityUtils.isEntityValid(entry.getKey()) || Tasks.getTick() >= entry.getValue() + 3);
        pendingCrystalSummons.removeIf(entry -> Tasks.getTick() >= entry.index() + 3);
        currentTickCacheBox = mc.player.getBoundingBox().expand(16, 16, 16);
        currentTickCache = mc.world.getOtherEntities(null, currentTickCacheBox);
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

    public interface CrystalResult {
        boolean isAccepted();
    }

    public static record Success() implements CrystalResult {

        @Override
        public boolean isAccepted() {
            return true;
        }
    }

    public static record EntityBlock(Entity entity) implements CrystalResult {

        @Override
        public boolean isAccepted() {
            return false;
        }
    }

    public static record PendingCrystal(BlockPos basePos) implements CrystalResult {

        @Override
        public boolean isAccepted() {
            return false;
        }
    }

    public static record PendingItemDrop(BlockPos dropPos, BlockState dropState) implements CrystalResult {

        @Override
        public boolean isAccepted() {
            return false;
        }
    }
}
