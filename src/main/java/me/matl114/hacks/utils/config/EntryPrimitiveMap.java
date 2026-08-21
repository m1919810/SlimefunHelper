package me.matl114.hacks.utils.config;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.CodecUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

@Getter
@Accessors(fluent = true)
public class EntryPrimitiveMap<T, W> extends PrimitiveMap<Holder<T>, W> {

    public static <T, W> Class<EntryPrimitiveMap<T, W>> parameter() {
        return (Class<EntryPrimitiveMap<T, W>>) (Class) EntryPrimitiveMap.class;
    }

    public EntryPrimitiveMap(Registry<T> registry, NBTType<W> type, Map<T, W> map) {
        this(registry, type, map, Optional.empty());
    }

    public EntryPrimitiveMap(Registry<T> registry, NBTType<W> type, Map<T, W> map, W defaultValue) {
        this(registry, type, map, Optional.ofNullable(defaultValue).map(value -> Primitive.of(type, value)));
    }

    public EntryPrimitiveMap(
            Registry<T> registry, NBTType<W> type, Map<T, W> map, Optional<Primitive<W>> defaultPrimitive) {
        this(registry, type, defaultPrimitive, valueMapToHolderMap(registry, map));
    }

    protected EntryPrimitiveMap(
            Registry<T> registry, NBTType<W> type, Optional<Primitive<W>> defaultPrimitive, Map<Holder<T>, W> map) {
        super(Holder.TYPE.<Holder<T>>cast(), type, map, createDefaultKeyPrimitive(registry), defaultPrimitive);
    }

    public EntryPrimitiveMap(
            Map<Holder<T>, Primitive<W>> map,
            Registry<T> registry,
            NBTType<W> type,
            Optional<Primitive<W>> defaultPrimitive) {
        this(
                registry,
                type,
                holderMapToValueMap(map, registry, type),
                resolveDefaultPrimitive(map, registry, type, defaultPrimitive));
    }

    private static <T> Optional<Primitive<Holder<T>>> createDefaultKeyPrimitive(Registry<T> registry) {
        return Optional.of(Primitive.of(Holder.TYPE.<Holder<T>>cast(), Holder.of(registry, null)));
    }

