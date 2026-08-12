package me.matl114.hacks.utils.config;

import com.google.common.base.Preconditions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

@Getter
@Accessors(fluent = true)
public class WeakEntryPrimitiveMap<T, W> extends PrimitiveMap<WeakHolder<T>, W> {
    public static final Identifier DEFAULT_KEY = new Identifier("minecraft", "default");

    public static <T, W> Class<WeakEntryPrimitiveMap<T, W>> parameter() {
        return (Class<WeakEntryPrimitiveMap<T, W>>) (Class) WeakEntryPrimitiveMap.class;
    }

    public WeakEntryPrimitiveMap(RegistryKey<? extends Registry<T>> registry, NBTType<W> type, Map<Identifier, W> map) {
        this(registry.getValue(), type, map, Optional.empty());
    }

    public WeakEntryPrimitiveMap(
            RegistryKey<? extends Registry<T>> registry, NBTType<W> type, Map<Identifier, W> map, W defaultValue) {
        this(registry.getValue(), type, map, Optional.ofNullable(defaultValue).map(value -> Primitive.of(type, value)));
    }

    public WeakEntryPrimitiveMap(
            Identifier registry, NBTType<W> type, Map<Identifier, W> map, Optional<Primitive<W>> defaultPrimitive) {
        this(registry, type, defaultPrimitive, valueMapToWeakHolderMap(registry, map));
    }

    private WeakEntryPrimitiveMap(
            Identifier registry, NBTType<W> type, Optional<Primitive<W>> defaultPrimitive, Map<WeakHolder<T>, W> map) {
        super(WeakHolder.TYPE.<WeakHolder<T>>cast(), type, map, createDefaultKeyPrimitive(registry), defaultPrimitive);
    }

    public WeakEntryPrimitiveMap(Map<WeakHolder<T>, Primitive<W>> map, Identifier registry, NBTType<W> type) {
        this(map, registry, type, Optional.empty());
    }

    public WeakEntryPrimitiveMap(
            Map<WeakHolder<T>, Primitive<W>> map,
            Identifier registry,
            NBTType<W> type,
            Optional<Primitive<W>> defaultPrimitive) {
        this(
                registry,
                type,
                weakMapToValueMap(map, registry, type),
                resolveDefaultPrimitive(map, registry, type, defaultPrimitive));
    }

    private static <T> Optional<Primitive<WeakHolder<T>>> createDefaultKeyPrimitive(Identifier registry) {
        return Optional.of(
                Primitive.of(WeakHolder.TYPE.<WeakHolder<T>>cast(), new WeakHolder<>(registry, DEFAULT_KEY)));
    }

