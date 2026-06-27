package me.matl114.hacks.utils.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import me.matl114.versioned.api.VNbt;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

@Getter
public class ChunkStorage extends IStorage {
    public static final Codec<ChunkStorage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    World.CODEC.fieldOf("dim").forGetter(ChunkStorage::getDimension),
                    ChunkPos.CODEC.fieldOf("chunk-pos").forGetter(ChunkStorage::getChunkPos),
                    Codec.unboundedMap(Codec.STRING, VNbt.CODEC)
                            .fieldOf("storage")
                            .forGetter(v -> v.storage))
            .apply(instance, ChunkStorage::new));
    public final ChunkPos chunkPos;

    public ChunkStorage(final ChunkPos chunkPos) {
        super();
        this.chunkPos = chunkPos;
    }

    public ChunkStorage(RegistryKey<World> dimension, ChunkPos chunkPos) {
        this(dimension, chunkPos, new ConcurrentHashMap<>());
    }

    public ChunkStorage(RegistryKey<World> dimension, ChunkPos chunkPos, Map<String, NbtElement> storage) {
        super(dimension, storage);
        this.chunkPos = chunkPos;
    }
}
