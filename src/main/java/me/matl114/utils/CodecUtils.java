package me.matl114.utils;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

public class CodecUtils {
    public static <T, W> Codec<Pair<T, W>> pairCodec(
            Codec<T> firstCodec, String firstName, Codec<W> secondCodec, String secondName) {
        return RecordCodecBuilder.<Pair<T, W>>create(instance -> instance.<T, W>group(
                        firstCodec.fieldOf(firstName).forGetter(Pair::getFirst),
                        secondCodec.fieldOf(secondName).forGetter(Pair::getSecond))
                .apply(instance, Pair::of));
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
}
