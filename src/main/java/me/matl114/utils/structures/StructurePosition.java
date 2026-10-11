package me.matl114.utils.structures;

public record StructurePosition(StructureConfig config, long chunkX, long chunkZ) {
    public long blockX() {
        return chunkX * 16L;
    }

    public long blockZ() {
        return chunkZ * 16L;
    }
}
