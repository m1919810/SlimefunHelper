package me.matl114.utils.world;

import java.util.Iterator;
import java.util.NoSuchElementException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.world.chunk.Chunk;

public class ChunkIterator implements Iterator<Chunk> {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private final ClientChunkManager.ClientChunkMap map = (mc.world.getChunkManager()).chunks;
    private final boolean onlyWithLoadedNeighbours;

    private Chunk chunk;
    private final int minX, maxX, maxZ;
    private int currentX, currentZ;

    public ChunkIterator(boolean onlyWithLoadedNeighbours) {
        this.onlyWithLoadedNeighbours = onlyWithLoadedNeighbours;
        int realRange = Math.min(map.radius, Math.max(2, mc.options.getClampedViewDistance()) + 3);
        int centerX = map.centerChunkX;
        int centerZ = map.centerChunkZ;
        minX = centerX - realRange;
        maxX = centerX + realRange;
        int minZ = centerZ - realRange;
        maxZ = centerZ + realRange;
        currentX = minX;
        currentZ = minZ;
        getNext();
    }

    private Chunk getNext() {
        Chunk prev = chunk;
        chunk = null;
        search:
        while (currentZ <= maxZ) {
            while (currentX <= maxX) {
                int idx = map.getIndex(currentX++, currentZ);
                chunk = map.chunks.get(idx);
                if (chunk != null && (!onlyWithLoadedNeighbours || isInRadius(chunk))) break search;
            }
            currentZ++;
            currentX = minX;
        }

        return prev;
    }

    private boolean isInRadius(Chunk chunk) {
        int x = chunk.getPos().x;
        int z = chunk.getPos().z;

        return mc.world.getChunkManager().isChunkLoaded(x + 1, z)
                && mc.world.getChunkManager().isChunkLoaded(x - 1, z)
                && mc.world.getChunkManager().isChunkLoaded(x, z + 1)
                && mc.world.getChunkManager().isChunkLoaded(x, z - 1);
    }

    @Override
    public boolean hasNext() {
        return chunk != null;
    }

    @Override
    public Chunk next() {
        if (chunk == null) {
            throw new NoSuchElementException();
        }
        return getNext();
    }
}
