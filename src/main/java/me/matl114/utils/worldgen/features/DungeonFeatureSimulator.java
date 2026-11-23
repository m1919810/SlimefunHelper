package me.matl114.utils.worldgen.features;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import me.matl114.utils.worldgen.MinecraftWorldgenRandom;
import me.matl114.utils.worldgen.loot.LootVersion;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Replays overworld dungeon generation for one chunk from the world seed.
 *
 * <p>Random stream: {@code ChunkGenerator.generateFeatures} seeds a Xoroshiro {@code ChunkRandom}
 * with {@code setPopulationSeed(worldSeed, chunkMinX, chunkMinZ)} and
 * {@code setDecoratorSeed(populationSeed, featureIndex, step)}. {@code PlacedFeature.generate}
 * builds its positions as a lazy {@code flatMap} chain ending in {@code forEach}, so each attempt
 * runs its placement draws and then {@code DungeonFeature.generate} before the next attempt starts.
 * A failed room still consumed its two size draws; a successful room also consumed its floor,
 * chest and spawner draws. All four supported versions share this code
 * ({@code net/minecraft/world/gen/feature/DungeonFeature.java}, {@code PlacedFeature.java} and the
 * placement modifiers differ only in Yarn parameter names; 26.1's {@code MonsterRoomFeature} matches).
 */
public final class DungeonFeatureSimulator {
    private static final int SPAWNER_ENTITY_COUNT = 4;
    private static final int CHEST_COUNT = 2;
    private static final int CHEST_ATTEMPTS = 3;
    private static final BlockState CAVE_AIR = Blocks.CAVE_AIR.getDefaultState();

    private DungeonFeatureSimulator() {}

    /**
     * Returns every attempt that generated a room according to {@code terrain}, plus a
     * {@link DungeonPrediction.Confidence#SEED_ONLY} prediction for each attempt whose room could not
     * be observed. Unobserved attempts are assumed to have failed when continuing the stream.
     */
    public static List<DungeonPrediction> simulateChunk(
            long worldSeed, int chunkX, int chunkZ, DungeonTerrain terrain, LootVersion lootVersion) {
        Objects.requireNonNull(terrain);
        Objects.requireNonNull(lootVersion);
        List<DungeonPrediction> predictions = new ArrayList<>();
        for (DungeonFeatureConfig feature : DungeonFeatureConfig.OVERWORLD) {
            simulateFeature(worldSeed, chunkX, chunkZ, feature, terrain, lootVersion, predictions);
        }
        return List.copyOf(predictions);
    }

    private static void simulateFeature(
            long worldSeed,
            int chunkX,
            int chunkZ,
            DungeonFeatureConfig feature,
            DungeonTerrain terrain,
            LootVersion lootVersion,
            List<DungeonPrediction> predictions) {
        MinecraftWorldgenRandom random = MinecraftWorldgenRandom.forPlacedFeature(
                worldSeed, chunkX, chunkZ, feature.featureIndex(), feature.generationStep());
        int heightRange = feature.maxYInclusive() - feature.minY() + 1;
        for (int attempt = 0; attempt < feature.attemptsPerChunk(); attempt++) {
            // SquarePlacementModifier, then UniformHeightProvider via MathHelper.nextBetween.
            int x = chunkX * 16 + random.nextInt(16);
            int z = chunkZ * 16 + random.nextInt(16);
            int y = random.nextInt(heightRange) + feature.minY();
            BlockPos origin = new BlockPos(x, y, z);
            // DungeonFeature.generate: both room radii are drawn before the terrain check.
            int radiusX = random.nextInt(2) + 2;
            int radiusZ = random.nextInt(2) + 2;

            Room room = new Room(feature, chunkX, chunkZ, attempt, origin, radiusX, radiusZ);
            if (!terrain.isObservable(origin, radiusX, radiusZ)) {
                predictions.add(room.generate(random.copy(), DungeonTerrain.UNKNOWN, lootVersion, true));
                continue;
            }
            MinecraftWorldgenRandom branch = random.copy();
            DungeonPrediction generated = room.generate(branch, terrain, lootVersion, false);
            if (generated != null) {
                predictions.add(generated);
                random = branch;
            }
        }
    }

    /**
     * Runs {@code DungeonFeature.generate} for every attempt in the chunk against pre-feature terrain,
     * including its solid floor/ceiling check and the one-to-five wall openings rule. Rooms are
     * written to an overlay, so later attempts in the chunk see earlier rooms as vanilla does.
     * Returned predictions are {@link DungeonPrediction.Confidence#TERRAIN_SIMULATED}.
     */
    public static List<DungeonPrediction> simulateChunk(
            long worldSeed, int chunkX, int chunkZ, FeatureTerrain terrain, LootVersion lootVersion) {
        return simulateChunk(worldSeed, chunkX, chunkZ, terrain, lootVersion, DungeonFeatureConfig::attemptsPerChunk);
    }

    /**
     * As {@link #simulateChunk(long, int, int, FeatureTerrain, LootVersion)}, but runs only the first
     * {@code attemptLimit} attempts of each feature stream (0 skips the stream). Streams are
     * independent, and an attempt only depends on earlier attempts of its own stream.
     */
    public static List<DungeonPrediction> simulateChunk(
            long worldSeed,
            int chunkX,
            int chunkZ,
            FeatureTerrain terrain,
            LootVersion lootVersion,
            java.util.function.ToIntFunction<DungeonFeatureConfig> attemptLimit) {
        Objects.requireNonNull(terrain);
        Objects.requireNonNull(lootVersion);
        OverlayTerrain world = new OverlayTerrain(terrain);
        List<DungeonPrediction> predictions = new ArrayList<>();
        for (DungeonFeatureConfig feature : DungeonFeatureConfig.OVERWORLD) {
            MinecraftWorldgenRandom random = MinecraftWorldgenRandom.forPlacedFeature(
                    worldSeed, chunkX, chunkZ, feature.featureIndex(), feature.generationStep());
            int heightRange = feature.maxYInclusive() - feature.minY() + 1;
            int attempts = Math.min(feature.attemptsPerChunk(), attemptLimit.applyAsInt(feature));
            for (int attempt = 0; attempt < attempts; attempt++) {
                int x = chunkX * 16 + random.nextInt(16);
                int z = chunkZ * 16 + random.nextInt(16);
                int y = random.nextInt(heightRange) + feature.minY();
                Room room = new Room(feature, chunkX, chunkZ, attempt, new BlockPos(x, y, z), 0, 0);
                DungeonPrediction generated = room.generateOnTerrain(random, world, lootVersion);
                if (generated != null) {
                    predictions.add(generated);
                }
            }
        }
        return List.copyOf(predictions);
    }

    /** Pre-feature terrain plus the blocks dungeons in this chunk have already written. */
    private static final class OverlayTerrain {
        private final FeatureTerrain base;
        private final java.util.Map<BlockPos, BlockState> written = new java.util.HashMap<>();

        private OverlayTerrain(FeatureTerrain base) {
            this.base = base;
        }

        private BlockState get(BlockPos pos) {
            BlockState state = written.get(pos);
            return state != null ? state : base.getBlockState(pos);
        }

        private boolean mayBeSolid(BlockPos pos) {
            BlockState state = written.get(pos);
            return state != null ? state.isSolid() : base.mayBeSolid(pos);
        }

        private void set(BlockPos pos, BlockState state) {
            written.put(pos.toImmutable(), state);
        }

        /** {@code Feature.setBlockStateIf} with the {@code FEATURES_CANNOT_REPLACE} predicate. */
        private void setIfReplaceable(BlockPos pos, BlockState state) {
            if (!base.cannotReplace(get(pos))) {
                set(pos, state);
            }
        }
    }

    private record Room(
            DungeonFeatureConfig feature,
            int chunkX,
            int chunkZ,
            int attempt,
            BlockPos origin,
            int radiusX,
            int radiusZ) {
        /**
         * A line-by-line replay of {@code DungeonFeature.generate} (1.21.11) on {@code world}, drawing
         * from the chunk's placed-feature stream. Returns {@code null} when the terrain check fails;
         * the two size draws are consumed either way.
         */
        @Nullable
        private DungeonPrediction generateOnTerrain(
                MinecraftWorldgenRandom random, OverlayTerrain world, LootVersion lootVersion) {
            int j = random.nextInt(2) + 2;
            int k = -j - 1;
            int l = j + 1;
            int o = random.nextInt(2) + 2;
            int p = -o - 1;
            int q = o + 1;
            // The check below needs a solid floor and ceiling everywhere, including under and over
            // the origin; test those two first with the cheap noise-only bound. No draws happen in
            // the check, so its order does not matter.
            if (!world.base.mayHaveTerrainAt(origin.down())
                    || !world.mayBeSolid(origin.down())
                    || !world.mayBeSolid(origin.up(4))) {
                return null;
            }
            // The rest reads the whole room, walls included, plus the block under its floor.
            world.base.prefetch(
                    origin.getX() + k,
                    origin.getY() - 2,
                    origin.getZ() + p,
                    origin.getX() + l,
                    origin.getY() + 4,
                    origin.getZ() + q);
            int openings = 0;
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (int s = k; s <= l; s++) {
                for (int t = -1; t <= 4; t++) {
                    for (int u = p; u <= q; u++) {
                        pos.set(origin, s, t, u);
                        boolean solid = world.get(pos).isSolid();
                        if ((t == -1 || t == 4) && !solid) {
                            return null;
                        }
                        if ((s == k || s == l || u == p || u == q)
                                && t == 0
                                && world.get(pos).isAir()
                                && world.get(pos.up()).isAir()) {
                            openings++;
                        }
                    }
                }
            }
            if (openings < 1 || openings > 5) {
                return null;
            }

            for (int s = k; s <= l; s++) {
                for (int t = 3; t >= -1; t--) {
                    for (int u = p; u <= q; u++) {
                        BlockPos block = origin.add(s, t, u);
                        BlockState state = world.get(block);
                        if (s != k && t != -1 && u != p && s != l && u != q) {
                            if (!state.isOf(Blocks.CHEST) && !state.isOf(Blocks.SPAWNER)) {
                                world.setIfReplaceable(block, CAVE_AIR);
                            }
                        } else if (block.getY() >= DungeonFeatureConfig.OVERWORLD_BOTTOM_Y
                                && !world.get(block.down()).isSolid()) {
                            world.set(block, CAVE_AIR);
                        } else if (state.isSolid() && !state.isOf(Blocks.CHEST)) {
                            if (t == -1 && random.nextInt(4) != 0) {
                                world.setIfReplaceable(block, Blocks.MOSSY_COBBLESTONE.getDefaultState());
                            } else {
                                world.setIfReplaceable(block, Blocks.COBBLESTONE.getDefaultState());
                            }
                        }
                    }
                }
            }

            List<DungeonChest> chests = new ArrayList<>(CHEST_COUNT);
            for (int chest = 0; chest < CHEST_COUNT; chest++) {
                for (int tries = 0; tries < CHEST_ATTEMPTS; tries++) {
                    int x = origin.getX() + random.nextInt(j * 2 + 1) - j;
                    int z = origin.getZ() + random.nextInt(o * 2 + 1) - o;
                    BlockPos chestPos = new BlockPos(x, origin.getY(), z);
                    if (!world.get(chestPos).isAir()) {
                        continue;
                    }
                    int walls = 0;
                    for (BlockPos neighbor :
                            List.of(chestPos.north(), chestPos.east(), chestPos.south(), chestPos.west())) {
                        if (world.get(neighbor).isSolid()) {
                            walls++;
                        }
                    }
                    if (walls == 1) {
                        world.setIfReplaceable(chestPos, Blocks.CHEST.getDefaultState());
                        // LootableInventory.setLootTable draws only when a chest block entity exists.
                        if (world.get(chestPos).isOf(Blocks.CHEST)) {
                            chests.add(new DungeonChest(chestPos, random.nextLootSeed(), lootVersion));
                        }
                        break;
                    }
                }
            }

            world.setIfReplaceable(origin, Blocks.SPAWNER.getDefaultState());
            if (world.get(origin).isOf(Blocks.SPAWNER)) {
                random.nextInt(SPAWNER_ENTITY_COUNT);
            }
            return new DungeonPrediction(
                    feature,
                    chunkX,
                    chunkZ,
                    attempt,
                    origin,
                    j,
                    o,
                    chests,
                    DungeonPrediction.Confidence.TERRAIN_SIMULATED);
        }

        /**
         * Runs the post-check part of {@code DungeonFeature.generate}. Returns {@code null} when the
         * observed terrain shows no room for this attempt.
         */
        @Nullable
        private DungeonPrediction generate(
                MinecraftWorldgenRandom random, DungeonTerrain terrain, LootVersion lootVersion, boolean seedOnly) {
            int wallX = radiusX + 1;
            int wallZ = radiusZ + 1;
            int floorBlocks = (2 * wallX + 1) * (2 * wallZ + 1);
            int compared = 0;
            int matched = 0;
            // The wall pass iterates x outer, y from 3 down to -1, z inner. Only floor blocks (y == -1)
            // draw, so the draw order is x outer, z inner over the floor.
            for (int dx = -wallX; dx <= wallX; dx++) {
                for (int dz = -wallZ; dz <= wallZ; dz++) {
                    DungeonTerrain.FloorState floor = terrain.floor(origin.add(dx, -1, dz));
                    if (!floor.consumesDraw()) {
                        continue;
                    }
                    boolean mossy = random.nextInt(4) != 0;
                    if (floor == DungeonTerrain.FloorState.MOSSY || floor == DungeonTerrain.FloorState.COBBLESTONE) {
                        compared++;
                        if (mossy == (floor == DungeonTerrain.FloorState.MOSSY)) {
                            matched++;
                        }
                    }
                }
            }

            DungeonPrediction.Confidence confidence;
            boolean patternMatches = compared > 0 && matched == compared;
            if (seedOnly) {
                confidence = DungeonPrediction.Confidence.SEED_ONLY;
            } else if (terrain.hasSpawner(origin)) {
                confidence = patternMatches
                        ? DungeonPrediction.Confidence.VERIFIED
                        : DungeonPrediction.Confidence.SPAWNER_ONLY;
            } else if (patternMatches && compared * 2 >= floorBlocks) {
                confidence = DungeonPrediction.Confidence.FLOOR_PATTERN;
            } else {
                return null;
            }

            List<DungeonChest> chests = placeChests(random, terrain, lootVersion);
            // The spawner entity: Util.getRandom over four types. MobSpawnerLogic.getSpawnEntry draws
            // nothing more because a fresh spawner's SpawnPotentials pool is empty.
            random.nextInt(SPAWNER_ENTITY_COUNT);
            return new DungeonPrediction(
                    feature, chunkX, chunkZ, attempt, origin, radiusX, radiusZ, chests, confidence);
        }

        private List<DungeonChest> placeChests(
                MinecraftWorldgenRandom random, DungeonTerrain terrain, LootVersion lootVersion) {
            List<BlockPos> placed = new ArrayList<>(CHEST_COUNT);
            List<DungeonChest> chests = new ArrayList<>(CHEST_COUNT);
            for (int chest = 0; chest < CHEST_COUNT; chest++) {
                for (int tries = 0; tries < CHEST_ATTEMPTS; tries++) {
                    int x = origin.getX() + random.nextInt(radiusX * 2 + 1) - radiusX;
                    int z = origin.getZ() + random.nextInt(radiusZ * 2 + 1) - radiusZ;
                    BlockPos pos = new BlockPos(x, origin.getY(), z);
                    // The interior was cleared to cave air; only an earlier chest occupies it.
                    if (placed.contains(pos) || solidNeighbors(pos, placed, terrain) != 1) {
                        continue;
                    }
                    // LootableInventory.setLootTable stores random.nextLong() as LootTableSeed.
                    chests.add(new DungeonChest(pos, random.nextLootSeed(), lootVersion));
                    placed.add(pos);
                    break;
                }
            }
            return chests;
        }

        private int solidNeighbors(BlockPos pos, List<BlockPos> placedChests, DungeonTerrain terrain) {
            int solid = 0;
            for (BlockPos neighbor : List.of(pos.north(), pos.east(), pos.south(), pos.west())) {
                if (placedChests.contains(neighbor)) {
                    solid++;
                } else if (isWall(neighbor) && terrain.isWallSolid(neighbor)) {
                    solid++;
                }
                // Other neighbors are interior cave air; the spawner is placed after the chests.
            }
            return solid;
        }

        private boolean isWall(BlockPos pos) {
            return Math.abs(pos.getX() - origin.getX()) == radiusX + 1
                    || Math.abs(pos.getZ() - origin.getZ()) == radiusZ + 1;
        }
    }
}
