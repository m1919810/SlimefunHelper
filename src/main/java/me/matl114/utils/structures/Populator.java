package me.matl114.utils.structures;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public final class Populator {
    private static final int ANCIENT_CITY_START_SEARCH_HALO_CHUNKS = 9;
    private static final int END_CITY_START_SEARCH_HALO_CHUNKS = 8;
    private static final Identifier END_CITY = Identifier.ofVanilla("end_city");
    private static final Identifier END_CITY_LOOT = Identifier.ofVanilla("chests/end_city_treasure");
    private static final Identifier DESERT_PYRAMID = Identifier.ofVanilla("desert_pyramid");
    private static final Identifier DESERT_PYRAMID_LOOT = Identifier.ofVanilla("chests/desert_pyramid");
    private static final Identifier RUINED_PORTAL_LOOT = Identifier.ofVanilla("chests/ruined_portal");
    private static final Identifier RUINED_PORTAL_NETHER = Identifier.ofVanilla("ruined_portal_nether");
    private static final List<Identifier> RUINED_PORTAL_STRUCTURE_ORDER = List.of(
            Identifier.ofVanilla("ruined_portal"),
            Identifier.ofVanilla("ruined_portal_desert"),
            Identifier.ofVanilla("ruined_portal_jungle"),
            Identifier.ofVanilla("ruined_portal_swamp"),
            Identifier.ofVanilla("ruined_portal_mountain"),
            Identifier.ofVanilla("ruined_portal_ocean"),
            RUINED_PORTAL_NETHER);

    private final long seed;
    private final Map<Identifier, StructureDecorationIndex.Index> structureIndices;

    public Populator(long seed) {
        this(seed, Map.of());
    }

    public Populator(long seed, Map<Identifier, StructureDecorationIndex.Index> structureIndices) {
        this.seed = seed;
        this.structureIndices = Map.copyOf(structureIndices);
    }

    public long seed() {
        return seed;
    }

    public Optional<MinecraftWorldgenRandom> decorationRandom(
            long decorationChunkX, long decorationChunkZ, Identifier structureId) {
        Objects.requireNonNull(structureId);
        StructureDecorationIndex.Index index = structureIndices.get(structureId);
        if (index == null) {
            return Optional.empty();
        }
        return Optional.of(MinecraftWorldgenRandom.forDecorationChunk(
                seed, Math.toIntExact(decorationChunkX), Math.toIntExact(decorationChunkZ), index));
    }

    public Optional<AncientCityVariant> generateAncientCity(StructurePosition structure) {
        Objects.requireNonNull(structure);
        if (!structure.config().containsStructure(Identifier.ofVanilla("ancient_city"))) {
            return Optional.empty();
        }
        return Optional.of(AncientCityVariant.generate(seed, structure));
    }

    /** Expands an Ancient City through the vanilla jigsaw generator and reports template chest markers. */
    public Optional<AncientCityStructureGenerator.Result> generateAncientCityPieces(
            StructurePosition structure, CubiomesOverworldBiomeSampler sampler) {
        Objects.requireNonNull(structure);
        Objects.requireNonNull(sampler);
        return AncientCityStructureGenerator.generate(seed, structure, sampler);
    }

    /** Generates Ancient City starts in a halo wide enough to cover the vanilla 116-block bound. */
    public List<AncientCityStructureGenerator.Result> generateAncientCityPiecesNear(
            StructureConfig config, ChunkRange chestChunkRange, CubiomesOverworldBiomeSampler sampler) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(chestChunkRange);
        Objects.requireNonNull(sampler);
        if (!config.containsStructure(Identifier.ofVanilla("ancient_city"))) {
            return List.of();
        }
        ChunkRange candidateRange = new ChunkRange(
                chestChunkRange.minChunkX() - ANCIENT_CITY_START_SEARCH_HALO_CHUNKS,
                chestChunkRange.minChunkZ() - ANCIENT_CITY_START_SEARCH_HALO_CHUNKS,
                chestChunkRange.maxChunkX() + ANCIENT_CITY_START_SEARCH_HALO_CHUNKS,
                chestChunkRange.maxChunkZ() + ANCIENT_CITY_START_SEARCH_HALO_CHUNKS);
        return generate(config, candidateRange).stream()
                .map(candidate -> generateAncientCityPieces(candidate, sampler))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Generates complete nearby Ancient City starts and predicts their chest seeds.
     * Vanilla's LongOpenHashSet reference order is reproduced for chunks with overlapping starts.
     */
    public List<PredictedChest> generateAncientCityChests(
            StructureConfig config, ChunkRange targetChunkRange, CubiomesOverworldBiomeSampler terrain) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(targetChunkRange);
        Objects.requireNonNull(terrain);
        return generateAncientCityChests(
                generateAncientCityPiecesNear(config, targetChunkRange, terrain), targetChunkRange, terrain);
    }

    /**
     * Predicts Ancient City chest seeds for the supplied starts. The caller must include every
     * city referenced by each target chunk; vanilla's LongOpenHashSet reference order is reproduced.
     */
    public List<PredictedChest> generateAncientCityChests(
            List<AncientCityStructureGenerator.Result> nearbyStarts,
            ChunkRange targetChunkRange,
            CubiomesOverworldBiomeSampler terrain) {
        Objects.requireNonNull(nearbyStarts);
        Objects.requireNonNull(terrain);
        Map<BlockPos, Boolean> placeableChestCache = new HashMap<>();
        LinkedHashSet<ChunkPos> targetChunks = new LinkedHashSet<>();
        List<StructureReferenceOrder.Start<AncientCityStructureGenerator.Result>> references =
                new ArrayList<>(nearbyStarts.size());
        for (AncientCityStructureGenerator.Result start : nearbyStarts) {
            int startChunkX = Math.toIntExact(start.candidate().chunkX());
            int startChunkZ = Math.toIntExact(start.candidate().chunkZ());
            references.add(new StructureReferenceOrder.Start(startChunkX, startChunkZ, start.referenceBounds(), start));
            for (AncientCityStructureGenerator.Piece piece : start.pieces()) {
                for (AncientCityChestTemplateData.WorldChest chest : piece.chests()) {
                    int chunkX = Math.floorDiv(chest.position().getX(), 16);
                    int chunkZ = Math.floorDiv(chest.position().getZ(), 16);
                    if (targetChunkRange != null && !targetChunkRange.contains(chunkX, chunkZ)) {
                        continue;
                    }
                    if (!canPlaceAncientCityChest(chest, terrain, placeableChestCache)) {
                        continue;
                    }
                    targetChunks.add(new ChunkPos(chunkX, chunkZ));
                }
            }
        }

        List<PredictedChest> predicted = new ArrayList<>();
        for (ChunkPos chunk : targetChunks) {
            Optional<MinecraftWorldgenRandom> random =
                    decorationRandom(chunk.x, chunk.z, Identifier.ofVanilla("ancient_city"));
            if (random.isEmpty()) {
                continue;
            }
            for (StructureReferenceOrder.Start<AncientCityStructureGenerator.Result> reference :
                    StructureReferenceOrder.forChunk(chunk, references)) {
                AncientCityStructureGenerator.Result start = reference.value();
                for (AncientCityStructureGenerator.Piece piece : start.pieces()) {
                    for (AncientCityChestTemplateData.WorldChest chest : piece.chests()) {
                        if (Math.floorDiv(chest.position().getX(), 16) != chunk.x
                                || Math.floorDiv(chest.position().getZ(), 16) != chunk.z) {
                            continue;
                        }
                        if (!canPlaceAncientCityChest(chest, terrain, placeableChestCache)) {
                            continue;
                        }
                        long lootSeed = random.get().nextLootSeed();
                        chest.lootTable()
                                .ifPresent(lootTable ->
                                        predicted.add(new PredictedChest(chest.position(), lootTable, lootSeed)));
                    }
                }
            }
        }
        return List.copyOf(predicted);
    }

    private static boolean canPlaceAncientCityChest(
            AncientCityChestTemplateData.WorldChest chest,
            CubiomesOverworldBiomeSampler terrain,
            Map<BlockPos, Boolean> placeableChestCache) {
        if (!chest.protectedBlocksProcessor()) {
            return true;
        }
        return placeableChestCache.computeIfAbsent(chest.position(), terrain::canPlaceGeneratedStructureBlock);
    }

    public Optional<DesertPyramidVariant> generateDesertPyramid(StructurePosition structure) {
        Objects.requireNonNull(structure);
        if (!structure.config().containsStructure(Identifier.ofVanilla("desert_pyramid"))) {
            return Optional.empty();
        }
        return Optional.of(DesertPyramidVariant.generate(seed, structure));
    }

    /**
     * Models the pyramid's post-process stream; the caller supplies the first chunk that processes
     * the piece because vanilla establishes its height on that first invocation.
     */
    public Optional<List<PredictedChest>> generateDesertPyramidChests(
            StructurePosition structure,
            CubiomesOverworldBiomeSampler terrain,
            int firstPostProcessChunkX,
            int firstPostProcessChunkZ) {
        Objects.requireNonNull(terrain);
        Optional<DesertPyramidVariant> variant = generateDesertPyramid(structure);
        if (variant.isEmpty()) {
            return Optional.empty();
        }
        Optional<MinecraftWorldgenRandom> firstPostProcessRandom =
                decorationRandom(firstPostProcessChunkX, firstPostProcessChunkZ, DESERT_PYRAMID);
        if (firstPostProcessRandom.isEmpty()) {
            return Optional.empty();
        }

        int heightOffset = firstPostProcessRandom.get().nextInt(3);
        List<BlockPos> positions = variant.get().chestPositions(terrain, heightOffset);
        Map<ChunkPos, MinecraftWorldgenRandom> chestChunkRandoms = new HashMap<>();
        List<PredictedChest> chests = new ArrayList<>(positions.size());
        for (BlockPos position : positions) {
            int chunkX = Math.floorDiv(position.getX(), 16);
            int chunkZ = Math.floorDiv(position.getZ(), 16);
            ChunkPos chunkKey = new ChunkPos(chunkX, chunkZ);
            MinecraftWorldgenRandom chestRandom = chestChunkRandoms.get(chunkKey);
            if (chestRandom == null) {
                Optional<MinecraftWorldgenRandom> chunkRandom = decorationRandom(chunkX, chunkZ, DESERT_PYRAMID);
                if (chunkRandom.isEmpty()) {
                    return Optional.empty();
                }
                chestRandom = chunkRandom.get();
                // DesertTempleGenerator.generate draws this on every intersecting chunk, even
                // after ShiftableStructurePiece has fixed the piece height on an earlier chunk.
                chestRandom.nextInt(3);
                chestChunkRandoms.put(chunkKey, chestRandom);
            }
            chests.add(new PredictedChest(position, DESERT_PYRAMID_LOOT, chestRandom.nextLootSeed()));
        }
        return Optional.of(chests);
    }

    /**
     * Predicts exact Desert Pyramid loot seeds and all possible chest Y positions. Vanilla fixes
     * the piece height on whichever of its intersecting post-process chunks runs first.
     */
    public Optional<List<PredictedChestAlternatives>> generateDesertPyramidChestAlternatives(
            StructurePosition structure, CubiomesOverworldBiomeSampler terrain) {
        Objects.requireNonNull(terrain);
        Optional<DesertPyramidVariant> variant = generateDesertPyramid(structure);
        if (variant.isEmpty()) {
            return Optional.empty();
        }

        DesertPyramidVariant pyramid = variant.get();
        int minChunkX = Math.floorDiv(pyramid.minBlockX(), 16);
        int maxChunkX = Math.floorDiv(pyramid.minBlockX() + pyramid.sizeX() - 1, 16);
        int minChunkZ = Math.floorDiv(pyramid.minBlockZ(), 16);
        int maxChunkZ = Math.floorDiv(pyramid.minBlockZ() + pyramid.sizeZ() - 1, 16);
        LinkedHashSet<Integer> heightOffsets = new LinkedHashSet<>();
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                Optional<MinecraftWorldgenRandom> random = decorationRandom(chunkX, chunkZ, DESERT_PYRAMID);
                if (random.isEmpty()) {
                    return Optional.empty();
                }
                heightOffsets.add(random.get().nextInt(3));
            }
        }

        List<List<BlockPos>> positionsByOffset = heightOffsets.stream()
                .map(offset -> pyramid.chestPositions(terrain, offset))
                .toList();
        Map<ChunkPos, MinecraftWorldgenRandom> chestChunkRandoms = new HashMap<>();
        List<PredictedChestAlternatives> predictions =
                new ArrayList<>(positionsByOffset.getFirst().size());
        for (int chestIndex = 0; chestIndex < positionsByOffset.getFirst().size(); chestIndex++) {
            List<BlockPos> possiblePositions = new ArrayList<>(positionsByOffset.size());
            for (List<BlockPos> positions : positionsByOffset) {
                possiblePositions.add(positions.get(chestIndex));
            }
            BlockPos position = possiblePositions.getFirst();
            ChunkPos chunkKey = new ChunkPos(Math.floorDiv(position.getX(), 16), Math.floorDiv(position.getZ(), 16));
            MinecraftWorldgenRandom chestRandom = chestChunkRandoms.get(chunkKey);
            if (chestRandom == null) {
                Optional<MinecraftWorldgenRandom> random = decorationRandom(chunkKey.x, chunkKey.z, DESERT_PYRAMID);
                if (random.isEmpty()) {
                    return Optional.empty();
                }
                chestRandom = random.get();
                // DesertTempleGenerator draws this before attempting any of its four chests.
                chestRandom.nextInt(3);
                chestChunkRandoms.put(chunkKey, chestRandom);
            }
            predictions.add(
                    new PredictedChestAlternatives(possiblePositions, DESERT_PYRAMID_LOOT, chestRandom.nextLootSeed()));
        }
        return Optional.of(List.copyOf(predictions));
    }

    public Optional<RuinedPortalVariant> generateRuinedPortal(StructurePosition structure, Identifier biomeId) {
        Objects.requireNonNull(structure);
        Objects.requireNonNull(biomeId);
        if (structure.config().structureIds().stream()
                .noneMatch(id -> id.getPath().startsWith("ruined_portal"))) {
            return Optional.empty();
        }
        return Optional.of(RuinedPortalVariant.generateVanilla(seed, structure, biomeId));
    }

    /** Selects the portal setup from the seed-derived Overworld biome sample used by Cubiomes. */
    public Optional<RuinedPortalVariant> generateRuinedPortal(
            StructurePosition structure, CubiomesOverworldBiomeSampler biomeSampler) {
        Objects.requireNonNull(biomeSampler);
        return generateRuinedPortal(structure, biomeSampler.getRuinedPortalBiomeId(structure));
    }

    /** Selects a Nether portal setup from the seed-derived Nether biome. */
    public Optional<RuinedPortalVariant> generateRuinedPortal(
            StructurePosition structure, CubiomesNetherBiomeSampler biomeSampler) {
        Objects.requireNonNull(biomeSampler);
        return generateRuinedPortal(structure, biomeSampler.getRuinedPortalBiomeId(structure));
    }

    public Optional<BlockPos> generateRuinedPortalChestPosition(
            StructurePosition structure, Identifier biomeId, CubiomesOverworldBiomeSampler terrain) {
        Objects.requireNonNull(terrain);
        return generateRuinedPortal(structure, biomeId).map(variant -> variant.chestPosition(seed, structure, terrain));
    }

    public Optional<BlockPos> generateRuinedPortalChestPosition(
            StructurePosition structure, CubiomesOverworldBiomeSampler terrain) {
        Objects.requireNonNull(terrain);
        return generateRuinedPortalChests(structure, terrain).stream()
                .map(PredictedChest::position)
                .findFirst();
    }

    public Optional<BlockPos> generateRuinedPortalChestPosition(
            StructurePosition structure, CubiomesNetherBiomeSampler terrain) {
        Objects.requireNonNull(terrain);
        return generateRuinedPortalChests(structure, terrain).stream()
                .map(PredictedChest::position)
                .findFirst();
    }

    /**
     * Returns the decoration RNG for the chunk that actually places the portal template. Vanilla
     * only places it when the current chunk box contains the transformed template bounding-box center.
     */
    public Optional<MinecraftWorldgenRandom> ruinedPortalTemplateRandom(
            StructurePosition structure, CubiomesOverworldBiomeSampler terrain) {
        Objects.requireNonNull(terrain);
        return generateRuinedPortal(structure, terrain).flatMap(variant -> {
            ChunkPos chunk = variant.templatePlacementChunk(structure);
            return decorationRandom(chunk.x, chunk.z, variant.structureId());
        });
    }

    public Optional<MinecraftWorldgenRandom> ruinedPortalTemplateRandom(
            StructurePosition structure, CubiomesNetherBiomeSampler terrain) {
        Objects.requireNonNull(terrain);
        return generateRuinedPortal(structure, terrain).flatMap(variant -> {
            ChunkPos chunk = variant.templatePlacementChunk(structure);
            return decorationRandom(chunk.x, chunk.z, variant.structureId());
        });
    }

    /** Predicts the template chest's LootTableSeed from the vanilla portal placement stream. */
    public Optional<PredictedChest> generateRuinedPortalChest(
            StructurePosition structure, CubiomesOverworldBiomeSampler terrain) {
        return generateRuinedPortalChests(structure, terrain).stream().findFirst();
    }

    public Optional<PredictedChest> generateRuinedPortalChest(
            StructurePosition structure, CubiomesNetherBiomeSampler terrain) {
        return generateRuinedPortalChests(structure, terrain).stream().findFirst();
    }

    /** Simulates vanilla's weighted structure choice and final-height biome rejection. */
    public List<PredictedChest> generateRuinedPortalChests(
            StructurePosition structure, CubiomesOverworldBiomeSampler terrain) {
        Objects.requireNonNull(terrain);
        if (!isRuinedPortalCandidate(structure)) {
            return List.of();
        }
        return generateRuinedPortalChests(
                structure,
                false,
                portal -> terrain.getRuinedPortalOriginY(portal, structure, seed),
                terrain::isRuinedPortalBiomeValid,
                terrain::canPlaceRuinedPortalChest);
    }

    public List<PredictedChest> generateRuinedPortalChests(
            StructurePosition structure, CubiomesNetherBiomeSampler terrain) {
        Objects.requireNonNull(terrain);
        if (!isRuinedPortalCandidate(structure)) {
            return List.of();
        }
        return generateRuinedPortalChests(
                structure,
                true,
                portal -> terrain.getRuinedPortalOriginY(portal, structure, seed),
                terrain::isRuinedPortalBiomeValid,
                terrain::canPlaceRuinedPortalChest);
    }

    private List<PredictedChest> generateRuinedPortalChests(
            StructurePosition structure,
            boolean nether,
            ToIntFunction<RuinedPortalVariant> originHeight,
            BiPredicate<Identifier, BlockPos> biomeCheck,
            Predicate<BlockPos> chestPlacementCheck) {
        List<Identifier> remaining = new ArrayList<>(RUINED_PORTAL_STRUCTURE_ORDER.stream()
                .filter(structure.config().structureIds()::contains)
                .toList());
        JavaLegacyRandom selectionRandom = StructureStartRandom.forCandidate(seed, structure);
        while (!remaining.isEmpty()) {
            Identifier structureId = remaining.remove(selectionRandom.nextInt(remaining.size()));
            // In the opposite dimension this structure's biome tag can never match.
            if (structureId.equals(RUINED_PORTAL_NETHER) != nether) {
                continue;
            }
            RuinedPortalVariant portal = RuinedPortalVariant.generateVanillaForStructure(seed, structure, structureId);
            int originY = originHeight.applyAsInt(portal);
            BlockPos origin =
                    new BlockPos(Math.toIntExact(structure.blockX()), originY, Math.toIntExact(structure.blockZ()));
            if (!biomeCheck.test(structureId, origin)) {
                continue;
            }
            BlockPos chest = portal.chestPosition(origin);
            if (!chestPlacementCheck.test(chest)) {
                return List.of();
            }
            ChunkPos chunk = portal.templatePlacementChunk(structure);
            Optional<MinecraftWorldgenRandom> random = decorationRandom(chunk.x, chunk.z, structureId);
            if (random.isEmpty()) {
                return List.of();
            }
            // The portal NBT chest has no seed. StructureTemplate writes the next shared stream
            // long before loading it; portal processors use per-position placement RNGs.
            return List.of(
                    new PredictedChest(chest, RUINED_PORTAL_LOOT, random.get().nextLootSeed()));
        }
        return List.of();
    }

    private static boolean isRuinedPortalCandidate(StructurePosition structure) {
        return structure.config().structureIds().stream()
                .anyMatch(id -> id.getPath().startsWith("ruined_portal"));
    }

    public List<StructurePosition> generate(StructureConfig config, ChunkRange range) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(range);
        List<StructurePosition> positions = new ArrayList<>();
        long minRegionX = Math.floorDiv(range.minChunkX(), (long) config.spacing());
        long maxRegionX = Math.floorDiv(range.maxChunkX(), (long) config.spacing());
        long minRegionZ = Math.floorDiv(range.minChunkZ(), (long) config.spacing());
        long maxRegionZ = Math.floorDiv(range.maxChunkZ(), (long) config.spacing());
        for (long regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (long regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                StructureConfig.StructureChunk chunk = config.getStartChunk(seed, regionX, regionZ);
                if (range.contains(chunk.chunkX(), chunk.chunkZ())) {
                    positions.add(new StructurePosition(config, chunk.chunkX(), chunk.chunkZ()));
                }
                if (regionZ == maxRegionZ) {
                    break;
                }
            }
            if (regionX == maxRegionX) {
                break;
            }
        }
        return List.copyOf(positions);
    }

    public List<StructurePosition> generate(Collection<StructureConfig> configs, ChunkRange range) {
        Objects.requireNonNull(configs);
        List<StructurePosition> positions = new ArrayList<>();
        for (StructureConfig config : configs) {
            positions.addAll(generate(config, range));
        }
        return List.copyOf(positions);
    }

    public List<EndCityResult> generateEndCities(StructureConfig config, ChunkRange range) {
        return generateEndCities(config, range, EndCityTemplateData.vanilla());
    }

    public List<EndCityResult> generateEndCities(
            StructureConfig config, ChunkRange range, EndCityTemplateData templateData) {
        Objects.requireNonNull(templateData);
        if (!config.containsStructure(END_CITY)) {
            return List.of();
        }
        CubiomesEndNoise endNoise = new CubiomesEndNoise(seed);
        return generate(config, range).stream()
                .map(position -> generateEndCity(position, templateData, endNoise))
                .flatMap(Optional::stream)
                .toList();
    }

    /** Generates End City starts within vanilla's structure-reference radius of the target chunks. */
    public List<EndCityResult> generateEndCitiesNear(StructureConfig config, ChunkRange chestChunkRange) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(chestChunkRange);
        ChunkRange candidateRange = new ChunkRange(
                chestChunkRange.minChunkX() - END_CITY_START_SEARCH_HALO_CHUNKS,
                chestChunkRange.minChunkZ() - END_CITY_START_SEARCH_HALO_CHUNKS,
                chestChunkRange.maxChunkX() + END_CITY_START_SEARCH_HALO_CHUNKS,
                chestChunkRange.maxChunkZ() + END_CITY_START_SEARCH_HALO_CHUNKS);
        return generateEndCities(config, candidateRange);
    }

    /** Generates complete nearby End City starts and predicts all chest loot seeds in the target chunks. */
    public Optional<List<PredictedChest>> generateEndCityChests(ChunkRange targetChunkRange) {
        Objects.requireNonNull(targetChunkRange);
        StructureConfig config = StructureConfig.vanillaConfigs().get(Identifier.ofVanilla("end_cities"));
        if (config == null) {
            return Optional.empty();
        }
        return generateEndCityChests(generateEndCitiesNear(config, targetChunkRange), targetChunkRange);
    }

    public Optional<EndCityResult> generateEndCity(StructurePosition position) {
        return generateEndCity(position, EndCityTemplateData.vanilla());
    }

    /**
     * Predicts End City chest seeds for the supplied starts. The caller must include every start
     * referenced by each target chunk; use the range-only overload to generate that complete set.
     */
    public Optional<List<PredictedChest>> generateEndCityChests(
            List<EndCityResult> nearbyStarts, ChunkRange targetChunkRange) {
        Objects.requireNonNull(nearbyStarts);
        Objects.requireNonNull(targetChunkRange);
        LinkedHashSet<ChunkPos> targetChunks = new LinkedHashSet<>();
        List<StructureReferenceOrder.Start<EndCityResult>> references = new ArrayList<>(nearbyStarts.size());
        for (EndCityResult start : nearbyStarts) {
            if (start.pieces().isEmpty()) {
                continue;
            }
            int minX =
                    start.pieces().stream().mapToInt(EndCityPiece::minX).min().orElseThrow();
            int minZ =
                    start.pieces().stream().mapToInt(EndCityPiece::minZ).min().orElseThrow();
            int maxX =
                    start.pieces().stream().mapToInt(EndCityPiece::maxX).max().orElseThrow();
            int maxZ =
                    start.pieces().stream().mapToInt(EndCityPiece::maxZ).max().orElseThrow();
            references.add(new StructureReferenceOrder.Start(
                    Math.toIntExact(start.structure().chunkX()),
                    Math.toIntExact(start.structure().chunkZ()),
                    new StructureReferenceOrder.Bounds(minX, minZ, maxX, maxZ),
                    start));
            for (EndCityMarkerPosition marker : start.chests()) {
                if (marker.generated()) {
                    int chunkX = Math.floorDiv(marker.position().getX(), 16);
                    int chunkZ = Math.floorDiv(marker.position().getZ(), 16);
                    if (targetChunkRange.contains(chunkX, chunkZ)) {
                        targetChunks.add(new ChunkPos(chunkX, chunkZ));
                    }
                }
            }
        }

        List<PredictedChest> predicted = new ArrayList<>();
        for (ChunkPos chunk : targetChunks) {
            Optional<MinecraftWorldgenRandom> chunkRandom = decorationRandom(chunk.x, chunk.z, END_CITY);
            if (chunkRandom.isEmpty()) {
                return Optional.empty();
            }
            for (StructureReferenceOrder.Start<EndCityResult> reference :
                    StructureReferenceOrder.forChunk(chunk, references)) {
                EndCityResult start = reference.value();
                for (EndCityMarkerPosition marker : start.chests()) {
                    if (marker.generated()
                            && Math.floorDiv(marker.position().getX(), 16) == chunk.x
                            && Math.floorDiv(marker.position().getZ(), 16) == chunk.z) {
                        predicted.add(new PredictedChest(
                                marker.position(),
                                END_CITY_LOOT,
                                chunkRandom.get().nextLootSeed()));
                    }
                }
            }
        }
        return Optional.of(List.copyOf(predicted));
    }

    public Optional<EndCityResult> generateEndCity(StructurePosition position, EndCityTemplateData templateData) {
        return generateEndCity(position, templateData, new CubiomesEndNoise(seed));
    }

    private Optional<EndCityResult> generateEndCity(
            StructurePosition position, EndCityTemplateData templateData, CubiomesEndNoise endNoise) {
        Objects.requireNonNull(position);
        Objects.requireNonNull(templateData);
        if (!position.config().containsStructure(END_CITY)) {
            return Optional.empty();
        }
        int chunkX = Math.toIntExact(position.chunkX());
        int chunkZ = Math.toIntExact(position.chunkZ());
        if (!endNoise.isEndCityBiome(chunkX, chunkZ)) {
            return Optional.empty();
        }
        int terrainHeight = endNoise.getEndCityTerrainHeight(chunkX, chunkZ);
        if (terrainHeight == 0) {
            return Optional.empty();
        }
        return VanillaEndCityStructureGenerator.generate(seed, position, templateData);
    }
}