    private static <T, W> Map<Holder<T>, W> valueMapToHolderMap(Registry<T> registry, Map<T, W> map) {
        Map<Holder<T>, W> result = new LinkedHashMap<>(map.size());
        for (var entry : map.entrySet()) {
            result.put(Holder.of(registry, entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static <T, W> Map<T, W> holderMapToValueMap(
            Map<Holder<T>, Primitive<W>> map, Registry<T> registry, NBTType<W> type) {
        Map<T, W> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            Holder<T> key = entry.getKey();
            Primitive<W> value = entry.getValue();
            Preconditions.checkArgument(registry.getKey() == key.registry().getKey());
            Preconditions.checkArgument(value.valueType() == type);
            if (key.entry() != null) {
                result.put(key.entry(), value.value());
            }
        }
        return result;
    }

    private static <T, W> Optional<Primitive<W>> resolveDefaultPrimitive(
            Map<Holder<T>, Primitive<W>> map,
            Registry<T> registry,
            NBTType<W> type,
            Optional<Primitive<W>> defaultPrimitive) {
        Primitive<W> legacyDefaultPrimitive = null;
        for (var entry : map.entrySet()) {
            Holder<T> key = entry.getKey();
            Primitive<W> value = entry.getValue();
            Preconditions.checkArgument(registry.getKey() == key.registry().getKey());
            Preconditions.checkArgument(value.valueType() == type);
            if (key.entry() == null) {
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

    private static <T, W> Registry<T> resolveRegistry(PrimitiveMap<Holder<T>, W> map) {
        return map.defaultKeyPrimitive()
                .map(Primitive::value)
                .map(Holder::registry)
                .or(() -> map.map().keySet().stream().findFirst().map(Holder::registry))
                .orElseThrow(() -> new IllegalArgumentException("Can not resolve registry from PrimitiveMap"));
    }

    private static <T, W> Map<Holder<T>, Primitive<W>> toLegacyMap(PrimitiveMap<Holder<T>, W> map) {
        Map<Holder<T>, Primitive<W>> result = new LinkedHashMap<>();
        for (var entry : map.map().entrySet()) {
            result.put(entry.getKey(), Primitive.of(map.valueType(), entry.getValue()));
        }
        return result;
    }

    private static <T, W> EntryPrimitiveMap<T, W> fromPrimitiveMap(PrimitiveMap<Holder<T>, W> map) {
        return new EntryPrimitiveMap<>(
                toLegacyMap(map), resolveRegistry(map), map.valueType(), map.defaultValuePrimitive());
    }

    private static <T, W> WrapperFactory<PrimitiveMap<Holder<T>, W>, EntryPrimitiveMap<T, W>> wrapperFactory() {
        return WrapperFactory.of(EntryPrimitiveMap::fromPrimitiveMap, map -> map);
    }

    public Registry<T> registry() {
        return this.defaultKeyPrimitive
                .map(Primitive::value)
                .map(Holder::registry)
                .orElse(null);
    }

    @Nullable
    public W getEntryValue(T value) {
        var map = map();
        var re = map.get(Holder.of(registry(), value));
        return re != null ? re : map.get(Holder.of(registry(), null));
    }

    public W getEntryValueOr(T value, W fallback) {
        W result = getEntryValue(value);
        return result == null ? fallback : result;
    }

    private static <T, W> Codec<EntryPrimitiveMap<T, W>> legacyCodec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                        CodecUtils.arrayMapCodec(
                                        Holder.TYPE.<Holder<T>>cast().typeCodec(),
                                        Primitive.TYPE.<Primitive<W>>cast().typeCodec())
                                .fieldOf("data")
                                .forGetter(EntryPrimitiveMap::toLegacyMap),
                        ((Codec<Registry<T>>) Registries.REGISTRIES.getCodec())
                                .fieldOf("key_type")
                                .forGetter(EntryPrimitiveMap::registry),
                        NBTTypes.<W>codec().fieldOf("value_type").forGetter(EntryPrimitiveMap::valueType),
                        Primitive.TYPE
                                .<Primitive<W>>cast()
                                .typeCodec()
                                .optionalFieldOf("default_primitive")
                                .forGetter(EntryPrimitiveMap::defaultValuePrimitive))
                .apply(instance, EntryPrimitiveMap::new));
    }

    public static <T, W> NBTType<EntryPrimitiveMap<T, W>> createEntry() {
        WrapperFactory<PrimitiveMap<Holder<T>, W>, EntryPrimitiveMap<T, W>> factory = wrapperFactory();
        NBTType<PrimitiveMap<Holder<T>, W>> parentType = PrimitiveMap.TYPE.cast();
        AttrKeyValue.CustomWidgetFactory<EntryPrimitiveMap<T, W>> widgetFactory = (attr, x, y, dx, dy) -> parentType
                .customWidgetFactory()
                .generateWidget(new TypeConvertAttrKeyValue<>(attr, factory, parentType), x, y, dx, dy);
        return new NBTType<>(
                "entryprimitivemap",
                Codec.withAlternative(factory.wrapCodecXmap(parentType.typeCodec()), legacyCodec()),
                widgetFactory,
                (EntryPrimitiveMap<T, W>) new EntryPrimitiveMap<>(Registries.BLOCK, NBTTypes.STRING_TYPE, Map.of()));
    }

    public static final NBTType<EntryPrimitiveMap<Object, Object>> TYPE = createEntry();

    @Override
    public NBTType<PrimitiveMap<Holder<T>, W>> type() {
        return TYPE.cast();
    }

    @Override
    protected PrimitiveMap<Holder<T>, W> withDefault(
            Map<Holder<T>, W> map,
            Optional<Primitive<Holder<T>>> defaultKeyPrimitive,
            Optional<Primitive<W>> defaultValuePrimitive) {
        Registry<T> registry = registry();
        return new EntryPrimitiveMap<>(registry, valueType(), defaultValuePrimitive, map);
    }

    @Override
    public boolean equals(Object object) {
        return this == object || (object instanceof EntryPrimitiveMap<?, ?> that && super.equals(that));
    }
}
