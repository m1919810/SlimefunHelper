package me.matl114.utils.structures;

import me.matl114.utils.RegistryUtils;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

/** Seeded Nether biome and raw-noise sampling for ruined portal generation. */
public final class CubiomesNetherBiomeSampler {
    private final BiomeSource biomeSource;
    private final MultiNoiseSampler biomeNoise;
    private final NoiseConfig noiseConfig;
    private final NoiseChunkGenerator terrainGenerator;
    private final HeightLimitView heightLimitView;

    private CubiomesNetherBiomeSampler(
            BiomeSource biomeSource,
            MultiNoiseSampler biomeNoise,
            NoiseConfig noiseConfig,
            NoiseChunkGenerator terrainGenerator) {
        this.biomeSource = biomeSource;
        this.biomeNoise = biomeNoise;
        this.noiseConfig = noiseConfig;
        this.terrainGenerator = terrainGenerator;
        this.heightLimitView = HeightLimitView.create(0, 128);
    }

    public static CubiomesNetherBiomeSampler create(long seed) {
        RegistryWrapper.WrapperLookup registries = DefaultWorldgenRegistryManager.registry();
        RegistryEntry<MultiNoiseBiomeSourceParameterList> biomeParameters = RegistryUtils.getRegistryEntry(
                registries, MultiNoiseBiomeSourceParameterLists.NETHER);
        RegistryEntry<ChunkGeneratorSettings> generatorSettings = RegistryUtils.getRegistryEntry(
                registries, ChunkGeneratorSettings.NETHER);
        if (biomeParameters == null || generatorSettings == null) {
            throw new IllegalStateException("Vanilla Nether worldgen entries are unavailable");
        }
        BiomeSource biomeSource = MultiNoiseBiomeSource.create(biomeParameters);
        NoiseConfig noiseConfig = NoiseConfig.create(
                generatorSettings.value(), registries.getWrapperOrThrow(RegistryKeys.NOISE_PARAMETERS), seed);
        return new CubiomesNetherBiomeSampler(
                biomeSource,
                noiseConfig.getMultiNoiseSampler(),
                noiseConfig,
                new NoiseChunkGenerator(biomeSource, generatorSettings));
    }

    /** Samples the candidate chunk center at quart Y=0, matching the viewer's biome lookup. */
    public net.minecraft.util.Identifier getRuinedPortalBiomeId(StructurePosition structure) {
        int quartX = Math.toIntExact(structure.chunkX() * 4L + 2L);
        int quartZ = Math.toIntExact(structure.chunkZ() * 4L + 2L);
        RegistryKey<Biome> biomeKey = biomeSource.getBiome(quartX, 0, quartZ, biomeNoise)
                .getKey()
                .orElseThrow(() -> new IllegalStateException("Sampled biome has no registry key"));
        return biomeKey.getValue();
    }

    /** Applies vanilla's final ruined-portal biome-tag check at the generated origin. */
    public boolean isRuinedPortalBiomeValid(
            net.minecraft.util.Identifier structureId, net.minecraft.util.math.BlockPos origin) {
        int quartX = origin.getX() >> 2;
        int quartY = origin.getY() >> 2;
        int quartZ = origin.getZ() >> 2;
        RegistryEntry<Biome> biome = biomeSource.getBiome(quartX, quartY, quartZ, biomeNoise);
        var structure = DefaultWorldgenRegistryManager.registry()
                .getWrapperOrThrow(RegistryKeys.STRUCTURE)
                .get(structureId);
        return structure != null && BiomeValidity.contains(structure.getValidBiomes(), biome);
    }

    /** Reproduces RuinedPortalStructure.findSuitableY for the IN_NETHER placement. */
    public int getRuinedPortalOriginY(
            RuinedPortalVariant variant, StructurePosition structure, long seed) {
        if (variant.verticalPlacement() != RuinedPortalVariant.VerticalPlacement.IN_NETHER) {
            throw new IllegalArgumentException("The selected portal setup is not the Nether placement");
        }

        RuinedPortalVariant.HorizontalBounds bounds = variant.horizontalBounds(structure);
        JavaLegacyRandom random = variant.randomAtOriginHeight(seed, structure);
        int y;
        if (variant.airPocket()) {
            y = betweenInclusive(random, 32, 100);
        } else if (random.nextFloat() < 0.5F) {
            y = betweenInclusive(random, 27, 29);
        } else {
            y = betweenInclusive(random, 29, 100);
        }

        VerticalBlockSample[] columns = {
            terrainGenerator.getColumnSample(bounds.minX(), bounds.minZ(), heightLimitView, noiseConfig),
            terrainGenerator.getColumnSample(bounds.maxX(), bounds.minZ(), heightLimitView, noiseConfig),
            terrainGenerator.getColumnSample(bounds.minX(), bounds.maxZ(), heightLimitView, noiseConfig),
            terrainGenerator.getColumnSample(bounds.maxX(), bounds.maxZ(), heightLimitView, noiseConfig)
        };
        int minimumY = heightLimitView.getBottomY() + 15;
        for (int candidateY = y; candidateY > minimumY; candidateY--) {
            int opaqueCorners = 0;
            for (VerticalBlockSample column : columns) {
                if (Heightmap.Type.WORLD_SURFACE_WG.getBlockPredicate().test(column.getState(candidateY))
                        && ++opaqueCorners == 3) {
                    return candidateY;
                }
            }
        }
        return minimumY;
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

    private static int betweenInclusive(JavaLegacyRandom random, int minimum, int maximum) {
        return minimum + random.nextInt(maximum - minimum + 1);
    }
}
