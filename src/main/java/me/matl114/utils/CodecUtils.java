package me.matl114.utils;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.function.Function;

public class CodecUtils {
    public static <T, W> Codec<Pair<T, W>> pairCodec(
            Codec<T> firstCodec, String firstName, Codec<W> secondCodec, String secondName) {
        return RecordCodecBuilder.<Pair<T, W>>create(instance -> instance.<T, W>group(
                        firstCodec.fieldOf(firstName).forGetter(Pair::getFirst),
                        secondCodec.fieldOf(secondName).forGetter(Pair::getSecond))
                .apply(instance, Pair::of));
    }

    public static <T, W> Codec<List<Pair<T, W>>> pairListCodec(Codec<T> keyCodec, Codec<W> valueCodec) {
        return Codec.list(pairCodec(keyCodec, "key", valueCodec, "value"));
    }

    public static <T, W> Codec<Map<T, W>> arrayMapCodec(Codec<T> keyCodec, Codec<W> valueCodec) {
        return Codec.list(pairCodec(keyCodec, "key", valueCodec, "value"))
                .xmap(
                        lst -> {
                            Map<T, W> tw = new LinkedHashMap<>();
                            lst.forEach(p -> tw.put(p.getFirst(), p.getSecond()));
                            return tw;
                        },
                        mp -> mp.entrySet().stream()
                                .map(v -> Pair.of(v.getKey(), v.getValue()))
                                .toList());
    }

    public static <T extends Enum<T>> Codec<T> enumCodec(Class<T> clazz) {
        Map<String, T> map = new HashMap<>();
        for (var re : clazz.getEnumConstants()) {
            map.put(re.name().toLowerCase(Locale.ROOT), re);
        }
        return finiteMapCodec(map, Enum::name);
    }

    public static <T> Codec<T> finiteMapCodec(Map<String, T> map, Function<T, String> stringFunction) {
        Map<String, T> map2 = new LinkedHashMap<>(map.size());
        for (var entry : map.entrySet()) {
            map2.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return Codec.STRING.comapFlatMap(
                str -> {
                    String s = str.toLowerCase(Locale.ROOT);
                    if (map2.containsKey(s)) {
                        return DataResult.success(map2.get(s));
                    } else {
                        return DataResult.error(() -> "Not in enum directory");
                    }
                },
                stringFunction);
    }
}
