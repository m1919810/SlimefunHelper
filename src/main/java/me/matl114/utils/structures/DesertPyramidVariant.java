package me.matl114.utils.structures;

import java.util.Arrays;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/** Cubiomes 1.21.x desert pyramid start orientation and invariant chest columns. */
public record DesertPyramidVariant(
        int rotation,
        boolean mirrored,
        int minBlockX,
        int minBlockZ,
        int sizeX,
        int sizeY,
        int sizeZ,
        List<ChestColumn> chests) {
    private static final List<ChestColumn> LOCAL_CHESTS =
            List.of(new ChestColumn(8, 10), new ChestColumn(12, 10), new ChestColumn(10, 8), new ChestColumn(10, 12));

    public DesertPyramidVariant {
        chests = List.copyOf(chests);
    }

    public static DesertPyramidVariant generate(long seed, StructurePosition structure) {
        JavaLegacyRandom random = StructureStartRandom.forCandidate(seed, structure);
        int orientation = random.nextInt(4);
        int rotation =
                switch (orientation) {
                    case 0, 2 -> 0;
                    case 1, 3 -> 1;
                    default -> throw new AssertionError("nextInt(4) returned outside its range");
                };
        boolean mirrored = orientation >= 2;
        int blockX = Math.toIntExact(structure.blockX());
        int blockZ = Math.toIntExact(structure.blockZ());
        List<ChestColumn> worldChests = LOCAL_CHESTS.stream()
                .map(pos -> new ChestColumn(blockX + pos.x(), blockZ + pos.z()))
                .toList();
        return new DesertPyramidVariant(rotation, mirrored, blockX, blockZ, 21, 15, 21, worldChests);
    }

    /** Vanilla moves the piece to {@code minHeightmapY - nextInt(3)}; chests use local Y=-11. */
    public static int chestY(int minHeightmapY, int nextIntThree) {
        if (nextIntThree < 0 || nextIntThree >= 3) {
            throw new IllegalArgumentException("nextIntThree must be in [0, 3)");
        }
        return minHeightmapY - nextIntThree - 11;
    }

    public List<BlockPos> chestPositions(int minHeightmapY, int nextIntThree) {
        int y = chestY(minHeightmapY, nextIntThree);
        return placementOrder().stream()
                .map(chest -> new BlockPos(chest.x(), y, chest.z()))
                .toList();
    }

    public List<BlockPos> chestPositions(CubiomesOverworldBiomeSampler terrain, int nextIntThree) {
        int minHeightmapY =
                terrain.getLowestHeight(minBlockX, minBlockZ, sizeX, sizeZ, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES);
        return chestPositions(minHeightmapY, nextIntThree);
    }

    private List<ChestColumn> placementOrder() {
        int orientation = mirrored ? (rotation == 0 ? 2 : 3) : (rotation == 0 ? 0 : 1);
        int[][] localPositions =
                switch (orientation) {
                    case 0 -> new int[][] {{10, 12}, {12, 10}, {10, 8}, {8, 10}};
                    case 1 -> new int[][] {{8, 10}, {10, 12}, {12, 10}, {10, 8}};
                    case 2 -> new int[][] {{10, 8}, {12, 10}, {10, 12}, {8, 10}};
                    case 3 -> new int[][] {{12, 10}, {10, 12}, {8, 10}, {10, 8}};
                    default -> throw new AssertionError("Orientation is outside [0, 4)");
                };
        return Arrays.stream(localPositions)
                .map(pos -> new ChestColumn(minBlockX + pos[0], minBlockZ + pos[1]))
                .toList();
    }

    public record ChestColumn(int x, int z) {}
}