    private static <T, W> Map<WeakHolder<T>, W> valueMapToWeakHolderMap(Identifier registry, Map<Identifier, W> map) {
        Map<WeakHolder<T>, W> result = new LinkedHashMap<>(map.size());
        for (var entry : map.entrySet()) {
            result.put(new WeakHolder<>(registry, entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static <T, W> Map<Identifier, W> weakMapToValueMap(
            Map<WeakHolder<T>, Primitive<W>> map, Identifier registry, NBTType<W> type) {
        Map<Identifier, W> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            WeakHolder<T> key = entry.getKey();
            Primitive<W> value = entry.getValue();
            Preconditions.checkArgument(Objects.equals(registry, key.registry()));
            Preconditions.checkArgument(value.valueType() == type);
            if (!Objects.equals(key.location(), DEFAULT_KEY)) {
                result.put(key.location(), value.value());
            }
        }
        return result;
    }

    private static <T, W> Optional<Primitive<W>> resolveDefaultPrimitive(
            Map<WeakHolder<T>, Primitive<W>> map,
            Identifier registry,
            NBTType<W> type,
            Optional<Primitive<W>> defaultPrimitive) {
        Primitive<W> legacyDefaultPrimitive = null;
        for (var entry : map.entrySet()) {
            WeakHolder<T> key = entry.getKey();
            Primitive<W> value = entry.getValue();
            Preconditions.checkArgument(Objects.equals(registry, key.registry()));
            Preconditions.checkArgument(value.valueType() == type);
            if (Objects.equals(key.location(), DEFAULT_KEY)) {
                legacyDefaultPrimitive = value;
            }
        }
        Primitive<W> finalLegacyDefaultPrimitive = legacyDefaultPrimitive;
        return defaultPrimitive
                .map(entry -> {
                    Preconditions.checkArgument(entry.valueType() == type);
                    return entry;
                })
                .or(() -> Optional.ofNullable(finalLegacyDefaultPrimitive));
    }

    private static <T, W> Identifier resolveRegistry(PrimitiveMap<WeakHolder<T>, W> map) {
        return map.defaultKeyPrimitive()
                .map(Primitive::value)
                .map(WeakHolder::registry)
                .or(() -> map.map().keySet().stream().findFirst().map(WeakHolder::registry))
                .orElseThrow(() -> new IllegalArgumentException("Can not resolve registry from PrimitiveMap"));
    }

    private static <T, W> Map<WeakHolder<T>, Primitive<W>> toLegacyMap(PrimitiveMap<WeakHolder<T>, W> map) {
        Map<WeakHolder<T>, Primitive<W>> result = new LinkedHashMap<>();
        for (var entry : map.map().entrySet()) {
            result.put(entry.getKey(), Primitive.of(map.valueType(), entry.getValue()));
        }
        return result;
    }

    private static <T, W> WeakEntryPrimitiveMap<T, W> fromPrimitiveMap(PrimitiveMap<WeakHolder<T>, W> map) {
        return new WeakEntryPrimitiveMap<>(
                toLegacyMap(map), resolveRegistry(map), map.valueType(), map.defaultValuePrimitive());
    }

    private static <T, W> WrapperFactory<PrimitiveMap<WeakHolder<T>, W>, WeakEntryPrimitiveMap<T, W>> wrapperFactory() {
        return WrapperFactory.of(WeakEntryPrimitiveMap::fromPrimitiveMap, map -> map);
    }

    public Identifier registry() {
        return defaultKeyPrimitive
                .map(Primitive::value)
                .map(WeakHolder::registry)
                .orElse(null);
    }

    public Map<RegistryKey<T>, W> keyMap() {
        RegistryKey<? extends Registry<T>> keyTypeKey = RegistryKey.ofRegistry(registry());
        return map().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> RegistryKey.of(keyTypeKey, entry.getKey().location()), Map.Entry::getValue));
    }

    public Map<Identifier, W> idMap() {
        return map().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().location(), entry -> entry.getValue()));
    }

    @Nullable
    public W getOrDefault(Identifier value) {
        return super.getOrDefault(new WeakHolder<>(registry(), value));
    }

    @Nullable
    public W getOrDefault(RegistryKey<T> value) {
        return getOrDefault(value.getValue());
    }

    @Nullable
    public W getOrDefault(RegistryEntry<T> value) {
        return value.getKey().map(this::getOrDefault).orElse(null);
    }

    public W getOrWithDefault(Identifier value, W fallback) {
        W result = getOrDefault(value);
        return result == null ? fallback : result;
    }

    public W getOrWithDefault(RegistryKey<T> value, W fallback) {
        return getOrWithDefault(value.getValue(), fallback);
    }

    public static <T, W> NBTType<WeakEntryPrimitiveMap<T, W>> createEntry() {
        WrapperFactory<PrimitiveMap<WeakHolder<T>, W>, WeakEntryPrimitiveMap<T, W>> factory = wrapperFactory();
        NBTType<PrimitiveMap<WeakHolder<T>, W>> parentType = PrimitiveMap.TYPE.cast();
        AttrKeyValue.CustomWidgetFactory<WeakEntryPrimitiveMap<T, W>> widgetFactory = (attr, x, y, dx, dy) -> parentType
                .customWidgetFactory()
                .generateWidget(new TypeConvertAttrKeyValue<>(attr, factory, parentType), x, y, dx, dy);
        return new NBTType<>(
                "weakentryprimitivemap",
                factory.wrapCodecXmap(parentType.typeCodec()),
                widgetFactory,
                (WeakEntryPrimitiveMap<T, W>)
                        new WeakEntryPrimitiveMap<>(Map.of(), RegistryKeys.BLOCK.getValue(), NBTTypes.STRING_TYPE));
    }

    public static final NBTType<WeakEntryPrimitiveMap<Object, Object>> TYPE = createEntry();

    @Override
    public NBTType<PrimitiveMap<WeakHolder<T>, W>> type() {
        return TYPE.cast();
    }

    @Override
    protected PrimitiveMap<WeakHolder<T>, W> withDefault(
            Map<WeakHolder<T>, W> map,
            Optional<Primitive<WeakHolder<T>>> defaultKeyPrimitive,
            Optional<Primitive<W>> defaultValuePrimitive) {
        Identifier registry = this.registry();
        return new WeakEntryPrimitiveMap<>(registry, valueType(), defaultValuePrimitive, map);
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof WeakEntryPrimitiveMap<?, ?> that && super.equals(that);
    }
}
