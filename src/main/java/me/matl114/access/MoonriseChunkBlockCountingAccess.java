package me.matl114.access;

import net.minecraft.world.chunk.ChunkSection;

public interface MoonriseChunkBlockCountingAccess {
    int getSpecialCollidingBlockCount();
    static MoonriseChunkBlockCountingAccess of(ChunkSection chunk){
        return (MoonriseChunkBlockCountingAccess) chunk;
    }
}
