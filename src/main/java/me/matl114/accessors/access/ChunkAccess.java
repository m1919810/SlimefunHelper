package me.matl114.accessors.access;

import java.util.Map;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

public interface ChunkAccess {
    public Iterable<Map.Entry<BlockPos, BlockEntity>> blockEntities();

    public static ChunkAccess of(Chunk chunk) {
        return (ChunkAccess) chunk;
    }
}
