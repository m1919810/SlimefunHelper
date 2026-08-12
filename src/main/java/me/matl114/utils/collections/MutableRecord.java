package me.matl114.utils.collections;

import com.google.common.base.Preconditions;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.*;

public class MutableRecord {
    public final List<String> fields;

    public final Map<String, Object> values;

    public MutableRecord(List<String> fields, Map<String, Object> values) {
        this.fields = fields;
        this.values = new HashMap<>(values);
    }

    public List<Pair<String, Object>> getComponents() {
        return this.fields.stream().map(s -> Pair.of(s, this.values.get(s))).toList();
    }

    public <T> T get(String key) {
        return (T) this.values.get(key);
    }

    public <T> T get(String key, T defaultValue) {
        return (T) this.values.getOrDefault(key, defaultValue);
    }

    public <T> T getOrPut(String key, T defaultValue) {
        T value = this.get(key);
        if (value != null) {
            return value;
        } else {
            set(key, defaultValue);
            return defaultValue;
        }
    }

    public <T> void set(String key, T value) {
        this.values.put(key, value);
    }

    public void replaceMap(MutableRecord record) {
        this.values.clear();
        this.values.putAll(record.values);
    }

    public static <T extends Record> MutableRecord of(T value) {
        Map<String, Object> argsMap = new HashMap<>();
        Class<?> clazz = value.getClass();
        RecordComponent[] components = clazz.getRecordComponents();
        List<String> string = new ArrayList<>(components.length);
        for (RecordComponent component : components) {
            String name = component.getName();
            string.add(name);
            Method accessor;
            try {
                accessor = clazz.getMethod(name);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Record component accessor not found: " + name, e);
            }
            try {
                Object val = accessor.invoke(value);
                argsMap.put(name, val);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to get value for " + name, e);
            }
        }
        return new MutableRecord(string, argsMap);
    }

    public static <T extends Record> MutableRecord of(List<String> keys, T value) {
        Map<String, Object> argsMap = new HashMap<>();
        Class<?> clazz = value.getClass();
        RecordComponent[] components = clazz.getRecordComponents();
        Preconditions.checkArgument(components.length == keys.size());
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            String name = keys.get(i);
            Method method = component.getAccessor();
            try {
                Object val = method.invoke(value);
                argsMap.put(name, val);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to get value for " + name, e);
            }
        }

        return new MutableRecord(keys, argsMap);
    }

    public <T extends Record> T toRecord(Class<T> clazz) {
        RecordComponent[] components = clazz.getRecordComponents();
        Class<?>[] parameterTypes =
                Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new);
        Constructor<T> constructor;
        try {
            constructor = clazz.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Record constructor not found", e);
        }
        Object[] argsArray = this.fields.stream().map(this.values::get).toArray();
        try {
            return constructor.newInstance(argsArray);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to instantiate record", e);
        }
    }

    /**
     * 使用 Codec 将任意对象编码为 Java Map，并存入 ArgsMap。
     * 编码结果必须是 Map<String, Object>。
     */
    public static <T> MutableRecord of(List<String> keys, T value, Codec<T> codec) {
        DataResult<Object> result = codec.encodeStart(JavaOps.INSTANCE, value);
        Object obj = result.getOrThrow();
        if (!(obj instanceof Map)) {
            throw new IllegalArgumentException("Must be a record like: " + obj);
        }
        Map<String, Object> map = (Map<String, Object>) obj;
        return new MutableRecord(keys, map);
    }

    /**
     * 使用 Codec 将当前 ArgsMap 的内容解码为原始对象。
     * 要求 this.args 与 Codec 期望的 Map 结构一致。
     */
    public <T> T toRecord(Codec<T> codec) {
        DataResult<T> result = codec.parse(JavaOps.INSTANCE, this.values);
        return result.getOrThrow();
    }
}
