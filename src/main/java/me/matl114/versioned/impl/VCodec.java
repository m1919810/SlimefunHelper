package me.matl114.versioned.impl;

import com.mojang.serialization.Codec;
import java.util.stream.IntStream;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;

public interface VCodec {
    static Codec<ChunkPos> CHUNK_POS_CODEC = Codec.INT_STREAM
            .comapFlatMap(
                    (stream) -> {
                        return Util.decodeFixedLengthArray(stream, 2).map((coords) -> {
                            return new ChunkPos(coords[0], coords[1]);
                        });
                    },
                    (chunkPos) -> {
                        return IntStream.of(new int[] {chunkPos.x, chunkPos.z});
                    })
            .stable();
}
