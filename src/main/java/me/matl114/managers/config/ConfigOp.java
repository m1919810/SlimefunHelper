package me.matl114.managers.config;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class ConfigOp implements DynamicOps<Ref<?>> {
    public static final ConfigOp INSTANCE = new ConfigOp();
    private static final Ref<?> EMPTY = new ObjectRef.JustOnlyObjectRef(null);

    private static Ref<?> wrapConfigValue(Object value) {
        Ref<?> wrapped = Refs.wrapInstance(value);
        return wrapped == null ? new ObjectRef.JustOnlyObjectRef(value) : wrapped;
    }

    private static DataResult<String> requireStringKey(Ref<?> input) {
        return INSTANCE.getStringValue(input).mapError(message -> "Map key must be string: " + message);
    }

    @Override
    public Ref<?> empty() {
        return EMPTY;
    }

    @Override
    public <U> U convertTo(DynamicOps<U> outOps, Ref<?> input) {
        return JavaOps.INSTANCE.convertTo(outOps, input.getAsPrimitive());
    }

    @Override
    public DataResult<Number> getNumberValue(Ref<?> input) {
        Object primitive = input.getAsPrimitive();
        if (primitive instanceof Number number) {
            return DataResult.success(number);
        }
        return DataResult.error(() -> "Not a numeric config value: " + primitive);
    }

    @Override
    public Ref<?> createNumeric(Number i) {
        if (i instanceof Byte || i instanceof Short || i instanceof Integer) {
            return wrapConfigValue(i.intValue());
        }
        if (i instanceof Long) {
            return wrapConfigValue(i.longValue());
        }
        if (i instanceof Float || i instanceof Double) {
            return wrapConfigValue(i.doubleValue());
        }

        double doubleValue = i.doubleValue();
        if (Double.isFinite(doubleValue) && Math.rint(doubleValue) == doubleValue) {
            long longValue = i.longValue();
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return wrapConfigValue((int) longValue);
            }
            return wrapConfigValue(longValue);
        }
        return wrapConfigValue(doubleValue);
    }

    @Override
    public DataResult<Boolean> getBooleanValue(Ref<?> input) {
        if (input instanceof FlagRef flagRef) {
            return DataResult.success(flagRef.get());
        }
        Object primitive = input.getAsPrimitive();
        if (primitive instanceof Boolean bool) {
            return DataResult.success(bool);
        }
        return DataResult.error(() -> "Not a boolean config value: " + primitive);
    }

    @Override
    public Ref<?> createBoolean(boolean value) {
        return new FlagRef(value);
    }

    @Override
    public DataResult<String> getStringValue(Ref<?> input) {
        Object primitive = input.getAsPrimitive();
        if (primitive instanceof String string) {
            return DataResult.success(string);
        }
        return DataResult.error(() -> "Not a string config value: " + primitive);
    }

    @Override
    public Ref<?> createString(String value) {
        return wrapConfigValue(value);
    }

    @Override
    public DataResult<Ref<?>> mergeToList(Ref<?> list, Ref<?> value) {
        if (list == empty()) {
            List<String> values = new ArrayList<>();
            values.add(Objects.toString(value.getAsPrimitive(), null));
            return DataResult.success(new ListRef(values));
        }
        if (list instanceof ListRef listRef) {
            List<String> values = new ArrayList<>(listRef.get());
            values.add(Objects.toString(value.getAsPrimitive(), null));
            return DataResult.success(new ListRef(values));
        }
        return DataResult.error(() -> "Not a list config value: " + list.getAsPrimitive());
    }

    @Override
    public DataResult<Ref<?>> mergeToMap(Ref<?> map, Ref<?> key, Ref<?> value) {
        DataResult<String> keyResult = requireStringKey(key);
        if (keyResult.isError()) {
            String message = keyResult.error().map(DataResult.Error::message).orElse("Unknown map key error");
            return DataResult.error(() -> message);
        }
        String resolvedKey = keyResult.result().get();
        MapRef mapRef;
        if (map == empty()) {
            mapRef = new MapRef();
        } else if (map instanceof MapRef existing) {
            mapRef = new MapRef();
            mapRef.setValueNoCopy(new LinkedHashMap<>(existing.getValue()));
        } else {
            return DataResult.error(() -> "Not a map config value: " + map.getAsPrimitive());
        }
        mapRef.putRaw(resolvedKey, value);
        return DataResult.success(mapRef);
    }

    @Override
    public DataResult<Stream<Pair<Ref<?>, Ref<?>>>> getMapValues(Ref<?> input) {
        if (input instanceof MapRef mapRef) {
            return DataResult.success(mapRef.getValue().entrySet().stream()
                    .map(entry -> Pair.of(new StringRef(entry.getKey()), entry.getValue())));
        }
        return DataResult.error(() -> "Not a map config value: " + input.getAsPrimitive());
    }

    @Override
    public Ref<?> createMap(Stream<Pair<Ref<?>, Ref<?>>> map) {
        Map<String, Object> values = new LinkedHashMap<>();
        map.forEach(entry -> {
            String key = requireStringKey(entry.getFirst())
                    .result()
                    .orElseGet(() -> Objects.toString(entry.getFirst().getAsPrimitive()));
            values.put(key, entry.getSecond().getAsPrimitive());
        });
        return wrapConfigValue(values);
    }

    @Override
    public DataResult<Stream<Ref<?>>> getStream(Ref<?> input) {
        if (input instanceof ListRef listRef) {
            return DataResult.success(listRef.get().stream().map(ConfigOp::wrapConfigValue));
        }
        return DataResult.error(() -> "Not a list config value: " + input.getAsPrimitive());
    }

    @Override
    public Ref<?> createList(Stream<Ref<?>> input) {
        return new ListRef(input.map(value -> Objects.toString(value.getAsPrimitive(), null))
                .toList());
    }

    @Override
    public Ref<?> remove(Ref<?> input, String key) {
        if (!(input instanceof MapRef mapRef)) {
            return input;
        }
        Map<String, Ref<?>> values = new LinkedHashMap<>(mapRef.getValue());
        values.remove(key);
        MapRef copy = new MapRef();
        copy.setValueNoCopy(values);
        return copy;
    }
}
