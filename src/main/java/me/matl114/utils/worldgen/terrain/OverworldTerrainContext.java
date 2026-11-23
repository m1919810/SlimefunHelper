package me.matl114.utils.worldgen.terrain;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import me.matl114.utils.RegistryUtils;
import me.matl114.utils.worldgen.DefaultWorldgenRegistryManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettesFactory;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.gen.StructureWeightSampler;
import net.minecraft.world.gen.carver.CarverContext;
import net.minecraft.world.gen.carver.ConfiguredCarver;
import net.minecraft.world.gen.carver.ConfiguredCarvers;
import net.minecraft.world.gen.chunk.AquiferSampler;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.chunk.GenerationShapeConfig;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla overworld terrain generation for one world seed, built from the built-in data pack
 * rather than any server registry. Instances are immutable and safe to share between threads,
 * like the generator state a server shares between its worldgen workers.
 *
 * <p>{@link #generateChunk} reproduces the chunk statuses that run before features:
 * {@code NOISE} (density functions, noise caves and aquifers, via vanilla's
 * {@code NoiseChunkGenerator.populateNoise}) and {@code CARVERS} (the same loop as
 * {@code NoiseChunkGenerator.carve}). Not reproduced: structure terrain adaptation
 * ({@code Beardifier}), {@code SURFACE} rules (they only swap solid blocks), structure pieces and
 * features of earlier generation steps.
 */
public final class OverworldTerrainContext {
    public static final int BOTTOM_Y = -64;
    public static final int HEIGHT = 384;

    /** Every overworld biome lists exactly these carvers, in this order (1.21.1 to 26.1). */
    private static final List<RegistryKey<ConfiguredCarver<?>>> OVERWORLD_CARVERS =
            List.of(ConfiguredCarvers.CAVE, ConfiguredCarvers.CAVE_EXTRA_UNDERGROUND, ConfiguredCarvers.CANYON);

    private final long seed;
    private final DynamicRegistryManager registries;
    private final RegistryEntry<ChunkGeneratorSettings> settings;
    private final BiomeSource biomeSource;
    private final NoiseConfig noiseConfig;
    private final NoiseChunkGenerator generator;
    private final HeightLimitView heightLimitView;
    private final AquiferSampler.FluidLevelSampler fluidLevelSampler;
    private final List<ConfiguredCarver<?>> carvers;
    private final BiomeAccess biomeAccess;
    private final PalettesFactory palettes;
    /** Sections default to stone, so carvers can record every point they would carve. */
    private final PalettesFactory stonePalettes;

    private final StructureWeightSampler noStructures = new StructureWeightSampler(List.of(), List.of(), null);
    private final Map<Block, Boolean> cannotReplace = new ConcurrentHashMap<>();
    private final Map<Block, Boolean> carverReplaceable = new ConcurrentHashMap<>();

    private OverworldTerrainContext(long seed) {
        this.seed = seed;
        this.registries = DefaultWorldgenRegistryManager.registry();
        RegistryEntry<MultiNoiseBiomeSourceParameterList> biomeParameters =
                RegistryUtils.getRegistryEntry(registries, MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        RegistryEntry<ChunkGeneratorSettings> generatorSettings =
                RegistryUtils.getRegistryEntry(registries, ChunkGeneratorSettings.OVERWORLD);
        if (biomeParameters == null || generatorSettings == null) {
            throw new IllegalStateException("Vanilla Overworld worldgen entries are unavailable");
        }
        this.settings = generatorSettings;
        this.biomeSource = MultiNoiseBiomeSource.create(biomeParameters);
        this.noiseConfig = NoiseConfig.create(
                generatorSettings.value(), registries.getWrapperOrThrow(RegistryKeys.NOISE_PARAMETERS), seed);
        this.generator = new NoiseChunkGenerator(biomeSource, generatorSettings);
        this.heightLimitView = HeightLimitView.create(BOTTOM_Y, HEIGHT);
        this.fluidLevelSampler = createFluidLevelSampler(generatorSettings.value());
        var carverRegistry = registries.getWrapperOrThrow(RegistryKeys.CONFIGURED_CARVER);
        this.carvers = OVERWORLD_CARVERS.stream()
                .map(key -> carverRegistry.getOrThrow(key).value())
                .<ConfiguredCarver<?>>map(carver -> carver)
                .toList();
        this.biomeAccess = new BiomeAccess(
                (biomeX, biomeY, biomeZ) ->
                        biomeSource.getBiome(biomeX, biomeY, biomeZ, noiseConfig.getMultiNoiseSampler()),
                BiomeAccess.hashSeed(seed));
        this.palettes = PalettesFactory.fromRegistryManager(registries);
        this.stonePalettes = new PalettesFactory(
                palettes.blockStatesStrategy(),
                Blocks.STONE.getDefaultState(),
                palettes.blockStatesContainerCodec(),
                palettes.biomeStrategy(),
                palettes.defaultBiome(),
                palettes.biomeContainerCodec());
    }

    public static OverworldTerrainContext create(long seed) {
        return new OverworldTerrainContext(seed);
    }

    /** Mirrors the private {@code NoiseChunkGenerator.createFluidLevelSampler}. */
    private static AquiferSampler.FluidLevelSampler createFluidLevelSampler(ChunkGeneratorSettings settings) {
        AquiferSampler.FluidLevel lava = new AquiferSampler.FluidLevel(-54, Blocks.LAVA.getDefaultState());
        int seaLevel = settings.seaLevel();
        AquiferSampler.FluidLevel sea = new AquiferSampler.FluidLevel(seaLevel, settings.defaultFluid());
        return (x, y, z) -> y < Math.min(-54, seaLevel) ? lava : sea;
    }

    /**
     * Generates one chunk up to (and including) the {@code CARVERS} status. Every carver start
     * within eight chunks is replayed with {@code ChunkRandom.setCarverSeed(seed + index, x, z)}, as
     * in {@code NoiseChunkGenerator.carve}.
     */
    public ProtoChunk generateChunk(ChunkPos pos) {
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.NO_UPGRADE_DATA, heightLimitView, palettes, null);
        ChunkNoiseSampler sampler = chunk.getOrCreateChunkNoiseSampler(created -> ChunkNoiseSampler.create(
                created,
                noiseConfig,
                // What StructureWeightSampler.createStructureWeightSampler returns without structures.
                noStructures,
                settings.value(),
                fluidLevelSampler,
                Blender.getNoBlending()));
        // The structure accessor is only used to create the sampler, which already exists.
        generator
                .populateNoise(Blender.getNoBlending(), noiseConfig, null, chunk)
                .join();
        carve(chunk, sampler, sampler.getAquiferSampler());
        return chunk;
    }

    /**
     * The chunk's {@code NOISE} status as a compact block array, filled by one full-height sampler on
     * the calling thread with the same loop as {@code NoiseChunkGenerator.populateNoise} (which would
     * otherwise hop to the shared worldgen executor). Index:
     * {@code ((y - BOTTOM_Y) * 16 + localZ) * 16 + localX}.
     */
    public NoiseBlocks noiseChunk(int chunkX, int chunkZ) {
        return sampleChunk(chunkX, chunkZ, BOTTOM_Y, BOTTOM_Y + HEIGHT).blocks();
    }

    /** A chunk's noise together with the aquifer of the sampler that produced it. */
    public record ChunkNoise(NoiseBlocks blocks, AquiferSampler aquifer) {}

    /**
     * A chunk's noise over the height band {@code [minY, maxY)} (widened to whole cells), keeping the
     * sampler's aquifer so carving needs no second sampler. That aquifer is built for the same band
     * and must only answer positions inside it.
     */
    public ChunkNoise sampleChunk(int chunkX, int chunkZ, int minY, int maxY) {
        TerrainNoiseSampler[] sampler = new TerrainNoiseSampler[1];
        NoiseBlocks blocks = noiseRegion(chunkX * 16, chunkZ * 16, 16 / cellWidth(), minY, maxY, sampler);
        return new ChunkNoise(blocks, sampler[0].getAquiferSampler());
    }

    /**
     * The aquifer vanilla's chunk sampler would use for this chunk, without sampling any noise. It is
     * a pure function of position, so this gives the same fluid decisions as generation.
     */
    public AquiferSampler aquifer(int chunkX, int chunkZ) {
        GenerationShapeConfig shape = settings.value().generationShapeConfig().trimHeight(heightLimitView);
        return new TerrainNoiseSampler(
                        16 / shape.horizontalCellBlockCount(),
                        noiseConfig,
                        chunkX * 16,
                        chunkZ * 16,
                        shape,
                        noStructures,
                        settings.value(),
                        fluidLevelSampler)
                .getAquiferSampler();
    }

    /** Horizontal size of one noise cell, in blocks (4 in the overworld). */
    public int cellWidth() {
        return settings.value().generationShapeConfig().horizontalCellBlockCount();
    }

    /**
     * Noise for one cell column ({@link #cellWidth()} blocks square) over {@code [minY, maxY)}, for
     * neighbour chunks a room only reaches into. Sampler setup dominates small regions, so prefer
     * {@link #sampleChunk} when most cells are needed.
     */
    public NoiseBlocks noiseCell(int cellStartX, int cellStartZ, int minY, int maxY) {
        return noiseRegion(cellStartX, cellStartZ, 1, minY, maxY, null);
    }

    /**
     * Noise for a square of {@code cellsPerSide} x {@code cellsPerSide} cells starting at the
     * cell-aligned block {@code startX}/{@code startZ}, over {@code [minY, maxY)}. One sampler covers
     * them all, which is much cheaper than one {@link #noiseCell} each.
     */
    public NoiseBlocks noiseCells(int startX, int startZ, int cellsPerSide, int minY, int maxY) {
        return noiseRegion(startX, startZ, cellsPerSide, minY, maxY, null);
    }

    /**
     * A square, cell-aligned region of {@code cellsPerSide} cells over the height band
     * {@code [minY, maxY)}, filled like {@code populateNoise}. The band is widened to whole noise
     * cells and clamped to the world. Cell interpolation is anchored to absolute coordinates and the
     * aquifer is a function of position, so a band gives the same blocks as the full-height pass.
     */
    private NoiseBlocks noiseRegion(
            int startX, int startZ, int cellsPerSide, int minY, int maxY, @Nullable TerrainNoiseSampler[] samplerOut) {
        GenerationShapeConfig full = settings.value().generationShapeConfig().trimHeight(heightLimitView);
        int cellWidth = full.horizontalCellBlockCount();
        int cellHeight = full.verticalCellBlockCount();
        int bandMin = Math.max(full.minimumY(), Math.floorDiv(minY, cellHeight) * cellHeight);
        int bandMax = Math.min(
                full.minimumY() + full.height(), Math.ceilDiv(Math.max(maxY, bandMin + 1), cellHeight) * cellHeight);
        GenerationShapeConfig shape =
                new GenerationShapeConfig(bandMin, bandMax - bandMin, full.horizontalSize(), full.verticalSize());
        int cellsPerChunk = cellsPerSide;
        int width = cellsPerSide * cellWidth;
        int minimumCellY = Math.floorDiv(shape.minimumY(), cellHeight);
        int cellCount = Math.floorDiv(shape.height(), cellHeight);
        TerrainNoiseSampler sampler = new TerrainNoiseSampler(
                cellsPerChunk, noiseConfig, startX, startZ, shape, noStructures, settings.value(), fluidLevelSampler);
        BlockState defaultBlock = settings.value().defaultBlock();
        NoiseBlocks.Builder blocks = new NoiseBlocks.Builder(width, bandMin, bandMax);
        sampler.sampleStartDensity();
        for (int cellX = 0; cellX < cellsPerChunk; cellX++) {
            sampler.sampleEndDensity(cellX);
            for (int cellZ = 0; cellZ < cellsPerChunk; cellZ++) {
                for (int cellY = cellCount - 1; cellY >= 0; cellY--) {
                    sampler.onSampledCellCorners(cellY, cellZ);
                    for (int dy = cellHeight - 1; dy >= 0; dy--) {
                        int y = (minimumCellY + cellY) * cellHeight + dy;
                        sampler.interpolateY(y, (double) dy / cellHeight);
                        for (int dx = 0; dx < cellWidth; dx++) {
                            int localX = cellX * cellWidth + dx;
                            sampler.interpolateX(startX + localX, (double) dx / cellWidth);
                            for (int dz = 0; dz < cellWidth; dz++) {
                                int localZ = cellZ * cellWidth + dz;
                                sampler.interpolateZ(startZ + localZ, (double) dz / cellWidth);
                                BlockState state = sampler.sample();
                                blocks.set(localX, y, localZ, state == null ? defaultBlock : state);
                            }
                        }
                    }
                }
            }
            sampler.swapBuffers();
        }
        sampler.stopInterpolation();
        if (samplerOut != null) {
            samplerOut[0] = sampler;
        }
        return blocks.build();
    }

    /**
     * Vanilla's {@code preliminary_surface_level} density function: a cheap 2D estimate of the
     * surface used by aquifers. It is usually below the real surface (by about 15 blocks at the
     * median, up to about 100 on jagged peaks), so callers add a safety margin.
     */
    public double preliminarySurface(int x, int z) {
        return noiseConfig
                .getNoiseRouter()
                .preliminarySurfaceLevel()
                .sample(new DensityFunction.UnblendedNoisePos(x, 0, z));
    }

    /**
     * Marks the points carvers would carve in this chunk, using an all-stone chunk.
     *
     * <p>{@code Carver.carveAtPoint} changes a point only if the block there is carvable, and what it
     * writes (lava below the lava level, otherwise {@code aquifer.apply(pos, 0)}, where {@code null}
     * keeps the block) depends only on the position. Carvers here get a stand-in aquifer that writes
     * {@link #CARVED} instead; callers resolve a marked point with {@link #aquifer} only when they
     * read it. Building that aquifer costs about two thirds of carving, and rooms in solid rock
     * rarely read a carved point. The final block is
     * {@code layer == stone || !carvable(noise) ? noise : layer == CARVED ? aquifer(pos) ?? noise : layer}.
     */
    public ProtoChunk carveLayer(ChunkPos pos) {
        // Every section starts as stone (the stonePalettes default). Sections created from a palette
        // count as empty until recounted, and ProtoChunk.getBlockState reports empty sections as air,
        // which carvers would refuse to carve; recounting makes them read as stone.
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.NO_UPGRADE_DATA, heightLimitView, stonePalettes, null);
        for (ChunkSection section : chunk.getSectionArray()) {
            section.calculateCounts();
        }
        // Stone is carvable everywhere, so the layer records every point a carver reaches, whatever
        // the real noise there is; the caller applies the carvable test against the real block.
        carve(chunk, null, MARKING_AQUIFER);
        return chunk;
    }

    /**
     * Written by carvers where the real aquifer has not been consulted yet. Structure void never
     * comes out of noise, an aquifer or a carver, so it cannot be confused with a real block.
     */
    public static final BlockState CARVED = Blocks.STRUCTURE_VOID.getDefaultState();

    /**
     * Stands in for the aquifer while carving: every query is answered with {@link #CARVED}, which
     * the carver writes as the point's new block. Lava below the lava level is decided by the
     * carver itself and still written as lava. No fluid ticks are needed for a scratch chunk.
     */
    private static final AquiferSampler MARKING_AQUIFER = new AquiferSampler() {
        @Override
        public BlockState apply(DensityFunction.NoisePos pos, double density) {
            return CARVED;
        }

        @Override
        public boolean needsFluidTick() {
            return false;
        }
    };

    /** Whether the overworld carvers' {@code #minecraft:overworld_carver_replaceables} contains the block. */
    public boolean carverReplaceable(BlockState state) {
        return carverReplaceable.computeIfAbsent(state.getBlock(), block -> registries
                .getWrapperOrThrow(RegistryKeys.BLOCK)
                .getEntry(block)
                .isIn(BlockTags.OVERWORLD_CARVER_REPLACEABLES));
    }

    /**
     * The loop of {@code NoiseChunkGenerator.carve}. {@code sampler} is only used by surface-rule
     * fixes after carving grass, which an all-stone chunk never triggers, so it may be null there.
     */
    private void carve(ProtoChunk chunk, @Nullable ChunkNoiseSampler sampler, AquiferSampler aquifer) {
        ChunkRandom random = new ChunkRandom(new CheckedRandom(0L));
        CarverContext context = new CarverContext(
                generator,
                registries,
                heightLimitView,
                sampler,
                noiseConfig,
                settings.value().surfaceRule());
        var mask = chunk.getOrCreateCarvingMask();
        ChunkPos center = chunk.getPos();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                ChunkPos start = new ChunkPos(center.x + dx, center.z + dz);
                for (int index = 0; index < carvers.size(); index++) {
                    ConfiguredCarver<?> carver = carvers.get(index);
                    random.setCarverSeed(seed + index, start.x, start.z);
                    if (carver.shouldCarve(random)) {
                        carver.carve(context, chunk, biomeAccess::getBiome, random, aquifer, start, mask);
                    }
                }
            }
        }
    }

    /** The biome vanilla samples at block coordinates, for callers that need it. */
    public RegistryEntry<net.minecraft.world.biome.Biome> getBiome(int blockX, int blockY, int blockZ) {
        return biomeSource.getBiome(
                BiomeCoords.fromBlock(blockX),
                BiomeCoords.fromBlock(blockY),
                BiomeCoords.fromBlock(blockZ),
                noiseConfig.getMultiNoiseSampler());
    }

    /** Whether {@code Feature.setBlockStateIf} with {@code FEATURES_CANNOT_REPLACE} must skip this block. */
    public boolean featuresCannotReplace(BlockState state) {
        return cannotReplace.computeIfAbsent(Objects.requireNonNull(state).getBlock(), block -> registries
                .getWrapperOrThrow(RegistryKeys.BLOCK)
                .getEntry(block)
                .isIn(BlockTags.FEATURES_CANNOT_REPLACE));
    }

    public long seed() {
        return seed;
    }

    public BiomeSource biomeSource() {
        return biomeSource;
    }

    public NoiseConfig noiseConfig() {
        return noiseConfig;
    }

    public NoiseChunkGenerator generator() {
        return generator;
    }

    public HeightLimitView heightLimitView() {
        return heightLimitView;
    }
}
