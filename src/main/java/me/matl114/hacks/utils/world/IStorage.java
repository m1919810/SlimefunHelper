package me.matl114.hacks.utils.world;

import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

public abstract class IStorage {
    @Getter
    public final RegistryKey<World> dimension;

    public final Map<String, NbtElement> storage;

    @Getter
    @Setter
    public boolean dirty = false;

    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    public IStorage() {
        this(mc.world.getRegistryKey(), null);
    }

    public IStorage(RegistryKey<World> dimension, Map<String, NbtElement> storage) {
        this.dimension = dimension;
        this.storage = storage == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(storage);
    }

    public NbtElement get(String key) {
        return storage.get(key);
    }

    public <T> T get(String key, Codec<T> codec) {
        var re = get(key);
        return re == null ? null : resultOrNull(re, codec);
    }

    public static <T> T resultOrNull(NbtElement re, Codec<T> codec) {
        var tmp = codec.decode(NbtOps.INSTANCE, re);
        return tmp.isSuccess() ? tmp.getOrThrow().getFirst() : null;
    }

    public void put(String key, NbtElement value) {
        if (value == null) {
            this.storage.remove(key);
        } else {
            this.storage.put(key, value);
        }
        dirty = true;
    }

    public <T> void put(String key, T val, Codec<T> codec) {
        if (val == null) {
            put(key, null);
        } else {
            put(key, codec.encodeStart(NbtOps.INSTANCE, val).getOrThrow());
        }
    }

    public boolean contains(String key) {
        return storage.containsKey(key);
    }

    public boolean isEmpty() {
        return storage.isEmpty();
    }

    public boolean nonEmpty() {
        return !storage.isEmpty();
    }
}
