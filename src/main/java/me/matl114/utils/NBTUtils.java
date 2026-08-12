package me.matl114.utils;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;

public class NBTUtils {
    public static NbtElement getOrDefault(@Nonnull NbtCompound element, String key, NbtElement defaultValue) {
        return element.entries.getOrDefault(key, defaultValue);
    }

    public static void putIfAbsent(@Nonnull NbtCompound element, String key, NbtElement value) {
        element.entries.putIfAbsent(key, value);
    }

    public static NbtElement getOrCreate(@Nonnull NbtCompound element, String key, Supplier<NbtElement> defaultValue) {
        return element.entries.computeIfAbsent(key, (k) -> defaultValue.get());
    }

    public static NbtElement computeIfAbsent(
            @Nonnull NbtCompound element, String key, Function<String, NbtElement> defaultValue) {
        return element.entries.computeIfAbsent(key, defaultValue);
    }

    public static NbtCompound ensurePath(@Nonnull NbtCompound tag, String path) {
        String[] value = path.split("\\.");
        NbtCompound current = tag;
        for (int i = 0; i < value.length; i++) {
            current = (NbtCompound) current.entries.compute(value[i], (k, v) -> {
                if (v instanceof NbtCompound nbtCompound) {
                    return nbtCompound;
                } else {
                    return new NbtCompound();
                }
            });
        }
        return current;
    }

    @Nullable
    public static NbtElement resolvePath(@Nonnull NbtCompound tag, String path) {
        String[] value = path.split("\\.");
        return resolve(tag, value);
    }

    public static NbtElement resolve(@Nonnull NbtCompound tag, String... value) {
        NbtCompound current = tag;
        for (int i = 0; i < value.length - 1; i++) {
            if (current.get(value[i]) instanceof NbtCompound nbtCompound) {
                current = nbtCompound;
            } else {
                return null;
            }
        }
        return current.get(value[value.length - 1]);
    }

    public static <W> void putValue(
            @Nonnull NbtCompound tag, String key, W value, Codec<W> codec, RegistryWrapper.WrapperLookup lookup) {
        tag.put(key, codec, lookup.getOps(NbtOps.INSTANCE), value);
    }

    public static <W> void putValue(@Nonnull NbtCompound tag, String key, W value, Codec<W> codec) {
        tag.put(key, codec, NbtOps.INSTANCE, value);
    }

    public static <W> W getValue(@Nonnull NbtCompound tag, String key, Codec<W> codec) {
        return tag.get(key, codec).orElse(null);
    }

    public static <W> W getValue(
            @Nonnull NbtCompound tag, String key, Codec<W> codec, RegistryWrapper.WrapperLookup lookup) {
        return tag.get(key, codec, lookup.getOps(NbtOps.INSTANCE)).orElse(null);
    }

    public static <W> W toValue(NbtElement nbtElement, Codec<W> codec) {
        var re = codec.parse(NbtOps.INSTANCE, nbtElement);
        return re.isSuccess() ? re.getOrThrow() : null;
    }

    public static <W> W toValue(NbtElement nbtElement, Codec<W> codec, RegistryWrapper.WrapperLookup lookup) {
        var re = codec.parse(lookup.getOps(NbtOps.INSTANCE), nbtElement);
        return re.isSuccess() ? re.getOrThrow() : null;
    }
}
