package me.matl114.utils.structures;

import me.matl114.utils.RegistryUtils;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

/**
 * Seeded 1.18+ Overworld biome and depth sampling following cubiomes' modern
 * climate-noise sampling and structure viability coordinates.
 */
public final class CubiomesOverworldBiomeSampler {
    private final long seed;
    private final BiomeSource biomeSource;
    private final MultiNoiseSampler biomeNoise;
    private final NoiseConfig noiseConfig;
    private final CubiomesDepthSampler cubiomesDepth;
    private final NoiseChunkGenerator terrainGenerator;
    private final HeightLimitView heightLimitView;

    private CubiomesOverworldBiomeSampler(
            long seed,
            BiomeSource biomeSource,
            MultiNoiseSampler biomeNoise,
            NoiseConfig noiseConfig,
            NoiseChunkGenerator terrainGenerator) {
        this.seed = seed;
        this.biomeSource = biomeSource;
        this.biomeNoise = biomeNoise;
        this.noiseConfig = noiseConfig;
        this.cubiomesDepth = new CubiomesDepthSampler(noiseConfig);
        this.terrainGenerator = terrainGenerator;
        this.heightLimitView = HeightLimitView.create(-64, 384);
    }

    public static CubiomesOverworldBiomeSampler create(long seed) {
        RegistryWrapper.WrapperLookup registries = DefaultWorldgenRegistryManager.registry();
        RegistryEntry<MultiNoiseBiomeSourceParameterList> biomeParameters =
                RegistryUtils.getRegistryEntry(registries, MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        RegistryEntry<ChunkGeneratorSettings> generatorSettings =
                RegistryUtils.getRegistryEntry(registries, ChunkGeneratorSettings.OVERWORLD);
        if (biomeParameters == null || generatorSettings == null) {
            throw new IllegalStateException("Vanilla Overworld worldgen entries are unavailable");
        }
        BiomeSource biomeSource = MultiNoiseBiomeSource.create(biomeParameters);
        NoiseConfig noiseConfig = NoiseConfig.create(
                generatorSettings.value(), registries.getWrapperOrThrow(RegistryKeys.NOISE_PARAMETERS), seed);
        return new CubiomesOverworldBiomeSampler(
                seed,
                biomeSource,
                noiseConfig.getMultiNoiseSampler(),
                noiseConfig,
                new NoiseChunkGenerator(biomeSource, generatorSettings));
    }

    public boolean isAncientCityBiomeViable(long seed, StructurePosition structure) {
        BlockPos sample = CubiomesAncientCityBiome.samplePosition(seed, structure);
        return biomeAtQuart(sample.getX() >> 2, sample.getY() >> 2, sample.getZ() >> 2)
                .matchesKey(BiomeKeys.DEEP_DARK);
    }

    /** Checks the vanilla Trial Chambers biome tag at the candidate chunk's center and sea level. */
    public boolean isTrialChambersBiomeViable(StructurePosition structure) {
        var trialChambers = DefaultWorldgenRegistryManager.registry()
                .getWrapperOrThrow(RegistryKeys.STRUCTURE)
                .get(Identifier.ofVanilla("trial_chambers"));
        if (trialChambers == null) {
            return false;
        }
        int quartX = Math.toIntExact(structure.chunkX() * 4L + 2L);
        int quartY = terrainGenerator.getSeaLevel() >> 2;
        int quartZ = Math.toIntExact(structure.chunkZ() * 4L + 2L);
        return BiomeValidity.contains(
                trialChambers.getValidBiomes(), biomeAtQuart(quartX, quartY, quartZ));
    }

    /** Cubiomes viewer's ruined-portal biome sample: chunk center at quart Y=0. */
    public Identifier getRuinedPortalBiomeId(StructurePosition structure) {
        int quartX = Math.toIntExact(structure.chunkX() * 4L + 2L);
        int quartZ = Math.toIntExact(structure.chunkZ() * 4L + 2L);
        RegistryKey<Biome> biomeKey = biomeAtQuart(quartX, 0, quartZ)
                .getKey()
                .orElseThrow(() -> new IllegalStateException("Sampled biome has no registry key"));
        return biomeKey.getValue();
    }

    /** Applies vanilla's second portal biome check at the generated structure origin. */
    public boolean isRuinedPortalBiomeValid(Identifier structureId, BlockPos origin) {
        RegistryEntry<Biome> biome = biomeAtQuart(origin.getX() >> 2, origin.getY() >> 2, origin.getZ() >> 2);
        var structure = DefaultWorldgenRegistryManager.registry()
                .getWrapperOrThrow(RegistryKeys.STRUCTURE)
                .get(structureId);
        return structure != null && BiomeValidity.contains(structure.getValidBiomes(), biome);
    }

    /** Cubiomes Viewer 1.21.11 biome and approximate four-corner depth filter. */
    public boolean isDesertPyramidCubiomesViable(StructurePosition structure) {
        int quartX = Math.toIntExact(structure.chunkX() * 4L + 2L);
        int quartZ = Math.toIntExact(structure.chunkZ() * 4L + 2L);
        if (!biomeAtQuart(quartX, 319 >> 2, quartZ).matchesKey(BiomeKeys.DESERT)) {
            return false;
        }

        int blockX = Math.toIntExact(structure.blockX());
        int blockZ = Math.toIntExact(structure.blockZ());
        int[][] corners = {
            {0, 0},
            {21, 21},
            {0, 21},
            {21, 0}
        };
        for (int[] corner : corners) {
            double depth = cubiomesDepth.sample(blockX + corner[0], blockZ + corner[1]);
            if (depth < 0.48D) {
                return false;
            }
        }
        return true;
    }

    /** Reproduces the vanilla 1.21.11 BasicTempleStructure checks that decide if a start exists. */
    public boolean isDesertPyramidViable(StructurePosition structure) {
        DesertPyramidVariant variant = DesertPyramidVariant.generate(seed, structure);
        int startX = Math.toIntExact(structure.blockX());
        int startZ = Math.toIntExact(structure.blockZ());
        Heightmap.Type startHeightmap = Heightmap.Type.WORLD_SURFACE_WG;
        int minCornerHeight = Math.min(
                Math.min(
                        terrainGenerator.getHeightInGround(
                                startX, startZ, startHeightmap, heightLimitView, noiseConfig),
                        terrainGenerator.getHeightInGround(
                                startX + variant.sizeX(), startZ, startHeightmap, heightLimitView, noiseConfig)),
                Math.min(
                        terrainGenerator.getHeightInGround(
                                startX, startZ + variant.sizeZ(), startHeightmap, heightLimitView, noiseConfig),
                        terrainGenerator.getHeightInGround(
                                startX + variant.sizeX(),
                                startZ + variant.sizeZ(),
                                startHeightmap,
                                heightLimitView,
                                noiseConfig)));
        if (minCornerHeight < terrainGenerator.getSeaLevel()) {
            return false;
        }

        var desertPyramid = DefaultWorldgenRegistryManager.registry()
                .getWrapperOrThrow(RegistryKeys.STRUCTURE)
                .get(Identifier.ofVanilla("desert_pyramid"));
        if (desertPyramid == null) {
            return false;
        }
        int centerX = Math.toIntExact(structure.chunkX() * 16L + 8L);
        int centerZ = Math.toIntExact(structure.chunkZ() * 16L + 8L);
        int centerY =
                terrainGenerator.getHeightInGround(centerX, centerZ, startHeightmap, heightLimitView, noiseConfig);
        RegistryEntry<Biome> startBiome = biomeAtQuart(centerX >> 2, centerY >> 2, centerZ >> 2);
        return BiomeValidity.contains(desertPyramid.getValidBiomes(), startBiome);
    }

    /** Samples the same raw noise height that vanilla structure placement queries. */
    public int getHeight(int blockX, int blockZ, Heightmap.Type heightmap) {
        return terrainGenerator.getHeight(blockX, blockZ, heightmap, heightLimitView, noiseConfig);
    }

    /** Checks a vanilla structure processor's protected-block rule against default seeded terrain. */
    public boolean canPlaceGeneratedStructureBlock(BlockPos position) {
        if (!heightLimitView.isInHeightLimit(position.getY())) {
            return false;
        }
        BlockState terrainState = terrainGenerator
                .getColumnSample(position.getX(), position.getZ(), heightLimitView, noiseConfig)
                .getState(position.getY());
        return !DefaultWorldgenRegistryManager.registry()
                .getWrapperOrThrow(RegistryKeys.BLOCK)
                .getEntry(terrainState.getBlock())
                .isIn(BlockTags.FEATURES_CANNOT_REPLACE);
    }

    public boolean canPlaceRuinedPortalChest(BlockPos position) {
        return canPlaceGeneratedStructureBlock(position);
    }

    NoiseChunkGenerator terrainGenerator() {
        return terrainGenerator;
    }

    BiomeSource biomeSource() {
        return biomeSource;
    }

    NoiseConfig noiseConfig() {
        return noiseConfig;
    }

    HeightLimitView heightLimitView() {
        return heightLimitView;
    }

    /** Returns the lowest raw-noise height over a structure footprint, inclusive. */
    public int getLowestHeight(int minBlockX, int minBlockZ, int sizeX, int sizeZ, Heightmap.Type heightmap) {
        int lowest = Integer.MAX_VALUE;
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                lowest = Math.min(lowest, getHeight(minBlockX + x, minBlockZ + z, heightmap));
            }
        }
        return lowest;
    }

    public int getRuinedPortalOriginY(RuinedPortalVariant variant, StructurePosition structure, long seed) {
        RuinedPortalVariant.HorizontalBounds bounds = variant.horizontalBounds(structure);
        boolean oceanFloor = variant.verticalPlacement() == RuinedPortalVariant.VerticalPlacement.ON_OCEAN_FLOOR;
        Heightmap.Type heightmap = oceanFloor ? Heightmap.Type.OCEAN_FLOOR_WG : Heightmap.Type.WORLD_SURFACE_WG;
        int height = getHeight(bounds.centerX(), bounds.centerZ(), heightmap) - 1;
        int templateHeight = variant.templateData().sizeY();
        JavaLegacyRandom random = variant.randomAtOriginHeight(seed, structure);
        int minY = heightLimitView.getBottomY() + 15;

        switch (variant.verticalPlacement()) {
            case IN_MOUNTAIN -> height = choosePlacementHeight(random, 70, height - templateHeight);
            case UNDERGROUND -> height = choosePlacementHeight(random, minY, height - templateHeight);
            case PARTLY_BURIED -> height = height - templateHeight + random.nextInt(7) + 2;
            case IN_NETHER -> throw new IllegalArgumentException(
                    "Nether ruined portals require a Nether NoiseConfig sampler");
            case ON_LAND_SURFACE, ON_OCEAN_FLOOR -> {}
        }
        return lowerPortalToThreeGroundCorners(bounds, height, heightmap, minY);
    }

    private int lowerPortalToThreeGroundCorners(
            RuinedPortalVariant.HorizontalBounds bounds, int height, Heightmap.Type heightmap, int minimumHeight) {
        VerticalBlockSample[] columns = {
            terrainGenerator.getColumnSample(bounds.minX(), bounds.minZ(), heightLimitView, noiseConfig),
            terrainGenerator.getColumnSample(bounds.maxX(), bounds.minZ(), heightLimitView, noiseConfig),
            terrainGenerator.getColumnSample(bounds.minX(), bounds.maxZ(), heightLimitView, noiseConfig),
            terrainGenerator.getColumnSample(bounds.maxX(), bounds.maxZ(), heightLimitView, noiseConfig)
        };
        for (int y = height; y > minimumHeight; y--) {
            int matchingCorners = 0;
            for (VerticalBlockSample column : columns) {
                if (heightmap.getBlockPredicate().test(column.getState(y))) {
                    matchingCorners++;
                    if (matchingCorners == 3) {
                        return y;
                    }
                }
            }
        }
        return Math.min(height, minimumHeight);
    }

    private static int choosePlacementHeight(JavaLegacyRandom random, int minimum, int maximum) {
        return minimum < maximum ? betweenInclusive(random, minimum, maximum) : maximum;
    }

    private static int betweenInclusive(JavaLegacyRandom random, int minimum, int maximum) {
        return minimum + random.nextInt(maximum - minimum + 1);
    }

    private RegistryEntry<Biome> biomeAtQuart(int quartX, int quartY, int quartZ) {
        return biomeSource.getBiome(quartX, quartY, quartZ, biomeNoise);
    }
}
