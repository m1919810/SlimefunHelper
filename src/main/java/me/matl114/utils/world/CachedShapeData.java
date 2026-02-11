package me.matl114.utils.world;

public record CachedShapeData(
        int sizeX,
        int sizeY,
        int sizeZ,
        long[] voxelSet,
        int minFullX,
        int minFullY,
        int minFullZ,
        int maxFullX,
        int maxFullY,
        int maxFullZ,
        boolean isEmpty,
        boolean hasSingleAABB) {}
