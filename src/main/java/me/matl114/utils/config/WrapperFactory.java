package me.matl114.utils.config;

import com.mojang.datafixers.kinds.K2;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.mojang.serialization.DynamicOps;
import lombok.AllArgsConstructor;
import me.matl114.utils.RuntimeAbort;

public interface WrapperFactory<T, W> {
    public static RuntimeException PARSE_FAILURE = new RuntimeAbort();
    public static WrapperFactory IDENTITY = WrapperFactory.of(Function.identity(), Function.identity());

    public static <T> WrapperFactory<T, T> identity() {
        return IDENTITY;
    }
    // may throw exception
    public W create(T va);

    public T get(W va);

    public static <T, W> WrapperFactory<T, W> of(Function<T, W> function, Function<W, T> function2) {
        return new BaseWrapperFactory<>(function, function2);
    }

    default WrapperFactory<W, T> inverse() {
        return of(this::get, this::create);
    }

    default <R> WrapperFactory<T, R> concat(WrapperFactory<W, R> other) {
        return of(s -> other.create(this.create(s)), t -> this.get(other.get(t)));
    }

    default Codec<W> wrapCodec(Codec<T> codec) {
        return codec.comapFlatMap(
                s -> {
                    try {
                        return DataResult.success(this.create(s));
                    } catch (Throwable e) {
                        return DataResult.error(() -> "Error create");
                    }
                },
                this::get);
    }

    default Codec<W> wrapCodecSafe(Codec<T> codec) {
        return codec.xmap(this::create, this::get);
    }

    @AllArgsConstructor
    public static class BaseWrapperFactory<T, W> implements WrapperFactory<T, W> {
        Function<T, W> function;
        Function<W, T> function2;

        @Override
        public W create(T va) {
            return function.apply(va);
        }

        @Override
        public T get(W va) {
            return function2.apply(va);
        }

        @Override
        public WrapperFactory<W, T> inverse() {
            return new BaseWrapperFactory<W, T>(function2, function);
        }

        public <R> WrapperFactory<T, R> concat(WrapperFactory<W, R> other) {
            if (other instanceof BaseWrapperFactory<W, R> base) {
                return new BaseWrapperFactory<>(function.andThen(base.function), base.function2.andThen(function2));
            } else {
                return new BaseWrapperFactory<>(
                        function.andThen(other::create), ((Function<R, W>) other::get).andThen(function2));
            }
        }
    }

    @AllArgsConstructor
    public static class ListWrapperFactory<T, W> implements WrapperFactory<List<T>, List<W>> {
        WrapperFactory<T, W> wrapped;

        @Override
        public List<W> create(List<T> va) {
            return va.stream().map(wrapped::create).toList();
        }

        @Override
        public List<T> get(List<W> va) {
            return va.stream().map(wrapped::get).toList();
        }
    }

    public static <T, W> WrapperFactory<List<T>, List<W>> list(WrapperFactory<T, W> factory) {
        return new ListWrapperFactory<>(factory);
    }

    public static <S, T, U, V> WrapperFactory<Map<U, V>, Map<S, T>> map(WrapperFactory<U, S> keyMapper, WrapperFactory<V, T> valueMapper) {
        return WrapperFactory.of(
            (map1)->{
                Map<S, T> map2 = new LinkedHashMap<>();
                for (var re : map1.entrySet()){
                    map2.put(keyMapper.create(re.getKey()), valueMapper.create(re.getValue()));
                }
                return map2;
            },
            (map2)->{
                Map<U, V> map1 = new LinkedHashMap<>();
                for (var re : map2.entrySet()){
                    map1.put(keyMapper.get(re.getKey()), valueMapper.get(re.getValue()));
                }
                return map1;
            }
        );
    }

    public static <R, S extends R, T> WrapperFactory<S, T> fromCodec(Codec<T> codec, DynamicOps<R> ops){
        return WrapperFactory.of(
            s -> codec.decode(ops, s).getOrThrow().getFirst(), t -> (S)codec.<R>encodeStart(ops, t).getOrThrow()
        );
    }

    public static final WrapperFactory<List<Pair>, Map<?, ?>> LIST_MAP_WRAPPER_FACTORY = WrapperFactory.of(
            lst -> {
                var map = new LinkedHashMap<>(lst.size());
                lst.forEach(pair -> map.put(pair.getFirst(), pair.getSecond()));
                return map;
            },
            map -> {
                return map.entrySet().stream()
                        .map(s -> Pair.of(s.getKey(), s.getValue()))
                        .collect(Collectors.toCollection(ArrayList::new));
            });

    public static <K1, K2> WrapperFactory<List<Pair<K1, K2>>, Map<K1, K2>> getListMapWrapper() {
        return (WrapperFactory) LIST_MAP_WRAPPER_FACTORY;
    }
}
