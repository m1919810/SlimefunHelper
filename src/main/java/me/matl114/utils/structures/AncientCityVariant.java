package me.matl114.utils.structures;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Cubiomes 1.21.x start-piece variant for the Ancient City structure. */
public record AncientCityVariant(
        Identifier template,
        int rotation,
        int minBlockX,
        int minY,
        int minBlockZ,
        int sizeX,
        int sizeY,
        int sizeZ,
        BlockPos biomeSample) {
    public static AncientCityVariant generate(long seed, StructurePosition structure) {
        JavaLegacyRandom random = StructureStartRandom.forCandidate(seed, structure);
        int rotation = random.nextInt(4);
        int start = 1 + random.nextInt(3);

        int chunkX = Math.toIntExact(structure.chunkX());
        int chunkZ = Math.toIntExact(structure.chunkZ());
        long blockX = structure.blockX();
        long blockZ = structure.blockZ();
        long baseX;
        long baseZ;
        int sizeX;
        int sizeZ;
        switch (rotation) {
            case 0 -> {
                baseX = blockX > 0 ? -1 : 0;
                baseZ = blockZ > 0 ? -1 : 0;
                sizeX = 18;
                sizeZ = 41;
            }
            case 1 -> {
                baseX = (blockX < 0 ? 1 : 0) - 41L;
                baseZ = blockZ > 0 ? -1 : 0;
                sizeX = 41;
                sizeZ = 18;
            }
            case 2 -> {
                baseX = (blockX < 0 ? 1 : 0) - 18L;
                baseZ = (blockZ < 0 ? 1 : 0) - 41L;
                sizeX = 18;
                sizeZ = 41;
            }
            case 3 -> {
                baseX = blockX > 0 ? -1 : 0;
                baseZ = (blockZ < 0 ? 1 : 0) - 18L;
                sizeX = 41;
                sizeZ = 18;
            }
            default -> throw new AssertionError("nextInt(4) returned outside its range");
        }

        long minX;
        long minZ;
        switch (rotation) {
            case 0 -> {
                minX = baseX - 13L;
                minZ = baseZ - 20L;
            }
            case 1 -> {
                minX = baseX + 20L;
                minZ = baseZ - 13L;
            }
            case 2 -> {
                minX = baseX + 13L;
                minZ = baseZ + 20L;
            }
            case 3 -> {
                minX = baseX - 20L;
                minZ = baseZ + 13L;
            }
            default -> throw new AssertionError("nextInt(4) returned outside its range");
        }

        long quartX = ((long) chunkX * 32L + 2L * minX + sizeX - 1L) / 2L >> 2;
        long quartZ = ((long) chunkZ * 32L + 2L * minZ + sizeZ - 1L) / 2L >> 2;
        BlockPos biomeSample =
                new BlockPos(Math.toIntExact(quartX << 2), (-27 >> 2) << 2, Math.toIntExact(quartZ << 2));
        return new AncientCityVariant(
                Identifier.ofVanilla("ancient_city/city_center_" + start),
                rotation,
                Math.toIntExact(blockX + minX),
                -27,
                Math.toIntExact(blockZ + minZ),
                sizeX,
                31,
                sizeZ,
                biomeSample);
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= minBlockX
                && (long) pos.getX() < (long) minBlockX + sizeX
                && pos.getY() >= minY
                && (long) pos.getY() < (long) minY + sizeY
                && pos.getZ() >= minBlockZ
                && (long) pos.getZ() < (long) minBlockZ + sizeZ;
    }
}
