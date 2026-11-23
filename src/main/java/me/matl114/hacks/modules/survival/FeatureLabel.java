package me.matl114.hacks.modules.survival;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.Render3D;
import me.matl114.hacks.WorldTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.inv.ChestHistory;
import me.matl114.hacks.modules.task.ServerStorage;
import me.matl114.hacks.utils.RenderUtils;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.hacks.utils.world.ChunkStorage;
import me.matl114.hooks.impl.xaerowaypoints.IXWaypoint;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.utils.Debug;
import me.matl114.utils.render.RenderCollector;
import me.matl114.utils.world.ContainerPosition;
import me.matl114.utils.worldgen.features.DungeonChest;
import me.matl114.utils.worldgen.features.DungeonFeatureConfig;
import me.matl114.utils.worldgen.features.DungeonFeatureSimulator;
import me.matl114.utils.worldgen.features.DungeonPrediction;
import me.matl114.utils.worldgen.features.DungeonTerrain;
import me.matl114.utils.worldgen.features.FeatureTerrain;
import me.matl114.utils.worldgen.loot.LootVersion;
import me.matl114.utils.worldgen.terrain.OverworldTerrainContext;
import me.matl114.utils.worldgen.terrain.TerrainChunkCache;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Labels overworld dungeon chests whose seeded loot contains an enchanted golden apple.
 *
 * <p>Everything is derived from the world seed, with no server or client chunk data: the
 * {@code monster_room} placement streams, vanilla's pre-feature terrain (noise, aquifers and
 * carvers, see {@link OverworldTerrainContext}), the room and chest generator, and the
 * {@code chests/simple_dungeon} loot table. Results are cached per chunk around the player and are
 * the only source of waypoints and rendering.
 */
public class FeatureLabel extends BaseModule {
    private static final int SCAN_INTERVAL = 10;
    private static final int WAYPOINT_SYNC_INTERVAL = 20;
    /** Seed-only work is scheduled in square regions of this many chunks; small, so near work preempts sooner. */
    private static final int SEED_ONLY_REGION_CHUNKS = 4;
    /** Items a worker takes from the queue at once, nearest first (possibly spanning regions). */
    private static final int SEED_ONLY_BATCH_SIZE = 64;

