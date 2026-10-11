package me.matl114.utils.structures;

public record ChunkRange(long minChunkX, long minChunkZ, long maxChunkX, long maxChunkZ) {
    public ChunkRange {
        if (minChunkX > maxChunkX || minChunkZ > maxChunkZ) {
            throw new IllegalArgumentException("Minimum chunk coordinates must not exceed maximum coordinates");
        }
    }

    public static ChunkRange centered(long centerChunkX, long centerChunkZ, long radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Radius must not be negative");
        }
        return new ChunkRange(
                centerChunkX - radius, centerChunkZ - radius, centerChunkX + radius, centerChunkZ + radius);
    }

    public boolean contains(long chunkX, long chunkZ) {
        return chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
    }
}
