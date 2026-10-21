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
import me.matl114.accessors.interfaces.MetadataHolder;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.annotations.Broadcast;
import me.matl114.events.channels.EventChannel;
import me.matl114.events.impl.BlockUpdate;
import me.matl114.events.impl.UseItemOnBlock;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.interact.SequencedActionManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.utils.DamageUtils;
import me.matl114.utils.ExplosionUtils;
import me.matl114.utils.algorithms.SerialExecutor;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.containers.MetaData;
import net.minecraft.block.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EndCrystalItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
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
    private static final float BLAST_RESISTANCE_THRESHOLD = 600.0F;
    private static final int DAMAGE_ESTIMATE_TTL = 10;
    private static final float UNKNOWN_DAMAGE_ESTIMATE = 1.0F;
    public static final String KEY_DAMAGE_TRACKER = "slimefunhelper:combat/damage_tracker";
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

    private enum TrackedDamageCategory {
        PLAYER_ATTACK(DamageTypes.PLAYER_ATTACK),
        SMASH(RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.ofVanilla("mace_smash"))),
        SPEAR(RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.ofVanilla("spear"))),
        BAD_RESPAWN_POINT(DamageTypes.BAD_RESPAWN_POINT),
        PLAYER_EXPLOSION(DamageTypes.PLAYER_EXPLOSION),
        EXPLOSION(DamageTypes.EXPLOSION);

        TrackedDamageCategory(RegistryKey<DamageType> world) {
            this.damageTypeKey = world;
        }

        RegistryKey<DamageType> damageTypeKey;
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
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractEntityC2SPacket.class),
                this::onTrackPlayerAttack,
                Integer.MAX_VALUE - 1);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(EntityDamageS2CPacket.class), this::onDamagePacket);
        registerListener(
                Listener.getPacketPreHandlePoint().getChannel(ExplosionS2CPacket.class), this::onExplosionPacket);
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

    public void onUseOnBlockAck(Event<IndexEntry<UseItemOnBlock>> event) {
        UseItemOnBlock action = event.context().val();
        pendingCrystalSummons.removeIf(
                entry -> Objects.equals(entry.val(), action.hitResult().getBlockPos()));
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

    private void onTrackPlayerAttack(Event<PlayerInteractEntityC2SPacket> event) {
        if (event.isCancelled()
                || checkNull()
                || !PlayerInteractEntityC2SPacketAccess.of(event.context).isAttack()) {
            return;
        }
        Entity target = mc.world.getEntityById(
                PlayerInteractEntityC2SPacketAccess.of(event.context).getEntityId());
        if (!(target instanceof PlayerEntity player)) return;
        ItemStack weapon = mc.player.getMainHandStack();
        cacheDamageEstimate(
                player, TrackedDamageCategory.PLAYER_ATTACK, getMaximumWeaponDamage(mc.player, player, weapon));
        String weaponPath = Registries.ITEM.getId(weapon.getItem()).getPath();
        if (weapon.getItem() instanceof MaceItem) {
            cacheDamageEstimate(player, TrackedDamageCategory.SMASH, getMaximumWeaponDamage(mc.player, player, weapon));
        }
        if (weaponPath.contains("spear")) {
            cacheDamageEstimate(player, TrackedDamageCategory.SPEAR, getMaximumWeaponDamage(mc.player, player, weapon));
        }
    }

    private void onExplosionPacket(Event<ExplosionS2CPacket> event) {
        if (checkNull()) return;
        BlockPos explosionBlock = BlockPos.ofFloored(event.context.center());
        BlockState explosionState = mc.world.getBlockState(explosionBlock);
        boolean centeredExplosion = Objects.equals(event.context.center(), explosionBlock.toCenterPos());
        boolean respawnAnchor = centeredExplosion && explosionState.getBlock() instanceof RespawnAnchorBlock;
        boolean bed = centeredExplosion && explosionState.getBlock() instanceof BedBlock;
        float radius = event.context.radius();
        if (radius <= 0.0F) {
            radius = respawnAnchor
                    ? ExplosionUtils.RESPAWN_ANCHOR_POWER
                    : bed ? ExplosionUtils.BED_POWER : ExplosionUtils.END_CRYSTAL_POWER;
        }
        ExplosionUtils.BlockStateAccess blockAccess =
                ExplosionUtils.fromWorldWithAutomaticOverrides(mc.world, event.context.center());
        for (PlayerEntity player : mc.world.getPlayers()) {
            Box targetBox = player.getBoundingBox();
            if (player != mc.player) {
                Vec3d syncedPos = PositionPredict.INSTANCE.getPredictor(player).getCurrentSyncedPos();
                if (syncedPos != null) {
                    targetBox = targetBox.offset(syncedPos.subtract(player.getPos()));
                }
            }
            float rawDamage = ExplosionUtils.calculateExplosionRawDamage(
                    radius, event.context.center(), targetBox, blockAccess, ExplosionUtils.ALL_TERRAIN);
            float damage = DamageUtils.getMultipliedDamageByDifficulty(mc.world, rawDamage);
            DamageTracker tracker = getDamageTracker(player);
            if (tracker != null) {
                tracker.onExplosionDamage(damage);
            }
        }
    }

    private void onDamagePacket(Event<EntityDamageS2CPacket> event) {
        if (checkNull()) return;
        Entity entity = mc.world.getEntityById(event.context.entityId());
        if (!(entity instanceof PlayerEntity player)) return;

        DamageTracker tracker = getOrCreateDamageTracker(player);
        RegistryKey<DamageType> damageType = event.context.sourceType().getKey().orElse(null);
        Entity source = mc.world.getEntityById(event.context.sourceCauseId());
        TrackedDamageCategory category = getDamageCategory(damageType);
        float damage = UNKNOWN_DAMAGE_ESTIMATE;
        if (isExplosionDamageCategory(category)) {
            damage = UNKNOWN_DAMAGE_ESTIMATE;
        } else if (source instanceof PlayerEntity attacker) {
            damage = tracker.takeEstimate(category, Tasks.getTick());
            if (damage <= 0.0F) {
                ItemStack weapon = attacker.getMainHandStack();
                damage = getMaximumWeaponDamage(attacker, player, weapon);
            }
        }
        if (damage <= 0.0F) damage = UNKNOWN_DAMAGE_ESTIMATE;
        tracker.onDamage(damage, Tasks.getTick(), 0);
    }

    public void cacheDamageEstimate(PlayerEntity player, RegistryKey<DamageType> damageType, float damage) {
        if (damageType == null || !Float.isFinite(damage) || damage < 0.0F) return;
        cacheDamageEstimate(player, getDamageCategory(damageType), damage);
    }

    private void cacheDamageEstimate(PlayerEntity player, TrackedDamageCategory category, float damage) {
        if (!Float.isFinite(damage) || damage < 0.0F) return;
        DamageTracker tracker = getOrCreateDamageTracker(player);
        tracker.cacheEstimate(category, damage, Tasks.getTick());
        tracker.considerEstimatedDamage(damage);
    }

    private float getMaximumWeaponDamage(PlayerEntity attacker, PlayerEntity target, ItemStack weapon) {
        double baseDamage = DamageUtils.getMaxRealAttackDamage(
                attacker,
                target,
                weapon,
                attacker == mc.player ? PlayerStateManager.INSTANCE.fallDistance : attacker.fallDistance,
                true);
        return (float) (baseDamage * 1.5D);
    }

    private static TrackedDamageCategory getDamageCategory(RegistryKey<DamageType> type) {
        if (DamageUtils.isType(type, "mace_smash")) return TrackedDamageCategory.SMASH;
        if (DamageUtils.isType(type, "spear")) return TrackedDamageCategory.SPEAR;
        if (DamageUtils.isType(type, "bad_respawn_point")) return TrackedDamageCategory.BAD_RESPAWN_POINT;
        if (DamageUtils.isType(type, "player_explosion")) return TrackedDamageCategory.PLAYER_EXPLOSION;
        if (DamageUtils.isType(type, "explosion")) return TrackedDamageCategory.EXPLOSION;
        return TrackedDamageCategory.PLAYER_ATTACK;
    }

    private static boolean isExplosionDamageCategory(TrackedDamageCategory category) {
        return category == TrackedDamageCategory.BAD_RESPAWN_POINT
                || category == TrackedDamageCategory.PLAYER_EXPLOSION
                || category == TrackedDamageCategory.EXPLOSION;
    }

    @Nullable
    public DamageTracker getDamageTracker(PlayerEntity player) {
        if (!(player instanceof MetadataHolder holder) || holder.isMetaEmpty()) return null;
        return holder.getMetadata().get(this, KEY_DAMAGE_TRACKER);
    }

    private DamageTracker getOrCreateDamageTracker(PlayerEntity player) {
        MetaData metadata = ((MetadataHolder) player).getMetadata();
        return metadata.getOrPut(this, KEY_DAMAGE_TRACKER, DamageTracker::new);
    }

    public static class DamageTracker {
        private final Map<TrackedDamageCategory, DamageEstimate> estimates = new EnumMap<>(TrackedDamageCategory.class);
        public float lastDamage;
        public float damageThreshold;
        public int lastDamageTick;
        private int invulnerabilityEndTick;

        public int getInvulnerabilityTicks() {
            return Math.max(invulnerabilityEndTick - Tasks.getTick(), 0);
        }

        private void cacheEstimate(TrackedDamageCategory category, float damage, int tick) {
            DamageEstimate estimate = new DamageEstimate(damage, tick + DAMAGE_ESTIMATE_TTL);
            estimates.put(category, estimate);
        }

        private void considerEstimatedDamage(float damage) {
            if (getInvulnerabilityTicks() <= 0 || damage <= 0.0F) return;
            lastDamage = Math.max(lastDamage, damage);
            damageThreshold = lastDamage;
        }

        private float takeEstimate(TrackedDamageCategory category, int tick) {
            estimates.entrySet().removeIf(entry -> entry.getValue().expiresAt < tick);
            DamageEstimate estimate = estimates.remove(category);
            if (estimate == null && category != TrackedDamageCategory.PLAYER_ATTACK) {
                estimate = estimates.remove(TrackedDamageCategory.PLAYER_ATTACK);
            }
            return estimate == null ? 0.0F : estimate.damage;
        }

        private void onExplosionDamage(float damage) {
            if (getInvulnerabilityTicks() <= 0 || damage <= 0.0F) return;
            lastDamage = Math.max(lastDamage, damage);
            damageThreshold = lastDamage;
        }

        private void onDamage(float damage, int tick, int networkDelay) {
            lastDamage = damage;
            damageThreshold = lastDamage;
            int invulnerabilityTicks = Math.max(10 - networkDelay, 0);
            lastDamageTick = tick;
            invulnerabilityEndTick = tick + invulnerabilityTicks;
        }

        public boolean canTakeDamage() {
            return getInvulnerabilityTicks() == 0;
        }

        public float getDamageAboveThreshold(float incomingDamage) {
            if (incomingDamage <= 0.0F) return 0.0F;
            if (canTakeDamage()) return incomingDamage;
            return Math.max(incomingDamage - damageThreshold, 0.0F);
        }
    }

    private record DamageEstimate(float damage, int expiresAt) {}

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
