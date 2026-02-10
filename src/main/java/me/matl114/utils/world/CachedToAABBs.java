package me.matl114.utils.world;

import net.minecraft.util.math.Box;

public record CachedToAABBs(java.util.List<Box> aabbs, boolean isOffset, double offX, double offY, double offZ) {

    public CachedToAABBs removeOffset() {
        final java.util.List<net.minecraft.util.math.Box> toOffset = this.aabbs;
        final double offX = this.offX;
        final double offY = this.offY;
        final double offZ = this.offZ;

        final java.util.List<net.minecraft.util.math.Box> ret = new java.util.ArrayList<>(toOffset.size());

        for (int i = 0, len = toOffset.size(); i < len; ++i) {
            ret.add(toOffset.get(i).offset(offX, offY, offZ));
        }

        return new CachedToAABBs(ret, false, 0.0, 0.0, 0.0);
    }

    public static CachedToAABBs offset(
            final CachedToAABBs cache, final double offX, final double offY, final double offZ) {
        if (offX == 0.0 && offY == 0.0 && offZ == 0.0) {
            return cache;
        }

        final double resX = cache.offX + offX;
        final double resY = cache.offY + offY;
        final double resZ = cache.offZ + offZ;

        return new CachedToAABBs(cache.aabbs, true, resX, resY, resZ);
    }
}
