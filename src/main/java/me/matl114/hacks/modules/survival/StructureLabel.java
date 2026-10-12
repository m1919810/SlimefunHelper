package me.matl114.hacks.modules.survival;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.Render3D;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.inv.ChestHistory;
import me.matl114.hacks.modules.task.ServerStorage;
import me.matl114.hacks.utils.config.WrapColor;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.hacks.utils.world.ChunkStorage;
import me.matl114.hooks.impl.xaerowaypoints.IXWaypoint;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.utils.Debug;
import me.matl114.utils.render.RenderCollector;
import me.matl114.utils.structures.ChunkRange;
import me.matl114.utils.structures.CubiomesNetherBiomeSampler;
import me.matl114.utils.structures.CubiomesOverworldBiomeSampler;
import me.matl114.utils.structures.EndCityResult;
import me.matl114.utils.structures.Populator;
import me.matl114.utils.structures.PredictedChest;
import me.matl114.utils.structures.PredictedChestAlternatives;
import me.matl114.utils.structures.StructureConfig;
import me.matl114.utils.structures.StructureDecorationIndex;
import me.matl114.utils.structures.StructurePosition;
import me.matl114.utils.world.ContainerPosition;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class StructureLabel extends BaseModule {
    private static final long CACHE_BLOCK_SIZE = 1024L;
    private static final int ANCIENT_LOOT_SEARCH_MARGIN = 256;
    private static final int CACHE_CHUNK_SIZE = 64;
    private static final int STRUCTURE_EXECUTOR_THREAD_COUNT =
            Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
    private static final int LOOT_EXECUTOR_THREAD_COUNT =
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    private static final ExecutorService structureExecutor =
            Executors.newFixedThreadPool(STRUCTURE_EXECUTOR_THREAD_COUNT, StructureLabel::daemonThread);
    private static final ThreadPoolExecutor lootExecutor = new ThreadPoolExecutor(
            LOOT_EXECUTOR_THREAD_COUNT,
            LOOT_EXECUTOR_THREAD_COUNT,
            0L,
            TimeUnit.MILLISECONDS,
            new PriorityBlockingQueue<>(),
            StructureLabel::daemonThread);
    private static final AtomicLong lootTaskSequence = new AtomicLong();
    private static final Identifier END_CITIES = Identifier.ofVanilla("end_cities");
    private static final Identifier ANCIENT_CITIES = Identifier.ofVanilla("ancient_cities");
    private static final Identifier DESERT_PYRAMIDS = Identifier.ofVanilla("desert_pyramids");
    private static final Identifier RUINED_PORTALS = Identifier.ofVanilla("ruined_portals");
    private static final Identifier DESERT_PYRAMID_LOOT = Identifier.ofVanilla("chests/desert_pyramid");
    private static final Identifier RUINED_PORTAL_LOOT = Identifier.ofVanilla("chests/ruined_portal");
    private static final String VISITED_STRUCTURE_KEY = "slimefunhelper:structure_label_visited";

    private static Thread daemonThread(Runnable task) {
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY);
        return thread;
    }

    private static Future<?> submitStructureTask(Runnable task) {
        return structureExecutor.submit(task);
    }

    public final ModulePath root = makePath(Configs.SURVIVAL_CONFIG, "structure-label");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final FlagRef labelNonElytraEndCities = flagBuilder(root.add("label-non-elytra-end-cities"))
            .updateListener(this::refreshWaypoints)
            .build();

    public final FlagRef labelElytraEndCities = flagBuilder(root.add("label-elytra-end-cities"))
            .updateListener(this::refreshWaypoints)
            .build();

    public final FlagRef labelAncientCities = flagBuilder(root.add("label-ancient-cities"))
            .updateListener(this::refreshWaypoints)
            .build();

    public final FlagRef labelOtherEnchantedGoldStructures = flagBuilder(
                    root.add("label-other-enchanted-gold-structures"))
            .updateListener(this::onOtherLootPredictionSettingChanged)
            .build();

    public final FlagRef labelEnchantedGoldenAppleChests =
            flagBuilder(root.add("label-enchanted-golden-apple-chests")).build();

    public final IntRef cacheInvalidationRange = intBuilder(root.add("cache-invalidation-range"))
            .defaultValue(2048)
            .validator(Configs.intRange(1024, 32768))
            .build();

    public final IntRef renderRange = intBuilder(root.add("render-range"))
            .defaultValue(128)
            .validator(Configs.intRange(16, 512))
            .build();

    public final IntRef endCityColorId = intBuilder(root.add("end-city-color-id"))
            .defaultValue(Formatting.YELLOW.ordinal())
            .validator(Configs.intRange(0, 15))
            .build();

    public final IntRef elytraColorId = intBuilder(root.add("elytra-color-id"))
            .defaultValue(Formatting.GREEN.ordinal())
            .validator(Configs.intRange(0, 15))
            .build();

    public final IntRef ancientCityColorId = intBuilder(root.add("ancient-city-color-id"))
            .defaultValue(Formatting.AQUA.ordinal())
            .validator(Configs.intRange(0, 15))
            .build();

    public final IntRef desertPyramidColorId = intBuilder(root.add("desert-pyramid-color-id"))
            .defaultValue(Formatting.GOLD.ordinal())
            .validator(Configs.intRange(0, 15))
            .build();

    public final IntRef ruinedPortalColorId = intBuilder(root.add("ruined-portal-color-id"))
            .defaultValue(Formatting.RED.ordinal())
            .validator(Configs.intRange(0, 15))
            .build();

    public final NBTRef<WrapColor> openedAppleChestColor = builder(
                    root.add("opened-apple-chest-color"), WrapColor.class)
            .defaultValue(new WrapColor("#C0C0C0"))
            .updateListener(color -> waypointDirty = true)
            .build();

    public final NBTRef<WrapColor> renderColor = builder(root.add("render-color"), WrapColor.class)
            .defaultValue(new WrapColor(Formatting.RED))
            .build();

    public final RenderCollector<Box> boxCollector = RenderCollectors.createBoxCollector(true, false, false);
    public final RenderCollector<Vec3d> tracerCollector = RenderCollectors.createTracerCollector();

    private final Map<AreaKey, List<EndCityResult>> cachedAreas = new ConcurrentHashMap<>();
    private final Map<AreaKey, List<StructurePosition>> cachedStructures = new ConcurrentHashMap<>();
    private final Map<AreaKey, List<PredictedChestAlternatives>> cachedPredictedAppleChests = new ConcurrentHashMap<>();
    private final Map<AreaKey, List<PredictedChestAlternatives>> cachedOtherPredictedAppleChests =
            new ConcurrentHashMap<>();
    private final Map<AreaKey, List<PredictedChest>> cachedAncientCityChests = new ConcurrentHashMap<>();
    private final Map<AreaKey, List<PredictedChestContents>> cachedAncientCityLootContents = new ConcurrentHashMap<>();
    private final Map<AreaKey, List<PredictedChestContents>> cachedOtherLootContents = new ConcurrentHashMap<>();
    private final Set<StructureKey> checkedAncientCities = ConcurrentHashMap.newKeySet();
    private final Set<StructureKey> viableAncientCities = ConcurrentHashMap.newKeySet();
    private final Set<StructureKey> checkedDesertPyramids = ConcurrentHashMap.newKeySet();
    private final Set<StructureKey> viableDesertPyramids = ConcurrentHashMap.newKeySet();
    private final Set<AreaKey> pendingAreas = ConcurrentHashMap.newKeySet();
    private final Set<AreaKey> pendingAreaDetails = ConcurrentHashMap.newKeySet();
    private final Set<AreaKey> pendingOtherLootAreas = ConcurrentHashMap.newKeySet();
    private final Set<AreaKey> checkedOtherLootAreas = ConcurrentHashMap.newKeySet();
    private final Set<AreaKey> pendingAncientChestAreas = ConcurrentHashMap.newKeySet();
    private final Set<LootAreaTaskKey> pendingLootAreaTasks = ConcurrentHashMap.newKeySet();
    private final Set<Future<?>> pendingTasks = ConcurrentHashMap.newKeySet();
    private final Map<CityKey, EndCityResult> cachedCities = new ConcurrentHashMap<>();
    private final Map<Thread, LootBiomeSamplers> lootBiomeSamplerCache = new ConcurrentHashMap<>();
    private final Map<StructureKey, IXWaypoint> waypoints = new ConcurrentHashMap<>();
    private final Map<StructureKey, WaypointSpec> committedWaypointSpecs = new ConcurrentHashMap<>();
    private final Map<AppleChestWaypointKey, IXWaypoint> appleChestWaypoints = new ConcurrentHashMap<>();
    private final Map<AppleChestWaypointKey, WaypointSpec> committedAppleChestWaypointSpecs = new ConcurrentHashMap<>();
    private final Set<ChunkPos> dirtyPos = ConcurrentHashMap.newKeySet();

    @Nullable
    private volatile GenerationContext context;

    @Nullable
    private volatile ContextRequest pendingContextRequest;

    private volatile long generation;
    private volatile long otherLootGeneration;
    private long waypointSyncTicks;
    private volatile boolean waypointDirty;
    private volatile AreaKey lastPlayerArea;
    private long lastRequestedRadius = -1L;

    private void refreshWaypoints(Boolean enabled) {
        waypointDirty = true;
    }

    private void onOtherLootPredictionSettingChanged(Boolean enabled) {
        otherLootGeneration++;
        pendingOtherLootAreas.clear();
        checkedOtherLootAreas.clear();
        cachedOtherPredictedAppleChests.clear();
        cachedOtherLootContents.clear();
        waypointDirty = true;
        GenerationContext current = context;
        AreaKey center = lastPlayerArea;
        if (enabled && current != null && center != null) {
            long predictionGeneration = otherLootGeneration;
            Future<?> future = scheduleLootTask(
                    center, () -> requestOtherLootPredictionsForCachedAreas(current, predictionGeneration));
            pendingTasks.add(future);
        }
    }

    public StructureLabel() {
        super("StructureLabel");
        bindFlag(enable);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostGameTick(), this::onTick);
        registerListener(RenderListener.getRender3DEvent(), this::onRender3D);
        registerListener(XaeroHelper.getWorldSwitchPoint(), this::onWorldSwitch);
        registerListener(Listener.getServerLeavePoint(), this::onServerLeave);
    }

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        resetState();
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        resetState(false, true);
    }

    private void onWorldSwitch(Event<World> event) {
        resetState(true, true);
    }

    private void onServerLeave(Event<Void> event) {
        resetState(true, true);
    }

    private void onTick(Event<ClientPlayerEntity> event) {
        boxCollector.clear();
        tracerCollector.clear();
        cleanupFinishedTasks();
        if (!enable.get() || checkNull()) {
            return;
        }
        if (!isSupportedDimension()) {
            if (context != null) {
                resetState();
            }
            return;
        }
        if (!prepareContextAsync()) {
            return;
        }
        AreaKey playerArea = AreaKey.fromBlock(mc.player.getBlockPos());
        long radius = currentCacheRadiusAreas();
        AreaKey previousPlayerArea = lastPlayerArea;
        boolean movedArea = previousPlayerArea != null && !playerArea.equals(previousPlayerArea);
        if (!Objects.equals(playerArea, lastPlayerArea)
                || (cachedStructures.isEmpty() && pendingAreas.isEmpty())
                || radius != lastRequestedRadius) {
            lastPlayerArea = playerArea;
            requestAreasAround(playerArea);
            lastRequestedRadius = radius;
            if (movedArea) {
                Future<?> future = submitStructureTask(() -> pruneCache(playerArea));
                pendingTasks.add(future);
            }
        }
        markNearbyStructuresVisited(mc.player.getBlockPos());
        requestAncientCityChestsNearPlayer(mc.player.getBlockPos());
        if (++waypointSyncTicks >= 20) {
            waypointSyncTicks = 0;
            if (waypointDirty) {
                rebuildWaypoints();
            }
        }
        submitNearbyKnownAppleChests();
    }

    private boolean prepareContextAsync() {
        RegistryKey<World> worldKey = mc.world.getRegistryKey();
        ContextRequest pending = pendingContextRequest;
        if (!SeedOre.INSTANCE.hasCurrentSeed()) {
            if (context != null || pending != null) {
                resetState(false, true);
            }
            return false;
        }
        long seed = SeedOre.INSTANCE.getCurrentSeed();
        if (context != null && context.worldKey().equals(worldKey) && context.seed() == seed) {
            return true;
        }
        if (pending != null && pending.worldKey().equals(worldKey) && pending.seed() == seed) {
            return false;
        }
        if (context != null || pending != null) {
            resetState();
        }
        ContextRequest request = new ContextRequest(generation, seed, worldKey);
        pendingContextRequest = request;
        Future<?> future = submitStructureTask(() -> {
            try {
                Map<Identifier, StructureConfig> configs = new java.util.LinkedHashMap<>();
                Map<Identifier, StructureConfig> vanillaConfigs = StructureConfig.vanillaConfigs();
                for (Identifier id : vanillaConfigs.keySet()) {
                    if (isSupportedStructure(id, worldKey)) {
                        configs.put(id, vanillaConfigs.get(id));
                    }
                }
                CubiomesOverworldBiomeSampler biomeSampler = null;
                if (World.OVERWORLD.equals(worldKey)) {
                    biomeSampler = CubiomesOverworldBiomeSampler.create(seed);
                }
                Map<Identifier, StructureDecorationIndex.Index> structureIndices =
                        StructureDecorationIndex.collectDefault();
                if (!isCurrent(request)) {
                    return;
                }
                Populator populator = new Populator(seed, structureIndices);
                context = new GenerationContext(
                        request.generation(), seed, worldKey, Map.copyOf(configs), populator, biomeSampler);
                pendingContextRequest = null;
                waypointDirty = true;
                waypointSyncTicks = 0;
                lastPlayerArea = null;
                lastRequestedRadius = -1L;
            } catch (Throwable throwable) {
                Debug.info("Unable to prepare structure labels", throwable);
                if (pendingContextRequest == request) {
                    pendingContextRequest = null;
                }
            }
        });
        pendingTasks.add(future);
        return false;
    }

    private void requestAreasAround(AreaKey center) {
        GenerationContext current = context;
        if (current == null) {
            return;
        }
        long radius = currentCacheRadiusAreas();
        List<AreaKey> areas = new ArrayList<>();
        for (long areaX = center.x() - radius; areaX <= center.x() + radius; areaX++) {
            for (long areaZ = center.z() - radius; areaZ <= center.z() + radius; areaZ++) {
                AreaKey area = new AreaKey(areaX, areaZ);
                if (!cachedStructures.containsKey(area) && pendingAreas.add(area)) {
                    areas.add(area);
                }
            }
        }
        areas.sort(
                (left, right) -> Long.compare(areaDistanceSquared(left, center), areaDistanceSquared(right, center)));
        if (areas.isEmpty()) {
            return;
        }
        for (AreaKey area : areas) {
            Future<?> future = submitStructureTask(() -> requestAreaBatch(List.of(area), current));
            pendingTasks.add(future);
        }
    }

    private static long areaDistanceSquared(AreaKey area, AreaKey center) {
        long dx = area.x() - center.x();
        long dz = area.z() - center.z();
        return dx * dx + dz * dz;
    }

    private long currentCacheRadiusAreas() {
        return (cacheInvalidationRange.get() + CACHE_BLOCK_SIZE - 1L) / CACHE_BLOCK_SIZE;
    }

    private boolean isWithinCurrentCache(AreaKey area) {
        AreaKey center = lastPlayerArea;
        if (center == null) {
            return false;
        }
        long radius = currentCacheRadiusAreas();
        return Math.abs(area.x() - center.x()) <= radius && Math.abs(area.z() - center.z()) <= radius;
    }

    private boolean isCurrentArea(AreaKey area, GenerationContext current) {
        return isCurrent(current) && isWithinCurrentCache(area);
    }

    private boolean isCurrentOtherLootPrediction(GenerationContext current, long predictionGeneration) {
        return labelOtherEnchantedGoldStructures.get()
                && predictionGeneration == otherLootGeneration
                && isCurrent(current);
    }

    private boolean isCurrentOtherLootArea(AreaKey area, GenerationContext current, long predictionGeneration) {
        return isCurrentOtherLootPrediction(current, predictionGeneration)
                && isWithinCurrentCache(area)
                && cachedStructures.containsKey(area);
    }

    private void requestAreaBatch(List<AreaKey> areas, GenerationContext current) {
        Populator populator = current.populator();
        List<AreaKey> completedAreas = new ArrayList<>();
        for (AreaKey area : areas) {
            try {
                if (!isCurrentArea(area, current)) {
                    pendingAreas.remove(area);
                    continue;
                }
                ChunkRange range = areaChunkRange(area);
                List<StructurePosition> structures = new ArrayList<>();
                for (StructureConfig config : current.configs().values()) {
                    if (!isCurrentArea(area, current)) {
                        break;
                    }
                    List<StructurePosition> generated = populator.generate(config, range);
                    structures.addAll(generated);
                    if (ANCIENT_CITIES.equals(config.structureSetId())) {
                        cacheAncientCityViability(area, generated, current);
                    }
                }
                if (!isCurrentArea(area, current)) {
                    pendingAreas.remove(area);
                    continue;
                }
                List<StructurePosition> immutableStructures = List.copyOf(structures);
                cacheDesertPyramidViability(area, immutableStructures, current);
                if (!isCurrentArea(area, current)) {
                    pendingAreas.remove(area);
                    continue;
                }
                completeAreaStructures(area, current, immutableStructures);
                if (labelOtherEnchantedGoldStructures.get()) {
                    requestOtherLootPredictions(area, immutableStructures, current, otherLootGeneration);
                }
                completedAreas.add(area);
            } catch (Throwable throwable) {
                Debug.getLogger().debug("Unable to generate structure positions", throwable);
                if (context == current) {
                    pendingAreas.remove(area);
                }
            }
        }

        for (AreaKey area : completedAreas) {
            if (!isCurrentArea(area, current)) {
                pendingAreas.remove(area);
                continue;
            }
            requestAreaDetails(area, areaChunkRange(area), current);
        }
    }

    private void requestOtherLootPredictionsForCachedAreas(GenerationContext current, long predictionGeneration) {
        if (current == null || !isCurrentOtherLootPrediction(current, predictionGeneration)) {
            return;
        }
        AreaKey center = lastPlayerArea;
        List<Map.Entry<AreaKey, List<StructurePosition>>> entries = new ArrayList<>(cachedStructures.entrySet());
        if (center != null) {
            entries.sort((left, right) -> Long.compare(
                    areaDistanceSquared(left.getKey(), center), areaDistanceSquared(right.getKey(), center)));
        }
        for (Map.Entry<AreaKey, List<StructurePosition>> entry : entries) {
            if (!isCurrentOtherLootPrediction(current, predictionGeneration)) {
                return;
            }
            if (!isCurrentOtherLootArea(entry.getKey(), current, predictionGeneration)) {
                continue;
            }
            requestOtherLootPredictions(entry.getKey(), entry.getValue(), current, predictionGeneration);
        }
    }

    private void requestAreaDetails(AreaKey area, ChunkRange range, GenerationContext current) {
        if (!pendingAreaDetails.add(area)) {
            return;
        }
        Future<?> future = submitStructureTask(() -> {
            try {
                if (!isCurrentArea(area, current)) {
                    return;
                }
                Populator populator = current.populator();
                List<EndCityResult> endCities = current.configs().containsKey(END_CITIES)
                        ? populator.generateEndCities(current.configs().get(END_CITIES), range)
                        : List.of();
                if (!isCurrentArea(area, current)) {
                    return;
                }
                completeAreaDetails(area, current, endCities);
            } catch (Throwable throwable) {
                Debug.getLogger().debug("Unable to generate detailed structure labels", throwable);
                if (context == current) {
                    pendingAreaDetails.remove(area);
                }
            } finally {
                if (!isCurrentArea(area, current)) {
                    pendingAreaDetails.remove(area);
                }
            }
        });
        pendingTasks.add(future);
    }

    private void requestOtherLootPredictions(
            AreaKey area, List<StructurePosition> structures, GenerationContext current, long predictionGeneration) {
        if (!isCurrentOtherLootArea(area, current, predictionGeneration)) {
            return;
        }
        boolean hasLootStructure = structures.stream().anyMatch(structure -> {
            Identifier structureSet = structure.config().structureSetId();
            return RUINED_PORTALS.equals(structureSet)
                    || (DESERT_PYRAMIDS.equals(structureSet)
                            && viableDesertPyramids.contains(StructureKey.of(structure)));
        });
        if (!hasLootStructure) {
            if (isCurrentOtherLootArea(area, current, predictionGeneration)) {
                checkedOtherLootAreas.add(area);
            }
            return;
        }
        if (checkedOtherLootAreas.contains(area) || !pendingOtherLootAreas.add(area)) {
            return;
        }
        Future<?> future = scheduleLootTask(area, () -> {
            try {
                if (!isCurrentOtherLootArea(area, current, predictionGeneration)) {
                    return;
                }
                Populator populator = current.populator();
                List<PredictedChestAlternatives> chests = new ArrayList<>();
                LootBiomeSamplers samplers = getLootBiomeSamplers(current);
                CubiomesOverworldBiomeSampler overworldSampler = samplers.overworld();
                CubiomesNetherBiomeSampler netherSampler = samplers.nether();
                if (!isCurrentOtherLootArea(area, current, predictionGeneration)) {
                    return;
                }
                if (overworldSampler != null && current.configs().containsKey(DESERT_PYRAMIDS)) {
                    for (StructurePosition pyramid : structures) {
                        if (!isCurrentOtherLootArea(area, current, predictionGeneration)) {
                            return;
                        }
                        if (DESERT_PYRAMIDS.equals(pyramid.config().structureSetId())
                                && viableDesertPyramids.contains(StructureKey.of(pyramid))) {
                            chests.addAll(populator
                                    .generateDesertPyramidChestAlternatives(pyramid, overworldSampler)
                                    .orElse(List.of()));
                        }
                    }
                }
                if (current.configs().containsKey(RUINED_PORTALS)) {
                    if (overworldSampler != null || netherSampler != null) {
                        for (StructurePosition portal : structures) {
                            if (!isCurrentOtherLootArea(area, current, predictionGeneration)) {
                                return;
                            }
                            if (!RUINED_PORTALS.equals(portal.config().structureSetId())) {
                                continue;
                            }
                            List<PredictedChest> portalChests = World.NETHER.equals(current.worldKey())
                                    ? netherSampler == null
                                            ? List.of()
                                            : populator.generateRuinedPortalChests(portal, netherSampler)
                                    : overworldSampler == null
                                            ? List.of()
                                            : populator.generateRuinedPortalChests(portal, overworldSampler);
                            for (PredictedChest chest : portalChests) {
                                chests.add(asAlternatives(chest));
                            }
                        }
                    }
                }
                if (!isCurrentOtherLootArea(area, current, predictionGeneration)) {
                    return;
                }
                scheduleAppleChestMarking(area, current, chests, true, true, predictionGeneration);
                if (isCurrentOtherLootArea(area, current, predictionGeneration)) {
                    checkedOtherLootAreas.add(area);
                }
            } catch (Throwable throwable) {
                Debug.getLogger().debug("Unable to predict structure loot", throwable);
            } finally {
                if (context == current && predictionGeneration == otherLootGeneration) {
                    pendingOtherLootAreas.remove(area);
                }
            }
        });
        pendingTasks.add(future);
    }

    private void requestAncientCityChestsNearPlayer(BlockPos playerPos) {
        GenerationContext current = context;
        if (current == null
                || current.biomeSampler() == null
                || !current.configs().containsKey(ANCIENT_CITIES)) {
            return;
        }
        long margin = ANCIENT_LOOT_SEARCH_MARGIN;
        long minAreaX = Math.floorDiv((long) playerPos.getX() - margin, CACHE_BLOCK_SIZE);
        long maxAreaX = Math.floorDiv((long) playerPos.getX() + margin, CACHE_BLOCK_SIZE);
        long minAreaZ = Math.floorDiv((long) playerPos.getZ() - margin, CACHE_BLOCK_SIZE);
        long maxAreaZ = Math.floorDiv((long) playerPos.getZ() + margin, CACHE_BLOCK_SIZE);
        List<AreaKey> areas = new ArrayList<>();
        for (long areaX = minAreaX; areaX <= maxAreaX; areaX++) {
            for (long areaZ = minAreaZ; areaZ <= maxAreaZ; areaZ++) {
                AreaKey area = new AreaKey(areaX, areaZ);
                if (isWithinCurrentCache(area)
                        && cachedStructures.containsKey(area)
                        && !cachedAncientCityChests.containsKey(area)) {
                    areas.add(area);
                }
            }
        }
        AreaKey center = AreaKey.fromBlock(playerPos);
        areas.sort(
                (left, right) -> Long.compare(areaDistanceSquared(left, center), areaDistanceSquared(right, center)));
        for (AreaKey area : areas) {
            requestAncientCityChests(area, current);
        }
    }

    private void requestAncientCityChests(AreaKey area, GenerationContext current) {
        if (cachedAncientCityChests.containsKey(area) || !pendingAncientChestAreas.add(area)) {
            return;
        }
        Future<?> future = submitStructureTask(() -> {
            try {
                if (!isCurrentArea(area, current)) {
                    return;
                }
                ChunkRange range = areaChunkRange(area);
                Populator populator = current.populator();
                List<PredictedChest> chests = populator.generateAncientCityChests(
                        populator.generateAncientCityPiecesNear(
                                current.configs().get(ANCIENT_CITIES), range, current.biomeSampler()),
                        range,
                        current.biomeSampler());
                if (isCurrentArea(area, current)) {
                    cachedAncientCityChests.put(area, List.copyOf(chests));
                    if (!isCurrentArea(area, current)) {
                        return;
                    }
                    cachedAncientCityLootContents.put(area, List.of());
                    scheduleAppleChestMarking(
                            area,
                            current,
                            chests.stream().map(StructureLabel::asAlternatives).toList(),
                            false,
                            true);
                }
            } catch (Throwable throwable) {
                Debug.getLogger().debug("Unable to generate nearby Ancient City chests", throwable);
            } finally {
                if (context == current) {
                    pendingAncientChestAreas.remove(area);
                }
            }
        });
        pendingTasks.add(future);
    }

    private void scheduleAppleChestMarking(
            AreaKey area, GenerationContext current, List<PredictedChestAlternatives> chests, boolean lowPriority) {
        scheduleAppleChestMarking(area, current, chests, lowPriority, false);
    }

    private void scheduleAppleChestMarking(
            AreaKey area,
            GenerationContext current,
            List<PredictedChestAlternatives> chests,
            boolean lowPriority,
            boolean cacheContents) {
        scheduleAppleChestMarking(area, current, chests, lowPriority, cacheContents, otherLootGeneration);
    }

    private void scheduleAppleChestMarking(
            AreaKey area,
            GenerationContext current,
            List<PredictedChestAlternatives> chests,
            boolean lowPriority,
            boolean cacheContents,
            long predictionGeneration) {
        if (chests.isEmpty()) {
            return;
        }
        LootAreaTaskKey taskKey =
                new LootAreaTaskKey(area, current.generation(), lowPriority ? predictionGeneration : 0L, lowPriority);
        if (!pendingLootAreaTasks.add(taskKey)) {
            return;
        }
        List<PredictedChestAlternatives> areaChests = List.copyOf(chests);
        Runnable predictionTask = () -> {
            try {
                if (lowPriority
                        ? !isCurrentOtherLootArea(area, current, predictionGeneration)
                        : !isCurrentArea(area, current)) {
                    return;
                }
                List<PredictedChestAlternatives> appleChests = new ArrayList<>();
                List<PredictedChestContents> contents = new ArrayList<>();
                for (PredictedChestAlternatives chest : areaChests) {
                    if (lowPriority
                            ? !isCurrentOtherLootArea(area, current, predictionGeneration)
                            : !isCurrentArea(area, current)) {
                        return;
                    }
                    if (cacheContents) {
                        Optional<List<ItemStack>> generated = chest.generateInventoryContents();
                        contents.add(new PredictedChestContents(
                                chest.possiblePositions().getFirst(), generated));
                        if (generated.stream()
                                .flatMap(List::stream)
                                .anyMatch(stack -> stack.isOf(Items.ENCHANTED_GOLDEN_APPLE))) {
                            appleChests.add(chest);
                        }
                    } else if (chest.containsEnchantedGoldenApple().orElse(false)) {
                        appleChests.add(chest);
                    }
                }
                if (!contents.isEmpty()
                        && (lowPriority
                                ? isCurrentOtherLootArea(area, current, predictionGeneration)
                                : isCurrentArea(area, current))) {
                    Map<AreaKey, List<PredictedChestContents>> lootContentsCache =
                            lowPriority ? cachedOtherLootContents : cachedAncientCityLootContents;
                    lootContentsCache.compute(area, (key, cached) -> {
                        if (lowPriority
                                ? !isCurrentOtherLootArea(area, current, predictionGeneration)
                                : !isCurrentArea(area, current)) {
                            return cached;
                        }
                        List<PredictedChestContents> combined = new ArrayList<>(cached == null ? List.of() : cached);
                        combined.addAll(contents);
                        return List.copyOf(combined);
                    });
                }
                if (!appleChests.isEmpty()
                        && (lowPriority
                                ? isCurrentOtherLootArea(area, current, predictionGeneration)
                                : isCurrentArea(area, current))) {
                    Map<AreaKey, List<PredictedChestAlternatives>> appleCache =
                            lowPriority ? cachedOtherPredictedAppleChests : cachedPredictedAppleChests;
                    appleCache.compute(area, (key, cached) -> {
                        if (lowPriority
                                ? !isCurrentOtherLootArea(area, current, predictionGeneration)
                                : !isCurrentArea(area, current)) {
                            return cached;
                        }
                        List<PredictedChestAlternatives> combined =
                                new ArrayList<>(cached == null ? List.of() : cached);
                        combined.addAll(appleChests);
                        return List.copyOf(combined);
                    });
                    if (lowPriority) {
                        waypointDirty = true;
                    }
                }
            } catch (Throwable throwable) {
                Debug.info("Unable to mark enchanted golden apple chests", throwable);
            } finally {
                pendingLootAreaTasks.remove(taskKey);
            }
        };
        Future<?> future = scheduleLootTask(area, predictionTask);
        pendingTasks.add(future);
    }

    private Future<?> scheduleLootTask(AreaKey area, Runnable task) {
        AreaKey center = lastPlayerArea;
        long distance = center == null ? Long.MAX_VALUE : areaDistanceSquared(area, center);
        PrioritizedLootTask future = new PrioritizedLootTask(task, distance, lootTaskSequence.getAndIncrement());
        lootExecutor.execute(future);
        return future;
    }

    private final class PrioritizedLootTask extends FutureTask<Void> implements Comparable<PrioritizedLootTask> {
        private final long distance;
        private final long sequence;

        private PrioritizedLootTask(Runnable task, long distance, long sequence) {
            super(task, null);
            this.distance = distance;
            this.sequence = sequence;
        }

        @Override
        public int compareTo(PrioritizedLootTask other) {
            int distanceOrder = Long.compare(distance, other.distance);
            return distanceOrder != 0 ? distanceOrder : Long.compare(sequence, other.sequence);
        }
    }

    private void completeAreaStructures(AreaKey area, GenerationContext current, List<StructurePosition> structures) {
        if (!isCurrentArea(area, current)) {
            pendingAreas.remove(area);
            return;
        }
        cachedStructures.put(area, List.copyOf(structures));
        pendingAreas.remove(area);
        if (structures.stream().anyMatch(structure -> {
            Identifier structureSet = structure.config().structureSetId();
            return END_CITIES.equals(structureSet) || ANCIENT_CITIES.equals(structureSet);
        })) {
            waypointDirty = true;
        }
    }

    private void completeAreaDetails(AreaKey area, GenerationContext current, List<EndCityResult> endCities) {
        if (!isCurrentArea(area, current)) {
            pendingAreaDetails.remove(area);
            return;
        }
        pendingAreaDetails.remove(area);
        cachedAreas.put(area, List.copyOf(endCities));
        for (EndCityResult result : endCities) {
            if (!isCurrentArea(area, current)) {
                return;
            }
            cachedCities.put(CityKey.of(result.structure()), result);
        }
        if (!endCities.isEmpty()) {
            waypointDirty = true;
        }
    }

    private static ChunkRange areaChunkRange(AreaKey area) {
        long minChunkX = Math.multiplyExact(area.x(), CACHE_CHUNK_SIZE);
        long minChunkZ = Math.multiplyExact(area.z(), CACHE_CHUNK_SIZE);
        return new ChunkRange(minChunkX, minChunkZ, minChunkX + CACHE_CHUNK_SIZE - 1, minChunkZ + CACHE_CHUNK_SIZE - 1);
    }

    private void cacheAncientCityViability(
            AreaKey area, List<StructurePosition> structures, GenerationContext current) {
        if (!isCurrentArea(area, current) || current.biomeSampler() == null) {
            return;
        }
        for (StructurePosition structure : structures) {
            if (!isCurrentArea(area, current)) {
                return;
            }
            if (!ANCIENT_CITIES.equals(structure.config().structureSetId())) {
                continue;
            }
            StructureKey key = StructureKey.of(structure);
            if (!isCurrentArea(area, current)) {
                return;
            }
            checkedAncientCities.add(key);
            if (current.biomeSampler().isAncientCityBiomeViable(current.seed(), structure)) {
                if (!isCurrentArea(area, current)) {
                    return;
                }
                viableAncientCities.add(key);
            }
        }
    }

    private void cacheDesertPyramidViability(
            AreaKey area, List<StructurePosition> structures, GenerationContext current) {
        if (!isCurrentArea(area, current) || current.biomeSampler() == null) {
            return;
        }
        boolean changed = false;
        for (StructurePosition structure : structures) {
            if (!isCurrentArea(area, current)) {
                return;
            }
            if (!DESERT_PYRAMIDS.equals(structure.config().structureSetId())) {
                continue;
            }
            StructureKey key = StructureKey.of(structure);
            if (!isCurrentArea(area, current)) {
                return;
            }
            if (!checkedDesertPyramids.add(key)) {
                continue;
            }
            if (current.biomeSampler().isDesertPyramidViable(structure)) {
                if (!isCurrentArea(area, current)) {
                    return;
                }
                viableDesertPyramids.add(key);
            }
            changed = true;
        }
        if (changed && isCurrentArea(area, current)) {
            waypointDirty = true;
        }
    }

    private void pruneCache(AreaKey center) {
        if (!Objects.equals(lastPlayerArea, center)) {
            return;
        }
        Set<AreaKey> areas = ConcurrentHashMap.newKeySet();
        areas.addAll(cachedAreas.keySet());
        areas.addAll(cachedStructures.keySet());
        for (AreaKey area : areas) {
            if (!Objects.equals(lastPlayerArea, center)) {
                return;
            }
            if (isWithinCurrentCache(area)) {
                continue;
            }
            List<EndCityResult> removed = cachedAreas.remove(area);
            for (EndCityResult result : removed == null ? List.<EndCityResult>of() : removed) {
                CityKey city = CityKey.of(result.structure());
                cachedCities.remove(city);
            }
            for (StructurePosition structure : cachedStructures.getOrDefault(area, List.of())) {
                StructureKey key = StructureKey.of(structure);
                checkedAncientCities.remove(key);
                viableAncientCities.remove(key);
                checkedDesertPyramids.remove(key);
                viableDesertPyramids.remove(key);
            }
            cachedStructures.remove(area);
            cachedPredictedAppleChests.remove(area);
            cachedOtherPredictedAppleChests.remove(area);
            cachedAncientCityChests.remove(area);
            cachedAncientCityLootContents.remove(area);
            cachedOtherLootContents.remove(area);
            checkedOtherLootAreas.remove(area);
            pendingAreaDetails.remove(area);
            pendingOtherLootAreas.remove(area);
            pendingAncientChestAreas.remove(area);
            waypointDirty = true;
        }
        checkedAncientCities.removeIf(
                key -> Objects.equals(lastPlayerArea, center) && !isWithinCurrentCache(areaForStructure(key)));
        viableAncientCities.removeIf(
                key -> Objects.equals(lastPlayerArea, center) && !isWithinCurrentCache(areaForStructure(key)));
        checkedDesertPyramids.removeIf(
                key -> Objects.equals(lastPlayerArea, center) && !isWithinCurrentCache(areaForStructure(key)));
        viableDesertPyramids.removeIf(
                key -> Objects.equals(lastPlayerArea, center) && !isWithinCurrentCache(areaForStructure(key)));
    }

    private static AreaKey areaForStructure(StructureKey key) {
        return new AreaKey(
                Math.floorDiv(key.chunkX(), CACHE_CHUNK_SIZE), Math.floorDiv(key.chunkZ(), CACHE_CHUNK_SIZE));
    }

    private void rebuildWaypoints() {
        if (mc.world == null) {
            return;
        }
        XaeroHelper helper = XaeroHelper.INSTANCE;
        if (helper == null) {
            waypointDirty = true;
            return;
        }
        Map<StructureKey, WaypointSpec> desired = new java.util.LinkedHashMap<>();
        for (List<StructurePosition> structures : cachedStructures.values()) {
            for (StructurePosition structure : structures) {
                if (!isStructureBiomeViable(structure)) {
                    continue;
                }
                EndCityResult city = cachedCities.get(CityKey.of(structure));
                boolean elytra = city != null && !city.elytraPositions().isEmpty();
                if (isWaypointDesired(structure, elytra)) {
                    desired.put(StructureKey.of(structure), createWaypointSpec(structure, elytra));
                }
            }
        }
        waypointDirty = false;
        for (Map.Entry<StructureKey, IXWaypoint> entry : List.copyOf(waypoints.entrySet())) {
            if (!desired.containsKey(entry.getKey())) {
                helper.removeWaypoint(entry.getValue());
                waypoints.remove(entry.getKey());
                committedWaypointSpecs.remove(entry.getKey());
            }
        }
        for (Map.Entry<StructureKey, WaypointSpec> entry : desired.entrySet()) {
            ChunkPos chunkPos = new ChunkPos(
                    Math.toIntExact(entry.getKey().chunkX()),
                    Math.toIntExact(entry.getKey().chunkZ()));
            ensureWaypoint(helper, entry.getKey(), entry.getValue(), dirtyPos.contains(chunkPos));
        }
        Map<AppleChestWaypointKey, WaypointSpec> desiredAppleChests = new java.util.LinkedHashMap<>();
        for (List<PredictedChestAlternatives> predictions : cachedOtherPredictedAppleChests.values()) {
            for (PredictedChestAlternatives prediction : predictions) {
                BlockPos position = prediction.possiblePositions().getFirst();
                AppleChestWaypointKey key =
                        new AppleChestWaypointKey(prediction.lootTable(), position.getX(), position.getZ());
                desiredAppleChests.put(key, createAppleChestWaypointSpec(prediction));
            }
        }
        for (Map.Entry<AppleChestWaypointKey, IXWaypoint> entry : List.copyOf(appleChestWaypoints.entrySet())) {
            if (!desiredAppleChests.containsKey(entry.getKey())) {
                helper.removeWaypoint(entry.getValue());
                appleChestWaypoints.remove(entry.getKey());
                committedAppleChestWaypointSpecs.remove(entry.getKey());
            }
        }
        for (Map.Entry<AppleChestWaypointKey, WaypointSpec> entry : desiredAppleChests.entrySet()) {
            ChunkPos chunkPos =
                    new ChunkPos(entry.getKey().x() >> 4, entry.getKey().z() >> 4);
            ensureAppleChestWaypoint(helper, entry.getKey(), entry.getValue(), dirtyPos.contains(chunkPos));
        }
        dirtyPos.clear();
    }

    private WaypointSpec createAppleChestWaypointSpec(PredictedChestAlternatives prediction) {
        BlockPos position = prediction.possiblePositions().getFirst();
        boolean desertPyramid = DESERT_PYRAMID_LOOT.equals(prediction.lootTable());
        double scale = mc.world.getDimension().coordinateScale();
        int x = (int) Math.round(position.getX() * scale);
        int z = (int) Math.round(position.getZ() * scale);
        return new WaypointSpec(
                x,
                position.getY(),
                z,
                desertPyramid ? "Desert Pyramid" : "Ruined Portal",
                desertPyramid ? "DP" : "RP",
                isVisitedChunk(new ChunkPos(position))
                        ? Formatting.GRAY.ordinal()
                        : desertPyramid ? desertPyramidColorId.get() : ruinedPortalColorId.get(),
                0,
                true,
                true);
    }

    private void ensureAppleChestWaypoint(
            XaeroHelper helper, AppleChestWaypointKey key, WaypointSpec spec, boolean forceReadd) {
        IXWaypoint waypoint = appleChestWaypoints.get(key);
        WaypointSpec previous = committedAppleChestWaypointSpecs.get(key);
        if (!forceReadd && spec.equals(previous)) {
            return;
        }
        if (forceReadd && waypoint != null) {
            helper.removeWaypoint(waypoint);
            appleChestWaypoints.remove(key);
            committedAppleChestWaypointSpecs.remove(key);
            waypoint = null;
        }
        if (waypoint != null && previous != null && !previous.initials().equals(spec.initials())) {
            helper.removeWaypoint(waypoint);
            appleChestWaypoints.remove(key);
            committedAppleChestWaypointSpecs.remove(key);
            waypoint = null;
        }
        if (waypoint != null) {
            helper.updateWaypoint(waypoint, current -> {
                current.setX(spec.x());
                current.setY(spec.y());
                current.setZ(spec.z());
                current.setColor(spec.color());
                current.setName(spec.name());
                current.setPurpose(spec.purpose());
            });
            committedAppleChestWaypointSpecs.put(key, spec);
            return;
        }
        waypoint = helper.createWaypoint(
                spec.x(),
                spec.y(),
                spec.z(),
                spec.name(),
                spec.initials(),
                spec.color(),
                spec.purpose(),
                spec.temp(),
                spec.yIncluded());
        if (waypoint != null) {
            appleChestWaypoints.put(key, waypoint);
            committedAppleChestWaypointSpecs.put(key, spec);
            helper.submitWaypoint(this, waypoint);
        }
    }

    private boolean isWaypointDesired(StructurePosition structure, boolean elytra) {
        if (END_CITIES.equals(structure.config().structureSetId())) {
            return elytra ? labelElytraEndCities.get() : labelNonElytraEndCities.get();
        }
        if (ANCIENT_CITIES.equals(structure.config().structureSetId())) {
            return labelAncientCities.get();
        }
        return false;
    }

    private WaypointSpec createWaypointSpec(StructurePosition structure, boolean elytra) {
        Identifier structureSet = structure.config().structureSetId();
        int color = isVisitedStructure(structure)
                ? Formatting.GRAY.ordinal()
                : END_CITIES.equals(structureSet)
                        ? elytra ? elytraColorId.get() : endCityColorId.get()
                        : ANCIENT_CITIES.equals(structureSet)
                                ? ancientCityColorId.get()
                                : DESERT_PYRAMIDS.equals(structureSet)
                                        ? desertPyramidColorId.get()
                                        : ruinedPortalColorId.get();
        boolean ancientCity = ANCIENT_CITIES.equals(structureSet);
        String name = ancientCity
                ? "Ancient City"
                : DESERT_PYRAMIDS.equals(structureSet)
                        ? "Desert Pyramid"
                        : RUINED_PORTALS.equals(structureSet)
                                ? "Ruined Portal"
                                : elytra ? "Elytra End City" : "End City";
        String initials = ancientCity
                ? "AC"
                : DESERT_PYRAMIDS.equals(structureSet)
                        ? "DP"
                        : RUINED_PORTALS.equals(structureSet) ? "RP" : elytra ? "E" : "C";
        double scale = mc.world.getDimension().coordinateScale();
        int x = (int) Math.round((structure.blockX() + 8) * scale);
        int z = (int) Math.round((structure.blockZ() + 8) * scale);
        return new WaypointSpec(x, 64, z, name, initials, color, 0, true, true);
    }

    private void markNearbyStructuresVisited(BlockPos playerPos) {
        if (mc.world == null) {
            return;
        }
        boolean changed = false;
        for (List<StructurePosition> structures : cachedStructures.values()) {
            for (StructurePosition structure : structures) {
                if (!isStructureBiomeViable(structure)) {
                    continue;
                }
                EndCityResult city = cachedCities.get(CityKey.of(structure));
                boolean elytra = city != null && !city.elytraPositions().isEmpty();
                if (!isWaypointDesired(structure, elytra)) {
                    continue;
                }
                long dx = structure.blockX() + 8L - playerPos.getX();
                long dz = structure.blockZ() + 8L - playerPos.getZ();
                if (dx * dx + dz * dz >= 128L * 128L) {
                    continue;
                }
                ChunkPos chunkPos =
                        new ChunkPos(Math.toIntExact(structure.chunkX()), Math.toIntExact(structure.chunkZ()));
                if (markChunkVisited(chunkPos)) {
                    changed = true;
                }
            }
        }
        for (List<PredictedChestAlternatives> predictions : cachedOtherPredictedAppleChests.values()) {
            for (PredictedChestAlternatives prediction : predictions) {
                BlockPos position = prediction.possiblePositions().getFirst();
                long dx = (long) position.getX() - playerPos.getX();
                long dz = (long) position.getZ() - playerPos.getZ();
                if (dx * dx + dz * dz >= 128L * 128L) {
                    continue;
                }
                if (markChunkVisited(new ChunkPos(position))) {
                    changed = true;
                }
            }
        }
        if (changed) {
            waypointDirty = true;
        }
    }

    private boolean markChunkVisited(ChunkPos chunkPos) {
        ChunkStorage storage = ServerStorage.getOrCreateChunkStorage(chunkPos);
        if (storage == null || Boolean.TRUE.equals(storage.get(VISITED_STRUCTURE_KEY, Codec.BOOL))) {
            return false;
        }
        storage.put(VISITED_STRUCTURE_KEY, true, Codec.BOOL);
        dirtyPos.add(chunkPos);
        return true;
    }

    private boolean isVisitedStructure(StructurePosition structure) {
        ChunkPos chunkPos = new ChunkPos(Math.toIntExact(structure.chunkX()), Math.toIntExact(structure.chunkZ()));
        return isVisitedChunk(chunkPos);
    }

    private boolean isVisitedChunk(ChunkPos chunkPos) {
        ChunkStorage storage = ServerStorage.getChunkStorage(chunkPos);
        return storage != null && Boolean.TRUE.equals(storage.get(VISITED_STRUCTURE_KEY, Codec.BOOL));
    }

    private void ensureWaypoint(XaeroHelper helper, StructureKey key, WaypointSpec spec, boolean forceReadd) {
        IXWaypoint waypoint = waypoints.get(key);
        WaypointSpec previous = committedWaypointSpecs.get(key);
        if (!forceReadd && spec.equals(previous)) {
            return;
        }
        if (forceReadd && waypoint != null) {
            helper.removeWaypoint(waypoint);
            waypoints.remove(key);
            committedWaypointSpecs.remove(key);
            waypoint = null;
        }
        if (waypoint != null && previous != null && !previous.initials().equals(spec.initials())) {
            helper.removeWaypoint(waypoint);
            waypoints.remove(key);
            committedWaypointSpecs.remove(key);
            waypoint = null;
        }
        if (waypoint != null) {
            helper.updateWaypoint(waypoint, current -> {
                current.setX(spec.x());
                current.setY(spec.y());
                current.setZ(spec.z());
                current.setColor(spec.color());
                current.setName(spec.name());
                current.setPurpose(spec.purpose());
            });
            committedWaypointSpecs.put(key, spec);
            return;
        }
        waypoint = helper.createWaypoint(
                spec.x(),
                spec.y(),
                spec.z(),
                spec.name(),
                spec.initials(),
                spec.color(),
                spec.purpose(),
                spec.temp(),
                spec.yIncluded());
        if (waypoint != null) {
            waypoints.put(key, waypoint);
            committedWaypointSpecs.put(key, spec);
            helper.submitWaypoint(this, waypoint);
        }
    }

    private void submitNearbyKnownAppleChests() {
        boolean renderAppleChests = labelEnchantedGoldenAppleChests.get();
        boolean otherLootPredictionEnabled = labelOtherEnchantedGoldStructures.get();
        if (!renderAppleChests || mc.player == null) {
            return;
        }
        double otherRangeSquared = (double) renderRange.get() * renderRange.get();
        double ancientRangeSquared = (double) ANCIENT_LOOT_SEARCH_MARGIN * ANCIENT_LOOT_SEARCH_MARGIN;
        int color = renderColor.get().withAlpha(255);
        int openedColor = openedAppleChestColor.get().withAlpha(255);
        BlockPos playerPos = mc.player.getBlockPos();
        AreaKey playerArea = AreaKey.fromBlock(playerPos);
        Set<BlockPos> rendered = new HashSet<>();
        renderPredictedAppleChests(
                cachedPredictedAppleChests,
                playerPos,
                playerArea,
                ancientRangeSquared,
                true,
                color,
                openedColor,
                rendered);
        if (otherLootPredictionEnabled) {
            renderPredictedAppleChests(
                    cachedOtherPredictedAppleChests,
                    playerPos,
                    playerArea,
                    otherRangeSquared,
                    false,
                    color,
                    openedColor,
                    rendered);
        }
    }

    private void renderPredictedAppleChests(
            Map<AreaKey, List<PredictedChestAlternatives>> predictionsByArea,
            BlockPos playerPos,
            AreaKey playerArea,
            double rangeSquared,
            boolean horizontalDistance,
            int color,
            int openedColor,
            Set<BlockPos> rendered) {
        for (Map.Entry<AreaKey, List<PredictedChestAlternatives>> entry : predictionsByArea.entrySet()) {
            boolean isCurrentArea = entry.getKey().equals(playerArea);
            List<PredictedChestAlternatives> predictions = entry.getValue();
            for (PredictedChestAlternatives prediction : predictions) {
                List<BlockPos> positions = prediction.possiblePositions();
                BlockPos first = positions.getFirst();
                List<BlockPos> nearbyPositions = positions.stream()
                        .filter(pos -> isWithinRenderRange(pos, playerPos, rangeSquared, horizontalDistance))
                        .toList();
                if (nearbyPositions.isEmpty()) {
                    continue;
                }
                if (positions.stream().anyMatch(rendered::contains)) {
                    continue;
                }
                int chestColor = nearbyPositions.stream().anyMatch(this::hasChestHistory) ? openedColor : color;
                BlockPos synchronizedAppleChest = isCurrentArea
                        ? null
                        : positions.stream()
                                .filter(pos -> isWithinRenderRange(pos, playerPos, rangeSquared, horizontalDistance))
                                .filter(this::containsSynchronizedApple)
                                .findFirst()
                                .orElse(null);
                if (synchronizedAppleChest != null) {
                    rendered.addAll(positions);
                    int actualColor = hasChestHistory(synchronizedAppleChest) ? openedColor : color;
                    boxCollector.submit(chestBox(synchronizedAppleChest), actualColor);
                    tracerCollector.submit(synchronizedAppleChest.toCenterPos(), actualColor);
                    continue;
                }
                if (positions.size() == 1) {
                    if (rendered.add(first)) {
                        boxCollector.submit(new Box(first), chestColor);
                        tracerCollector.submit(first.toCenterPos(), chestColor);
                    }
                    continue;
                }
                int minY = positions.stream().mapToInt(BlockPos::getY).min().orElseThrow();
                int maxY = positions.stream().mapToInt(BlockPos::getY).max().orElseThrow();
                if (rendered.add(first)) {
                    rendered.addAll(positions);
                    boxCollector.submit(
                            new Box(
                                    first.getX() + 0.375,
                                    minY,
                                    first.getZ() + 0.375,
                                    first.getX() + 0.625,
                                    maxY + 1,
                                    first.getZ() + 0.625),
                            chestColor);
                    tracerCollector.submit(
                            new Vec3d(first.getX() + 0.5, (minY + maxY + 1) * 0.5, first.getZ() + 0.5), chestColor);
                }
            }
        }
    }

    private static boolean isWithinRenderRange(
            BlockPos position, BlockPos playerPos, double rangeSquared, boolean horizontalDistance) {
        if (!horizontalDistance) {
            return position.getSquaredDistance(playerPos) <= rangeSquared;
        }
        long dx = (long) position.getX() - playerPos.getX();
        long dz = (long) position.getZ() - playerPos.getZ();
        return dx * dx + dz * dz <= rangeSquared;
    }

    private static PredictedChestAlternatives asAlternatives(PredictedChest chest) {
        return new PredictedChestAlternatives(List.of(chest.position()), chest.lootTable(), chest.lootSeed());
    }

    private boolean containsEnchantedGoldenApple(BlockEntity blockEntity) {
        // Clients do not receive unopened chest loot contents. Only mark an apple once its
        // item stack has actually been synchronized by the server.
        if (!(blockEntity instanceof Inventory inventory)) {
            return false;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSynchronizedApple(BlockPos pos) {
        if (mc.world == null || !mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }
        BlockEntity blockEntity = mc.world.getBlockEntity(pos);
        return blockEntity != null
                && blockEntity.getType() == BlockEntityType.CHEST
                && containsEnchantedGoldenApple(blockEntity);
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

    private boolean isStructureBiomeViable(StructurePosition structure) {
        Identifier setId = structure.config().structureSetId();
        StructureKey key = StructureKey.of(structure);
        if (END_CITIES.equals(setId)) {
            EndCityResult city = cachedCities.get(CityKey.of(structure));
            return city != null && city.outsideCentralIsland() && city.piecesGenerated();
        }
        if (ANCIENT_CITIES.equals(setId)) {
            return viableAncientCities.contains(key);
        }
        if (DESERT_PYRAMIDS.equals(setId)) {
            return viableDesertPyramids.contains(key);
        }
        return true;
    }

    private Box chestBox(BlockPos pos) {
        if (mc.world == null) {
            return new Box(pos);
        }
        VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
        return shape.isEmpty() ? new Box(pos) : shape.getBoundingBox().offset(pos);
    }

    private void onRender3D(Event<Render3D> event) {
        if (checkNull() || !enable.get()) {
            return;
        }
        me.matl114.utils.RenderUtils.startDrawVirtual(event.context().stack());
        try {
            boxCollector.render3D(event.context().stack(), event.context.partialTicks());
            tracerCollector.render3D(event.context().stack(), event.context.partialTicks());
        } finally {
            me.matl114.utils.RenderUtils.stopDrawVirtual(event.context().stack());
        }
    }

    private boolean isCurrent(GenerationContext current) {
        return !checkNull() && enable.get() && context == current && generation == current.generation();
    }

    private boolean isCurrent(ContextRequest request) {
        return !checkNull() && enable.get() && pendingContextRequest == request && generation == request.generation();
    }

    private void cleanupFinishedTasks() {
        pendingTasks.removeIf(Future::isDone);
    }

    private LootBiomeSamplers getLootBiomeSamplers(GenerationContext current) {
        Thread thread = Thread.currentThread();
        LootBiomeSamplers cached = lootBiomeSamplerCache.get(thread);
        if (cached != null && cached.matches(current)) {
            return cached;
        }
        CubiomesOverworldBiomeSampler overworldSampler = World.OVERWORLD.equals(current.worldKey())
                ? CubiomesOverworldBiomeSampler.create(current.seed())
                : null;
        CubiomesNetherBiomeSampler netherSampler =
                World.NETHER.equals(current.worldKey()) ? CubiomesNetherBiomeSampler.create(current.seed()) : null;
        LootBiomeSamplers created =
                new LootBiomeSamplers(current.seed(), current.worldKey(), overworldSampler, netherSampler);
        if (isCurrent(current)) {
            lootBiomeSamplerCache.put(thread, created);
        }
        return created;
    }

    private void resetState() {
        resetState(false, false);
    }

    private void resetState(boolean worldSwitch, boolean removeWaypoints) {
        generation++;
        otherLootGeneration++;
        context = null;
        pendingContextRequest = null;
        lastPlayerArea = null;
        lastRequestedRadius = -1L;
        cachedAreas.clear();
        cachedStructures.clear();
        cachedPredictedAppleChests.clear();
        cachedOtherPredictedAppleChests.clear();
        cachedAncientCityChests.clear();
        cachedAncientCityLootContents.clear();
        cachedOtherLootContents.clear();
        lootBiomeSamplerCache.clear();
        checkedAncientCities.clear();
        viableAncientCities.clear();
        checkedDesertPyramids.clear();
        viableDesertPyramids.clear();
        cachedCities.clear();
        pendingAreas.clear();
        pendingAreaDetails.clear();
        pendingOtherLootAreas.clear();
        checkedOtherLootAreas.clear();
        pendingAncientChestAreas.clear();
        pendingLootAreaTasks.clear();
        boxCollector.clear();
        tracerCollector.clear();
        for (Future<?> future : pendingTasks) {
            future.cancel(true);
        }
        lootExecutor.purge();
        pendingTasks.clear();
        XaeroHelper helper = XaeroHelper.INSTANCE;
        if (worldSwitch) {
            if (helper != null) {
                helper.removeSub(this);
            }
            waypoints.clear();
            committedWaypointSpecs.clear();
            appleChestWaypoints.clear();
            committedAppleChestWaypointSpecs.clear();
            dirtyPos.clear();
        } else if (removeWaypoints) {
            if (helper != null) {
                List<IXWaypoint> waypointsToRemove = new ArrayList<>(waypoints.values());
                waypointsToRemove.addAll(appleChestWaypoints.values());
                for (IXWaypoint waypoint : waypointsToRemove) {
                    helper.removeWaypoint(waypoint);
                }
            }
            waypoints.clear();
            committedWaypointSpecs.clear();
            appleChestWaypoints.clear();
            committedAppleChestWaypointSpecs.clear();
            dirtyPos.clear();
        }
        waypointDirty = true;
    }

    private record AreaKey(long x, long z) {
        private static AreaKey fromBlock(BlockPos pos) {
            return new AreaKey(
                    Math.floorDiv((long) pos.getX(), CACHE_BLOCK_SIZE),
                    Math.floorDiv((long) pos.getZ(), CACHE_BLOCK_SIZE));
        }
    }

    private record LootAreaTaskKey(
            AreaKey area, long structureGeneration, long predictionGeneration, boolean lowPriority) {}

    private record PredictedChestContents(BlockPos position, Optional<List<ItemStack>> contents) {
        private PredictedChestContents {
            position = position.toImmutable();
            contents =
                    contents.map(stacks -> stacks.stream().map(ItemStack::copy).toList());
        }
    }

    private record CityKey(long chunkX, long chunkZ) {
        private static CityKey of(StructurePosition position) {
            return new CityKey(position.chunkX(), position.chunkZ());
        }
    }

    private boolean isSupportedDimension() {
        return World.END.equals(mc.world.getRegistryKey())
                || World.OVERWORLD.equals(mc.world.getRegistryKey())
                || World.NETHER.equals(mc.world.getRegistryKey());
    }

    private boolean isSupportedStructure(Identifier id, RegistryKey<World> worldKey) {
        if (World.END.equals(worldKey)) {
            return END_CITIES.equals(id);
        }
        if (World.NETHER.equals(worldKey)) {
            return Identifier.ofVanilla("ruined_portals").equals(id);
        }
        return !END_CITIES.equals(id);
    }

    private record ContextRequest(long generation, long seed, RegistryKey<World> worldKey) {}

    private record GenerationContext(
            long generation,
            long seed,
            RegistryKey<World> worldKey,
            Map<Identifier, StructureConfig> configs,
            Populator populator,
            @Nullable CubiomesOverworldBiomeSampler biomeSampler) {}

    private record LootBiomeSamplers(
            long seed,
            RegistryKey<World> worldKey,
            @Nullable CubiomesOverworldBiomeSampler overworld,
            @Nullable CubiomesNetherBiomeSampler nether) {
        private boolean matches(GenerationContext current) {
            return seed == current.seed() && worldKey.equals(current.worldKey());
        }
    }

    private record StructureKey(Identifier structureSet, long chunkX, long chunkZ) {
        private static StructureKey of(StructurePosition position) {
            return new StructureKey(position.config().structureSetId(), position.chunkX(), position.chunkZ());
        }
    }

    private record AppleChestWaypointKey(Identifier lootTable, int x, int z) {}

    private record WaypointSpec(
            int x,
            int y,
            int z,
            String name,
            String initials,
            int color,
            int purpose,
            boolean temp,
            boolean yIncluded) {}
}
