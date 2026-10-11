package me.matl114.utils.structures;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.math.ChunkPos;

/** Reproduces the start-reference order used by vanilla structure decoration. */
final class StructureReferenceOrder {
    private static final int REFERENCE_RADIUS = 8;

    private StructureReferenceOrder() {}

    static <T> List<Start<T>> forChunk(ChunkPos chunk, Collection<Start<T>> starts) {
        Map<Long, Start<T>> startsByPosition = new HashMap<>();
        for (Start<T> start : starts) {
            startsByPosition.put(ChunkPos.toLong(start.chunkX(), start.chunkZ()), start);
        }

        LongOpenHashSet references = new LongOpenHashSet();
        int minBlockX = chunk.getStartX();
        int minBlockZ = chunk.getStartZ();
        int maxBlockX = minBlockX + 15;
        int maxBlockZ = minBlockZ + 15;
        for (int x = chunk.x - REFERENCE_RADIUS; x <= chunk.x + REFERENCE_RADIUS; x++) {
            for (int z = chunk.z - REFERENCE_RADIUS; z <= chunk.z + REFERENCE_RADIUS; z++) {
                long packed = ChunkPos.toLong(x, z);
                Start start = startsByPosition.get(packed);
                if (start != null && start.bounds().intersectsXZ(minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
                    references.add(packed);
                }
            }
        }

        List<Start<T>> ordered = new ArrayList<>(references.size());
        for (long packed : references) {
            Start<T> start = startsByPosition.get(packed);
            if (start != null) {
                ordered.add(start);
            }
        }
        return List.copyOf(ordered);
    }

    record Start<T>(int chunkX, int chunkZ, Bounds bounds, T value) {}

    record Bounds(int minX, int minZ, int maxX, int maxZ) {
        boolean intersectsXZ(int otherMinX, int otherMinZ, int otherMaxX, int otherMaxZ) {
            return maxX >= otherMinX && minX <= otherMaxX && maxZ >= otherMinZ && minZ <= otherMaxZ;
        }
    }
}
