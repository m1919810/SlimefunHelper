package me.matl114.utils.worldgen.structures;

/** A structure-set start chunk candidate; biome validity and generated pieces may still reject it. */
public record StructurePosition(StructureConfig config, long chunkX, long chunkZ) {
    public long blockX() {
        return chunkX * 16L;
    }

    public long blockZ() {
        return chunkZ * 16L;
    }
}
