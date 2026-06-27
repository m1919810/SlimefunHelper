package me.matl114.hacks.utils.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.matl114.versioned.api.VNbt;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

public class WorldStorage extends IStorage {
    public static final Codec<WorldStorage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    World.CODEC.fieldOf("dim").forGetter(WorldStorage::getDimension),
                    Codec.unboundedMap(Codec.STRING, VNbt.CODEC)
                            .fieldOf("storage")
                            .forGetter(v -> v.storage))
            .apply(instance, WorldStorage::new));

    public WorldStorage() {
        super();
    }

    public WorldStorage(RegistryKey<World> dimension) {
        this(dimension, new ConcurrentHashMap<>());
    }

    public WorldStorage(RegistryKey<World> dimension, Map<String, NbtElement> storage) {
        super(dimension, storage);
    }
}
