package me.matl114.accessors.moonrise;

import net.minecraft.world.chunk.ChunkSection;

public interface MoonriseChunkBlockCountingAccess {
    int getSpecialCollidingBlockCount();
    static MoonriseChunkBlockCountingAccess of(ChunkSection chunk){
        return (MoonriseChunkBlockCountingAccess) chunk;
    }
}
