package me.matl114.utils.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

public record RegionPos(int x, int z) {
    public static RegionPos of(BlockPos pos) {
        return new RegionPos(pos.getX() >> 9 << 9, pos.getZ() >> 9 << 9);
    }

    public static RegionPos of(ChunkPos pos) {
        return new RegionPos(pos.x >> 5 << 9, pos.z >> 5 << 9);
    }

    public RegionPos negate() {
        return new RegionPos(-x, -z);
    }

    public Vec3d toVec3d() {
        return new Vec3d(x, 0, z);
    }

    public BlockPos toBlockPos() {
        return new BlockPos(x, 0, z);
    }
}