    /** Screening (no-terrain candidate pass and surface bound) is cheap: few threads. */
    private static final int SCREEN_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() / 8);
    /** Terrain simulation is the expensive stage: half of the cores. */
    private static final int TERRAIN_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

    private static final ExecutorService SCREEN_EXECUTOR = seedOnlyExecutor("FeatureLabel screening", SCREEN_THREADS);
    private static final ExecutorService TERRAIN_EXECUTOR = seedOnlyExecutor("FeatureLabel terrain", TERRAIN_THREADS);

    /** Seed-only work runs below normal priority to leave the render thread room. */
    private static ExecutorService seedOnlyExecutor(String name, int threads) {
        return Executors.newFixedThreadPool(threads, task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
    }
    /** Per worker: one tile plus its border and room for the next tile's overlap. */
    private static final int TERRAIN_CACHE_CHUNKS = 256;

    private static final int CACHE_MARGIN_BLOCKS = 64;
    /** Waypoint colors follow chest history; refresh them at this interval even without other changes. */
    private static final int WAYPOINT_COLOR_REFRESH_INTERVAL = 100;
    /**
     * Per-chunk flag in the world's {@link ServerStorage}, like StructureLabel's
     * {@code slimefunhelper:structure_label_visited}: a chunk whose predicted apple chest the player
     * has come close to keeps a gray waypoint across sessions.
     */
    private static final String VISITED_KEY = "slimefunhelper:feature_label_visited";
    /** Horizontal distance at which a predicted chest counts as visited. */
    private static final int VISIT_DISTANCE = 32;

    public final ModulePath root = makePath(Configs.SURVIVAL_CONFIG, "feature-label");
    public final FlagRef enable = flagBuilder(root.addEnable()).build();
    public final EnumRef<LootVersion> lootVersion = builder(root.add("loot-version"), LootVersion.class)
            .defaultValue(LootVersion.V1_21_11)
            .build();
    public final IntRef distance = intBuilder(root.add("distance"))
            .defaultValue(256)
            .validator(Configs.intRange(16, 4096))
            .build();
    /**
     * Seed-only attempts whose floor is more than this many blocks above the chunk's highest
     * preliminary surface estimate are rejected without sampling noise. Measured on random columns,
     * the real surface exceeds the estimate by 15 (median), 35 (99%) and at most about 100 blocks.
     */
    public final IntRef surfaceMargin = intBuilder(root.add("surface-margin"))
            .defaultValue(16)
            .validator(Configs.intRange(0, 384))
            .updateListener(margin -> {
                this.clearSeedOnlyState();
            })
            .build();
    /** Faster mode: terrain-check only chunks where the no-terrain pass found an apple. */
    public final FlagRef candidateOnly = flagBuilder(root.add("candidate-only"), true)
            .updateListener(enabled -> {
                this.clearSeedOnlyState();
            })
            .build();

    /**
     * Per-feature-type settings, one sub-group each so that more types can be added beside
     * {@code dungeon} later. Disabling a type drops its cached results and stops computing it.
     */
    private final ModulePath dungeonRoot = root.add("dungeon");

    public final FlagRef dungeonEnable = flagBuilder(dungeonRoot.addEnable(), true)
            .updateListener(enabled -> {
                this.clearSeedOnlyState();
                this.waypointDirty = true;
            })
            .build();
    public final IntRef dungeonColorId = intBuilder(dungeonRoot.add("color-id"))
            .defaultValue(Formatting.GREEN.ordinal())
            .validator(Configs.intRange(0, 15))
            .updateListener(color -> this.waypointDirty = true)
            .build();
    /**
     * Every predicted apple chest stays cached in memory, but only the nearest N become Xaero
     * waypoints, re-selected whenever the player crosses a chunk border.
     */
    public final IntRef maxWaypoints = intBuilder(root.add("max-waypoints"))
            .defaultValue(1024)
            .validator(Configs.intRange(1, 16384))
            .updateListener(max -> waypointDirty = true)
            .build();

    /** Same option as StructureLabel's {@code label-enchanted-golden-apple-chests}. */
    public final FlagRef labelEnchantedGoldenAppleChests =
            flagBuilder(root.add("label-enchanted-golden-apple-chests"), true).build();

    public final IntRef renderRange = intBuilder(root.add("render-range"))
            .defaultValue(128)
            .validator(Configs.intRange(16, 512))
            .build();
    public final NBTRef<WrapColor> renderColor = builder(root.add("render-color"), WrapColor.class)
            .defaultValue(new WrapColor(Formatting.RED))
            .build();
    public final NBTRef<WrapColor> openedAppleChestColor = builder(
                    root.add("opened-apple-chest-color"), WrapColor.class)
            .defaultValue(new WrapColor("#C0C0C0"))
            .build();

    public final RenderCollector<Box> boxCollector = RenderCollectors.createBoxCollector(true, false, false);
    public final RenderCollector<Vec3d> tracerCollector = RenderCollectors.createTracerCollector();

    private final Map<ChunkPos, List<AppleChest>> seedOnlyApples = new ConcurrentHashMap<>();
    private final Set<ChunkPos> seedOnlyRequested = ConcurrentHashMap.newKeySet();
    private final Set<Future<?>> pendingTasks = ConcurrentHashMap.newKeySet();
    private final Map<BlockPos, IXWaypoint> waypoints = new HashMap<>();
    private final Map<BlockPos, WaypointSpec> committedWaypointSpecs = new HashMap<>();
    private final TimerExecutor missingSeedTimer = new TimerExecutor();
    private final ThreadLocal<TerrainChunkCache> terrainCaches = new ThreadLocal<>();
    /** Stage 1: chunks waiting for the cheap candidate and surface screening. */
    private final WorkQueue<ChunkPos> screenQueue =
            new WorkQueue<>(SCREEN_EXECUTOR, SCREEN_THREADS, pos -> pos, this::screenChunk);
    /** Stage 2: screened chunks whose candidates need terrain simulation. */
    private final WorkQueue<TerrainJob> terrainQueue =
            new WorkQueue<>(TERRAIN_EXECUTOR, TERRAIN_THREADS, TerrainJob::pos, this::simulateTerrainJob);
    /** Seed-only statistics, for diagnosing cost: chunks simulated on terrain or skipped as non-candidates. */
    private final AtomicLong seedOnlyTerrainChunks = new AtomicLong();

    private final AtomicLong seedOnlySkippedChunks = new AtomicLong();
    /** Candidate chunks whose every apple candidate lies above the surface bound. */
    private final AtomicLong seedOnlySurfaceSkippedChunks = new AtomicLong();

    private final AtomicLong terrainNoiseChunks = new AtomicLong();
    private final AtomicLong terrainNoiseCells = new AtomicLong();
    private final AtomicLong terrainCellSamplers = new AtomicLong();
    private final AtomicLong surfaceRejectedAttempts = new AtomicLong();
    private final AtomicLong terrainCarveLayers = new AtomicLong();
    private final AtomicLong terrainBandMisses = new AtomicLong();

    @Nullable
    private volatile OverworldTerrainContext terrainContext;

    @Nullable
    private volatile Context context;

    @Nullable
    private volatile ChunkPos lastPlayerChunk;

    private volatile long generation;
    private volatile boolean waypointDirty;
    private int scanTicks;

    @Nullable
    private ChunkPos lastScanCenter;

    private int lastScanDistance = -1;
    private int waypointTicks;
    private int colorRefreshTicks;

    @Nullable
    private ChunkPos lastWaypointChunk;

    public FeatureLabel() {
        super("FeatureLabel");
        bindFlag(enable);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onTick);
        registerListener(RenderListener.getRender3DEvent(), this::onRender3D);
        registerListener(XaeroHelper.getWorldSwitchPoint(), this::resetState);
        registerListener(Listener.getServerLeavePoint(), this::resetState);
    }

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        resetState();
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        resetState();
    }

    private void onTick() {
        boxCollector.clear();
        tracerCollector.clear();
        pendingTasks.removeIf(Future::isDone);
        if (!enable.get() || checkNull()) {
            return;
        }
        if (!World.OVERWORLD.equals(mc.world.getRegistryKey())) {
            if (context != null) {
                resetState();
            }
            return;
        }
        if (!WorldTasks.hasSeed()) {
            missingSeedTimer.run(100, () -> logI18N("message.module.feature-label.input-seed"));
            if (context != null) {
                resetState();
            }
            return;
        }
        long seed = WorldTasks.getSeed();
        LootVersion version = lootVersion.get();
        Context current = context;
        if (current == null || current.world() != mc.world || current.seed() != seed || current.version() != version) {
            resetState();
            current = new Context(generation, seed, version, mc.world);
            context = current;
        }

        BlockPos playerPos = mc.player.getBlockPos();
        ChunkPos playerChunk = new ChunkPos(playerPos);
        lastPlayerChunk = playerChunk;
        // Pruning, reordering and requests walk the whole search area, so they run only when the
        // player enters a new chunk (or the distance changes), but then immediately.
        boolean areaChanged = !playerChunk.equals(lastScanCenter) || distance.get() != lastScanDistance;
        if (areaChanged) {
            lastScanCenter = playerChunk;
            lastScanDistance = distance.get();
            scanTicks = SCAN_INTERVAL - 1;
            pruneCache(playerChunk);
            requestSeedOnlyChunks(current, playerChunk);
        } else if (scanTicks-- <= 0) {
            scanTicks = SCAN_INTERVAL - 1;
            if (seedOnlyRequested.isEmpty()) {
                requestSeedOnlyChunks(current, playerChunk);
            } else {
                ensureSeedOnlyWorkers(current);
            }
        }
        if (colorRefreshTicks-- <= 0) {
            colorRefreshTicks = WAYPOINT_COLOR_REFRESH_INTERVAL - 1;
            waypointDirty = true;
        }
        boolean crossedChunk = !playerChunk.equals(lastWaypointChunk);
        if (waypointTicks-- <= 0 || crossedChunk) {
            waypointTicks = WAYPOINT_SYNC_INTERVAL - 1;
            if (waypointDirty || crossedChunk) {
                syncWaypoints(playerPos);
                lastWaypointChunk = playerChunk;
            }
        }
        markNearbyChestsVisited(playerPos);
        if (labelEnchantedGoldenAppleChests.get()) {
            submitRender(playerPos);
        }
    }

    /** Marks the chunks of predicted apple chests within {@link #VISIT_DISTANCE} as visited. */
    private void markNearbyChestsVisited(BlockPos playerPos) {
        int chunkRadius = Math.ceilDiv(VISIT_DISTANCE, 16) + 1;
        ChunkPos center = new ChunkPos(playerPos);
        boolean changed = false;
        for (int x = center.x - chunkRadius; x <= center.x + chunkRadius; x++) {
            for (int z = center.z - chunkRadius; z <= center.z + chunkRadius; z++) {
                List<AppleChest> chests = seedOnlyApples.get(new ChunkPos(x, z));
                if (chests == null) {
                    continue;
                }
                for (AppleChest chest : chests) {
                    if (horizontalDistanceSquared(chest.position(), playerPos) < (long) VISIT_DISTANCE * VISIT_DISTANCE
                            && markChunkVisited(new ChunkPos(chest.position()))) {
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            waypointDirty = true;
        }
    }

    private static boolean markChunkVisited(ChunkPos chunkPos) {
        ChunkStorage storage = ServerStorage.getOrCreateChunkStorage(chunkPos);
        if (storage == null || Boolean.TRUE.equals(storage.get(VISITED_KEY, Codec.BOOL))) {
            return false;
        }
        storage.put(VISITED_KEY, true, Codec.BOOL);
        return true;
    }

    private static boolean isVisitedChunk(ChunkPos chunkPos) {
        ChunkStorage storage = ServerStorage.getChunkStorage(chunkPos);
        return storage != null && Boolean.TRUE.equals(storage.get(VISITED_KEY, Codec.BOOL));
    }

    // ---- seed-only prediction -------------------------------------------------------------------

    private void requestSeedOnlyChunks(Context current, ChunkPos center) {
        if (!isEnabled(FeatureType.DUNGEON)) {
            return;
        }
        int radius = Math.ceilDiv(distance.get(), 16);
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                if (isWithinDistance(pos, center) && seedOnlyRequested.add(pos)) {
                    chunks.add(pos);
                }
            }
        }
        for (ChunkPos pos : chunks) {
            screenQueue.add(pos);
        }
        ensureSeedOnlyWorkers(current);
    }

    private void clearSeedOnlyState() {
        seedOnlyApples.clear();
        seedOnlyRequested.clear();
        screenQueue.clear();
        terrainQueue.clear();
    }

    private void ensureSeedOnlyWorkers(Context current) {
        screenQueue.ensureWorkers(current);
        terrainQueue.ensureWorkers(current);
    }

    /** Stores a finished chunk; chunks that left the search area meanwhile are not stored. */
    private void completeSeedOnlyChunk(Context current, ChunkPos pos, @Nullable List<AppleChest> apples) {
        if (apples != null && isCurrentSeedOnly(current) && isInCache(pos)) {
            seedOnlyApples.put(pos, apples);
            if (!apples.isEmpty()) {
                waypointDirty = true;
            }
        }
    }

    /**
     * Stage 1. With {@code candidate-only}, only chunks where the no-terrain pass (every
     * attempt assumed to succeed after failed predecessors) found an apple go on, and only each
     * candidate's own stream up to the candidate is simulated: that decides whether the candidate
     * stands. Candidates above the surface bound cannot, so their chunks need no terrain at all.
     */
    private void screenChunk(Context current, ChunkPos pos) {
        if (!candidateOnly.get()) {
            terrainQueue.add(new TerrainJob(pos, null));
            terrainQueue.ensureWorkers(current);
            return;
        }
        FeatureTerrain terrain = featureTerrain(terrainCache(current), surfaceMargin.get());
        Map<DungeonFeatureConfig, Integer> limits = new HashMap<>();
        boolean anyCandidate = false;
        for (DungeonPrediction hypothesis : DungeonFeatureSimulator.simulateChunk(
                current.seed(), pos.x, pos.z, DungeonTerrain.UNKNOWN, current.version())) {
            if (!isCurrentSeedOnly(current)) {
                return;
            }
            boolean apple = hypothesis.chests().stream()
                    .anyMatch(chest -> chest.containsEnchantedGoldenApple().orElse(false));
            if (!apple) {
                continue;
            }
            anyCandidate = true;
            if (terrain.mayHaveTerrainAt(hypothesis.origin().down())) {
                limits.merge(hypothesis.feature(), hypothesis.attempt() + 1, Math::max);
            }
        }
        if (limits.isEmpty()) {
            (anyCandidate ? seedOnlySurfaceSkippedChunks : seedOnlySkippedChunks).incrementAndGet();
            completeSeedOnlyChunk(current, pos, List.of());
            return;
        }
        terrainQueue.add(new TerrainJob(pos, Map.copyOf(limits)));
        terrainQueue.ensureWorkers(current);
    }

    /** Stage 2: simulates the chunk's (limited) attempt streams on generated terrain. */
    private void simulateTerrainJob(Context current, TerrainJob job) {
        ChunkPos pos = job.pos();
        TerrainChunkCache cache = terrainCache(current);
        ToIntFunction<DungeonFeatureConfig> attemptLimit = job.attemptLimits() == null
                ? DungeonFeatureConfig::attemptsPerChunk
                : feature -> job.attemptLimits().getOrDefault(feature, 0);
        int[] band = readBand(cache, pos, attemptLimit);
        cache.setPrimaryChunk(pos.x, pos.z, band[0], band[1]);
        long noise = cache.noiseChunks();
        long cells = cache.noiseCells();
        long cellSamplers = cache.cellSamplers();
        long carves = cache.carveLayers();
        long misses = cache.bandMisses();
        List<DungeonPrediction> predictions = DungeonFeatureSimulator.simulateChunk(
                current.seed(),
                pos.x,
                pos.z,
                featureTerrain(cache, surfaceMargin.get()),
                current.version(),
                attemptLimit);
        seedOnlyTerrainChunks.incrementAndGet();
        terrainNoiseChunks.addAndGet(cache.noiseChunks() - noise);
        terrainNoiseCells.addAndGet(cache.noiseCells() - cells);
        terrainCellSamplers.addAndGet(cache.cellSamplers() - cellSamplers);
        terrainCarveLayers.addAndGet(cache.carveLayers() - carves);
        terrainBandMisses.addAndGet(cache.bandMisses() - misses);
        completeSeedOnlyChunk(current, pos, findAppleChests(current, predictions));
    }

    /**
     * The heights a terrain job can read, {@code [min, max)}. {@code DungeonFeature.generate} reads
     * from two below the origin (the block under the floor) to four above it (the ceiling). Each
     * stream's origins span its placed feature's height range, but {@code monster_room} attempts with
     * a floor above the chunk's surface bound are rejected before reading anything, which caps that
     * stream at the bound plus the room height. Reads outside the band are still served exactly,
     * only slower (see {@link TerrainChunkCache#setPrimaryChunk(int, int, int, int)}).
     */
    private int[] readBand(TerrainChunkCache cache, ChunkPos pos, ToIntFunction<DungeonFeatureConfig> attemptLimit) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int surfaceBound = cache.surfaceEstimate(pos.x, pos.z) + surfaceMargin.get();
        for (DungeonFeatureConfig feature : DungeonFeatureConfig.OVERWORLD) {
            if (attemptLimit.applyAsInt(feature) <= 0) {
                continue;
            }
            // A floor at y - 1 above the surface bound is rejected, so origins stop at bound + 1.
            int highestOrigin = Math.min(feature.maxYInclusive(), surfaceBound + 1);
            min = Math.min(min, feature.minY() - 2);
            max = Math.max(max, highestOrigin + 5);
        }
        return min > max ? new int[] {0, 1} : new int[] {min, max};
    }

    /** A screened chunk; {@code attemptLimits == null} simulates every attempt. */
    private record TerrainJob(ChunkPos pos, @Nullable Map<DungeonFeatureConfig, Integer> attemptLimits) {}

    /**
     * Pending work grouped by 4x4-chunk region and kept ordered by distance to the player.
     *
     * <p>When the player enters a new chunk, {@link #reorder} drops items that left the search area
     * and re-sorts the regions (and the items in each) around the new chunk, so regions entering
     * range overtake older, farther ones. Items added in between (new requests, or terrain jobs from
     * the screening stage) mark the order dirty; the next poll re-sorts around the player's current
     * chunk. Lists are sorted farthest first so that the nearest entry is removed from the end.
     *
     * <p>A worker takes up to {@link #SEED_ONLY_BATCH_SIZE} items per poll, nearest first, moving on
     * to the next region when one runs out. All access to the regions is under {@code regions}'s lock.
     */
    private final class WorkQueue<T> {
        private final ExecutorService executor;
        private final int threads;
        private final Function<T, ChunkPos> position;
        private final BiConsumer<Context, T> processor;
        private final Map<Long, Region<T>> regions = new HashMap<>();
        /** The regions, farthest first; valid around {@link #orderCenter} unless {@link #orderDirty}. */
        private final ArrayList<Region<T>> order = new ArrayList<>();

        private boolean orderDirty;

        @Nullable
        private ChunkPos orderCenter;

        private final AtomicInteger workers = new AtomicInteger();

        private WorkQueue(
                ExecutorService executor,
                int threads,
                Function<T, ChunkPos> position,
                BiConsumer<Context, T> processor) {
            this.executor = executor;
            this.threads = threads;
            this.position = position;
            this.processor = processor;
        }

        private void add(T item) {
            ChunkPos pos = position.apply(item);
            long key = ChunkPos.toLong(
                    Math.floorDiv(pos.x, SEED_ONLY_REGION_CHUNKS), Math.floorDiv(pos.z, SEED_ONLY_REGION_CHUNKS));
            synchronized (regions) {
                Region<T> region = regions.get(key);
                if (region == null) {
                    region = new Region<>(key);
                    regions.put(key, region);
                    order.add(region);
                }
                region.items.add(item);
                region.sorted = false;
                orderDirty = true;
            }
        }

        private void clear() {
            synchronized (regions) {
                regions.clear();
                order.clear();
                orderDirty = false;
                orderCenter = null;
            }
        }

        /**
         * Removes queued items whose chunk fails {@code keep}, drops empty regions and sorts the rest
         * by distance to {@code center}.
         */
        private void reorder(ChunkPos center, java.util.function.Predicate<ChunkPos> keep) {
            synchronized (regions) {
                order.removeIf(region -> {
                    region.items.removeIf(item -> !keep.test(position.apply(item)));
                    region.sorted = false;
                    if (region.items.isEmpty()) {
                        regions.remove(region.key);
                        return true;
                    }
                    return false;
                });
                sortOrder(center);
            }
        }

        private int pendingRegions() {
            synchronized (regions) {
                return regions.size();
            }
        }

        private int workers() {
            return workers.get();
        }

        private void ensureWorkers(Context current) {
            while (true) {
                int active = workers.get();
                if (active >= threads || pendingRegions() == 0) {
                    return;
                }
                if (workers.compareAndSet(active, active + 1)) {
                    pendingTasks.add(executor.submit(() -> run(current)));
                }
            }
        }

        private void run(Context current) {
            try {
                List<T> batch;
                while (isCurrentSeedOnly(current) && !(batch = pollBatch(SEED_ONLY_BATCH_SIZE)).isEmpty()) {
                    for (T item : batch) {
                        if (!isCurrentSeedOnly(current)) {
                            return;
                        }
                        ChunkPos pos = position.apply(item);
                        if (!isInCache(pos)) {
                            seedOnlyRequested.remove(pos);
                            continue;
                        }
                        try {
                            processor.accept(current, item);
                        } catch (Throwable throwable) {
                            Debug.getLogger().debug("Unable to predict seed-only dungeon loot", throwable);
                        }
                    }
                }
            } finally {
                workers.decrementAndGet();
            }
        }

        /** Removes and returns up to {@code limit} items, nearest to the player first. */
        private List<T> pollBatch(int limit) {
            ChunkPos center = lastPlayerChunk;
            if (center == null) {
                return List.of();
            }
            synchronized (regions) {
                if (orderDirty || !center.equals(orderCenter)) {
                    sortOrder(center);
                }
                List<T> batch = new ArrayList<>(Math.min(limit, 16));
                while (batch.size() < limit && !order.isEmpty()) {
                    Region<T> region = order.getLast();
                    if (!region.sorted) {
                        region.items.sort(
                                Comparator.comparingLong((T item) -> chunkDistanceSquared(position.apply(item), center))
                                        .reversed());
                        region.sorted = true;
                    }
                    while (batch.size() < limit && !region.items.isEmpty()) {
                        batch.add(region.items.removeLast());
                    }
                    if (region.items.isEmpty()) {
                        order.removeLast();
                        regions.remove(region.key);
                    }
                }
                return batch;
            }
        }

        /** Sorts the regions farthest first around {@code center}; items are sorted lazily on poll. */
        private void sortOrder(ChunkPos center) {
            order.sort(Comparator.comparingLong((Region<T> region) -> regionDistanceSquared(region.key, center))
                    .reversed());
            if (!center.equals(orderCenter)) {
                order.forEach(region -> region.sorted = false);
            }
            orderCenter = center;
            orderDirty = false;
        }
    }

    /** Queued items of one 4x4-chunk region; {@code sorted} means farthest first around the order center. */
    private static final class Region<T> {
        private final long key;
        private final ArrayList<T> items = new ArrayList<>();
        private boolean sorted;

        private Region(long key) {
            this.key = key;
        }
    }

    /** Squared chunk distance from a region's centre to {@code center}. */
    private static long regionDistanceSquared(long regionKey, ChunkPos center) {
        long dx = (long) ChunkPos.getPackedX(regionKey) * SEED_ONLY_REGION_CHUNKS
                + SEED_ONLY_REGION_CHUNKS / 2
                - center.x;
        long dz = (long) ChunkPos.getPackedZ(regionKey) * SEED_ONLY_REGION_CHUNKS
                + SEED_ONLY_REGION_CHUNKS / 2
                - center.z;
        return dx * dx + dz * dz;
    }

    /** The calling worker's terrain cache for this seed; generating terrain is the costly part. */
    private TerrainChunkCache terrainCache(Context current) {
        OverworldTerrainContext terrain = terrainContext(current);
        TerrainChunkCache cache = terrainCaches.get();
        if (cache == null || cache.context() != terrain) {
            cache = new TerrainChunkCache(terrain, TERRAIN_CACHE_CHUNKS);
            terrainCaches.set(cache);
        }
        return cache;
    }

    private FeatureTerrain featureTerrain(TerrainChunkCache chunks, int margin) {
        OverworldTerrainContext terrain = chunks.context();
        return new FeatureTerrain() {
            @Override
            public boolean mayHaveTerrainAt(BlockPos floor) {
                if (floor.getY() <= chunks.surfaceEstimate(floor.getX() >> 4, floor.getZ() >> 4) + margin) {
                    return true;
                }
                surfaceRejectedAttempts.incrementAndGet();
                return false;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return chunks.getBlockState(pos);
            }

            @Override
            public boolean mayBeSolid(BlockPos pos) {
                return chunks.mayBeSolid(pos);
            }

            @Override
            public void prefetch(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
                chunks.prefetch(minX, minY, minZ, maxX, maxY, maxZ);
            }

            @Override
            public boolean cannotReplace(BlockState state) {
                return terrain.featuresCannotReplace(state);
            }
        };
    }

    private OverworldTerrainContext terrainContext(Context current) {
        OverworldTerrainContext terrain = terrainContext;
        if (terrain != null && terrain.seed() == current.seed()) {
            return terrain;
        }
        synchronized (this) {
            terrain = terrainContext;
            if (terrain == null || terrain.seed() != current.seed()) {
                terrain = OverworldTerrainContext.create(current.seed());
                terrainContext = terrain;
            }
            return terrain;
        }
    }

    // ---- loot -----------------------------------------------------------------------------------

    /** Returns {@code null} when the context became stale part-way. */
    @Nullable
    private List<AppleChest> findAppleChests(Context current, List<DungeonPrediction> predictions) {
        List<AppleChest> apples = new ArrayList<>();
        for (DungeonPrediction prediction : predictions) {
            for (DungeonChest chest : prediction.chests()) {
                if (!isCurrent(current)) {
                    return null;
                }
                if (chest.containsEnchantedGoldenApple().orElse(false)) {
                    apples.add(new AppleChest(chest.position(), chest.lootSeed(), FeatureType.DUNGEON));
                }
            }
        }
        return List.copyOf(apples);
    }

    // ---- waypoints and rendering ----------------------------------------------------------------

    private List<AppleChest> visibleAppleChests() {
        List<AppleChest> chests = new ArrayList<>();
        for (List<AppleChest> chunkChests : seedOnlyApples.values()) {
            for (AppleChest chest : chunkChests) {
                if (isEnabled(chest.type())) {
                    chests.add(chest);
                }
            }
        }
        return chests;
    }

    private boolean isEnabled(FeatureType type) {
        return switch (type) {
            case DUNGEON -> dungeonEnable.get();
        };
    }

    private int colorId(FeatureType type) {
        return switch (type) {
            case DUNGEON -> dungeonColorId.get();
        };
    }

    private void syncWaypoints(BlockPos playerPos) {
        XaeroHelper helper = XaeroHelper.INSTANCE;
        if (helper == null) {
            return;
        }
        waypointDirty = false;
        long range = distance.get();
        Map<BlockPos, WaypointSpec> desired = new LinkedHashMap<>();
        List<AppleChest> inRange = new ArrayList<>();
        for (AppleChest chest : visibleAppleChests()) {
            if (horizontalDistanceSquared(chest.position(), playerPos) <= range * range) {
                inRange.add(chest);
            }
        }
        for (AppleChest chest : nearest(inRange, playerPos, maxWaypoints.get())) {
            desired.putIfAbsent(chest.position(), createWaypointSpec(chest));
        }
        for (Map.Entry<BlockPos, IXWaypoint> entry : List.copyOf(waypoints.entrySet())) {
            if (!desired.containsKey(entry.getKey())) {
                helper.removeWaypoint(entry.getValue());
                waypoints.remove(entry.getKey());
                committedWaypointSpecs.remove(entry.getKey());
            }
        }
        for (Map.Entry<BlockPos, WaypointSpec> entry : desired.entrySet()) {
            ensureWaypoint(helper, entry.getKey(), entry.getValue());
        }
    }

    /** Selects the {@code limit} nearest chests with a bounded max-heap instead of sorting every guess. */
    private static List<AppleChest> nearest(List<AppleChest> chests, BlockPos playerPos, int limit) {
        if (chests.size() <= limit) {
            return chests;
        }
        Comparator<AppleChest> byDistance =
                Comparator.comparingLong(chest -> horizontalDistanceSquared(chest.position(), playerPos));
        PriorityQueue<AppleChest> farthestFirst = new PriorityQueue<>(limit + 1, byDistance.reversed());
        for (AppleChest chest : chests) {
            farthestFirst.add(chest);
            if (farthestFirst.size() > limit) {
                farthestFirst.poll();
            }
        }
        return List.copyOf(farthestFirst);
    }

    private WaypointSpec createWaypointSpec(AppleChest chest) {
        BlockPos pos = chest.position();
        int color = hasChestHistory(pos) || isVisitedChunk(new ChunkPos(pos))
                ? Formatting.GRAY.ordinal()
                : colorId(chest.type());
        FeatureType type = chest.type();
        return new WaypointSpec(
                pos.getX(), pos.getY(), pos.getZ(), type.waypointName(), type.waypointInitials(), color);
    }

    private void ensureWaypoint(XaeroHelper helper, BlockPos key, WaypointSpec spec) {
        WaypointSpec previous = committedWaypointSpecs.get(key);
        if (spec.equals(previous)) {
            return;
        }
        IXWaypoint waypoint = waypoints.get(key);
        if (waypoint != null && previous != null && previous.initials().equals(spec.initials())) {
            helper.updateWaypoint(waypoint, current -> {
                current.setColor(spec.color());
                current.setName(spec.name());
            });
            committedWaypointSpecs.put(key, spec);
            return;
        }
        if (waypoint != null) {
            helper.removeWaypoint(waypoint);
            waypoints.remove(key);
            committedWaypointSpecs.remove(key);
        }
        waypoint = helper.submitWaypoint(
                this, spec.x(), spec.y(), spec.z(), spec.name(), spec.initials(), spec.color(), 0, true, true);
        if (waypoint != null) {
            waypoints.put(key, waypoint);
            committedWaypointSpecs.put(key, spec);
        }
    }

    private void submitRender(BlockPos playerPos) {
        double rangeSquared = (double) renderRange.get() * renderRange.get();
        int color = renderColor.get().withAlpha(255);
        int openedColor = openedAppleChestColor.get().withAlpha(255);
        int chunkRadius = Math.ceilDiv(renderRange.get(), 16) + 1;
        ChunkPos center = new ChunkPos(playerPos);
        for (int x = center.x - chunkRadius; x <= center.x + chunkRadius; x++) {
            for (int z = center.z - chunkRadius; z <= center.z + chunkRadius; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                List<AppleChest> chests = seedOnlyApples.get(chunk);
                if (chests == null) {
                    continue;
                }
                for (AppleChest chest : chests) {
                    BlockPos pos = chest.position();
                    if (!isEnabled(chest.type()) || pos.getSquaredDistance(playerPos) > rangeSquared) {
                        continue;
                    }
                    int chestColor = hasChestHistory(pos) ? openedColor : color;
                    boxCollector.submit(chestBox(pos), chestColor);
                    tracerCollector.submit(pos.toCenterPos(), chestColor);
                }
            }
        }
    }

    private static long horizontalDistanceSquared(BlockPos pos, BlockPos center) {
        long dx = (long) pos.getX() - center.getX();
        long dz = (long) pos.getZ() - center.getZ();
        return dx * dx + dz * dz;
    }

    private void onRender3D(Event<Render3D> event) {
        if (checkNull() || !enable.get()) {
            return;
        }
        RenderUtils.startDrawVirtual(event.context().stack());
        try {
            boxCollector.render3D(event.context().stack(), event.context.partialTicks());
            tracerCollector.render3D(event.context().stack(), event.context.partialTicks());
        } finally {
            RenderUtils.stopDrawVirtual(event.context().stack());
        }
    }

    private Box chestBox(BlockPos pos) {
        if (!mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return new Box(pos);
        }
        VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
        return shape.isEmpty() ? new Box(pos) : shape.getBoundingBox().offset(pos);
    }

    private boolean hasChestHistory(BlockPos pos) {
        ChestHistory history = ChestHistory.INSTANCE;
        if (history == null || mc.world == null) {
            return false;
        }
        ContainerPosition single = ContainerPosition.ofSingle(mc.world, pos);
        if (history.getEntry(single) != null) {
            return true;
        }
        ContainerPosition resolved = ContainerPosition.resolve(mc.world, pos);
        return !resolved.equals(single) && history.getEntry(resolved) != null;
    }

    // ---- state ----------------------------------------------------------------------------------

    /**
     * Drops results and queued work outside the cache radius around {@code center} and reorders the
     * remaining work by distance to it. Called when the player enters a new chunk: queued work is
     * removed then rather than when a worker reaches it, so moving far away cannot leave the pools
     * holding a backlog of chunks the player will never see; dropped chunks are requested again if
     * the player comes back.
     */
    private void pruneCache(ChunkPos center) {
        seedOnlyApples.keySet().removeIf(pos -> !isInCache(pos, center));
        seedOnlyRequested.removeIf(pos -> !isInCache(pos, center));
        screenQueue.reorder(center, pos -> isInCache(pos, center));
        terrainQueue.reorder(center, pos -> isInCache(pos, center));
    }

    private boolean isInCache(ChunkPos pos) {
        ChunkPos center = lastPlayerChunk;
        return center != null && isInCache(pos, center);
    }

    private boolean isInCache(ChunkPos pos, ChunkPos center) {
        long limit = distance.get() + CACHE_MARGIN_BLOCKS;
        return chunkDistanceSquared(pos, center) * 256L <= limit * limit;
    }

    private boolean isWithinDistance(ChunkPos pos, ChunkPos center) {
        long limit = distance.get() + 16L;
        return chunkDistanceSquared(pos, center) * 256L <= limit * limit;
    }

    private static long chunkDistanceSquared(ChunkPos pos, ChunkPos center) {
        long dx = pos.x - center.x;
        long dz = pos.z - center.z;
        return dx * dx + dz * dz;
    }

    private boolean isCurrent(Context current) {
        return enable.get() && context == current && generation == current.generation();
    }

    private boolean isCurrentSeedOnly(Context current) {
        return isCurrent(current);
    }

    private void resetState() {
        generation++;
        context = null;
        lastPlayerChunk = null;
        clearSeedOnlyState();
        for (Future<?> future : pendingTasks) {
            future.cancel(true);
        }
        pendingTasks.clear();
        boxCollector.clear();
        tracerCollector.clear();
        XaeroHelper helper = XaeroHelper.INSTANCE;
        if (helper != null && !waypoints.isEmpty()) {
            helper.removeSub(this);
        }
        waypoints.clear();
        committedWaypointSpecs.clear();
        scanTicks = 0;
        lastScanCenter = null;
        lastScanDistance = -1;
        waypointTicks = 0;
        colorRefreshTicks = 0;
        lastWaypointChunk = null;
        waypointDirty = true;
    }

    private record Context(long generation, long seed, LootVersion version, ClientWorld world) {}

    /**
     * Kind of world feature a prediction came from. Only dungeons are predicted for now; the type is
     * recorded on every result so that other features can share the cache, waypoints and rendering.
     */
    public enum FeatureType {
        DUNGEON("Dungeon", "DG");

        private final String waypointName;
        private final String waypointInitials;

        FeatureType(String waypointName, String waypointInitials) {
            this.waypointName = waypointName;
            this.waypointInitials = waypointInitials;
        }

        public String waypointName() {
            return waypointName;
        }

        public String waypointInitials() {
            return waypointInitials;
        }
    }

    /** A predicted enchanted-golden-apple chest and the feature type that generates it. */
    private record AppleChest(BlockPos position, long lootSeed, FeatureType type) {}

    private record WaypointSpec(int x, int y, int z, String name, String initials, int color) {}
}
