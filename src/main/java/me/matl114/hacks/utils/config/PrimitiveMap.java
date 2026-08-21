package me.matl114.hacks.utils.config;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.utils.CodecUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;

@Getter
@Accessors(fluent = true)
public class PrimitiveMap<T, W> implements NBTParsable<PrimitiveMap<T, W>> {
    final NBTType<T> keyType;
    final NBTType<W> valueType;
    final Optional<Primitive<T>> defaultKeyPrimitive;
    final Optional<Primitive<W>> defaultValuePrimitive;
    final Map<T, W> map;
    Map<Primitive<T>, Primitive<W>> originValue;

    public PrimitiveMap(NBTType<T> keyType, NBTType<W> valueType, Map<T, W> map) {
        this(keyType, valueType, map, Optional.empty(), Optional.empty());
    }

    public PrimitiveMap(
            NBTType<T> keyType, NBTType<W> valueType, Map<T, W> map, T defaultKeyPrimitive, W defaultValuePrimitive) {
        this(
                keyType,
                valueType,
                map,
                Optional.of(Primitive.of(keyType, defaultKeyPrimitive)),
                Optional.of(Primitive.of(valueType, defaultValuePrimitive)));
    }

    protected PrimitiveMap(
            NBTType<T> keyType,
            NBTType<W> valueType,
            Map<T, W> map,
            Optional<Primitive<T>> defaultKeyPrimitive,
            Optional<Primitive<W>> defaultValuePrimitive) {
        this.keyType = keyType;
        this.valueType = valueType;
        this.map = new LinkedHashMap<>(map);
        this.defaultKeyPrimitive = defaultKeyPrimitive.map(entry -> {
            Preconditions.checkArgument(entry.valueType() == keyType);
            return entry;
        });
        this.defaultValuePrimitive = defaultValuePrimitive.map(entry -> {
            Preconditions.checkArgument(entry.valueType() == valueType);
            return entry;
        });
    }

    public PrimitiveMap(Map<Primitive<T>, Primitive<W>> map, NBTType<T> keyType, NBTType<W> valueType) {
        this(map, keyType, valueType, Optional.empty(), Optional.empty());
    }

    public PrimitiveMap(
            Map<Primitive<T>, Primitive<W>> map,
            NBTType<T> keyType,
            NBTType<W> valueType,
            Optional<Primitive<T>> defaultKeyPrimitive,
            Optional<Primitive<W>> defaultValuePrimitive) {
        this.keyType = keyType;
        this.valueType = valueType;
        this.originValue = map;
        this.map = new LinkedHashMap<>(map.size());
        for (Map.Entry<Primitive<T>, Primitive<W>> entry : map.entrySet()) {
            Primitive<T> key = entry.getKey();
            Primitive<W> value = entry.getValue();
            Preconditions.checkArgument(key.valueType() == keyType);
            Preconditions.checkArgument(value.valueType() == valueType);
            this.map.put(key.value(), value.value());
        }
        this.defaultKeyPrimitive = defaultKeyPrimitive.map(entry -> {
            Preconditions.checkArgument(entry.valueType() == keyType);
            return entry;
        });
        this.defaultValuePrimitive = defaultValuePrimitive.map(entry -> {
            Preconditions.checkArgument(entry.valueType() == valueType);
            return entry;
        });
    }

    public Map<Primitive<T>, Primitive<W>> toPrimitiveMap() {
        if (originValue == null) {
            Map<Primitive<T>, Primitive<W>> cached = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                cached.put(Primitive.of(keyType, entry.getKey()), Primitive.of(valueType, entry.getValue()));
            }
            this.originValue = cached;
        }
        return originValue;
    }

    private Primitive<T> copyKeyPrimitive(Primitive<T> value) {
        return Primitive.of(
                value.valueType(), value.valueType().parse(value.valueType().toNbt(value.value())));
    }

    private Primitive<W> copyValuePrimitive(Primitive<W> value) {
        return Primitive.of(
                value.valueType(), value.valueType().parse(value.valueType().toNbt(value.value())));
    }

    public Primitive<T> createNewKeyPrimitive() {
        return defaultKeyPrimitive
                .map(this::copyKeyPrimitive)
                .orElseGet(() -> Primitive.of(keyType, keyType.createEmpty()));
    }

    public Primitive<W> createNewValuePrimitive() {
        return defaultValuePrimitive
                .map(this::copyValuePrimitive)
                .orElseGet(() -> Primitive.of(valueType, valueType.createEmpty()));
    }

    NBTType<Map<Primitive<T>, Primitive<W>>> cachedEntryType;

    public static <T, W> NBTType<PrimitiveMap<T, W>> create() {
        Codec<Primitive<T>> keyCodec = Primitive.TYPE.<Primitive<T>>cast().typeCodec();
        Codec<Primitive<W>> valueCodec = Primitive.TYPE.<Primitive<W>>cast().typeCodec();
        Function<PrimitiveMap<T, W>, NBTType<Map<Primitive<T>, Primitive<W>>>> typeGenerator = (w) -> {
            if (w.cachedEntryType == null) {
                w.cachedEntryType = NBTTypes.createArrayMapLike(
                        "primitive_map",
                        Primitive.TYPE.<Primitive<T>>cast(),
                        w::createNewKeyPrimitive,
                        "key",
                        Primitive.TYPE.<Primitive<W>>cast(),
                        w::createNewValuePrimitive,
                        "value",
                        WrapperFactory.identity(),
                        AttrKeyValue.CustomWidgetFactory.cutSizeXLeft(0.5),
                        AttrKeyValue.CustomWidgetFactory.cutSizeXRight(0.5),
                        300,
                        20);
            }
            return w.cachedEntryType;
        };
        return new NBTType<>(
                "primitivemap",
                RecordCodecBuilder.<PrimitiveMap<T, W>>create(instance -> instance.group(
                                CodecUtils.arrayMapCodec(keyCodec, valueCodec)
                                        .fieldOf("data")
                                        .forGetter(PrimitiveMap::toPrimitiveMap),
                                NBTTypes.<T>codec().fieldOf("key_type").forGetter(PrimitiveMap::keyType),
                                NBTTypes.<W>codec().fieldOf("value_type").forGetter(PrimitiveMap::valueType),
                                keyCodec.optionalFieldOf("default_key_primitive")
                                        .forGetter(PrimitiveMap::defaultKeyPrimitive),
                                valueCodec
                                        .optionalFieldOf("default_value_primitive")
                                        .forGetter(PrimitiveMap::defaultValuePrimitive))
                        .apply(instance, PrimitiveMap::new)),
                (attr, x, y, dx, dy) -> {
                    PrimitiveMap<T, W> map = attr.getOriginValue();
                    WrapperFactory<Map<Primitive<T>, Primitive<W>>, PrimitiveMap<T, W>> wrapperFactory =
                            WrapperFactory.of(
                                    mp -> new PrimitiveMap<>(
                                            mp,
                                            map.keyType,
                                            map.valueType,
                                            map.defaultKeyPrimitive,
                                            map.defaultValuePrimitive),
                                    PrimitiveMap::toPrimitiveMap);
                    return new TypeConvertAttrKeyValue<>(attr, wrapperFactory, typeGenerator.apply(map))
                            .generateValueWidget(x, y, dx, dy);
                },
                (PrimitiveMap<T, W>) new PrimitiveMap<>(NBTTypes.STRING_TYPE, NBTTypes.STRING_TYPE, Map.of()));
    }

    public static final NBTType<PrimitiveMap<Object, Object>> TYPE = create();

    public static <T, W> Class<PrimitiveMap<T, W>> classType() {
        return (Class<PrimitiveMap<T, W>>) (Class) PrimitiveMap.class;
    }

    public static <T, W, R extends PrimitiveMap<T, W>> Codec<R> inheritedCodec(
            Function<PrimitiveMap<T, W>, R> wrapper) {
        return PrimitiveMap.TYPE.<PrimitiveMap<T, W>>cast().typeCodec().xmap(wrapper, map -> map);
    }

    public static <T, W, R extends PrimitiveMap<T, W>> Codec<R> inheritedAlternativeCodec(
            Function<PrimitiveMap<T, W>, R> wrapper, Codec<R> legacyCodec) {
        return Codec.withAlternative(inheritedCodec(wrapper), legacyCodec);
    }

    public static <T, W, R extends PrimitiveMap<T, W>> AttrKeyValue.CustomWidgetFactory<R> inheritedWidgetFactory(
            Function<PrimitiveMap<T, W>, R> wrapper) {
        NBTType<PrimitiveMap<T, W>> delegateType = PrimitiveMap.TYPE.cast();
        WrapperFactory<PrimitiveMap<T, W>, R> wrapperFactory = WrapperFactory.of(wrapper, map -> map);
        return (attr, x, y, dx, dy) ->
                new TypeConvertAttrKeyValue<>(attr, wrapperFactory, delegateType).generateValueWidget(x, y, dx, dy);
    }

    @Override
    public NBTType<PrimitiveMap<T, W>> type() {
        return TYPE.cast();
    }

    protected PrimitiveMap<T, W> withDefault(
            Map<T, W> map, Optional<Primitive<T>> defaultKeyPrimitive, Optional<Primitive<W>> defaultValuePrimitive) {
        return new PrimitiveMap<>(keyType, valueType, map, defaultKeyPrimitive, defaultValuePrimitive);
    }

    @Override
    public <R> Optional<PrimitiveMap<T, W>> tryTypeConvert(Ref<R> ref) {
        if (ref instanceof NBTRef<?> nbt
                && nbt.get() instanceof PrimitiveMap<?, ?> primitiveMap
                && primitiveMap.keyType == keyType
                && primitiveMap.valueType == valueType) {
            return Optional.of(withDefault((Map<T, W>) primitiveMap.map(), defaultKeyPrimitive, defaultValuePrimitive));
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof PrimitiveMap<?, ?> that)) return false;
        return Objects.equals(keyType, that.keyType)
                && Objects.equals(valueType, that.valueType)
                && Objects.equals(defaultKeyPrimitive, that.defaultKeyPrimitive)
                && Objects.equals(defaultValuePrimitive, that.defaultValuePrimitive)
                && Objects.equals(map, that.map);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyType, valueType, defaultKeyPrimitive, defaultValuePrimitive, map);
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return NBTParsable.super.isSameType(type)
                && type instanceof PrimitiveMap<?, ?> primitiveMap
                && primitiveMap.keyType == keyType
                && primitiveMap.valueType == valueType
                && Objects.equals(primitiveMap.defaultKeyPrimitive, defaultKeyPrimitive)
                && Objects.equals(primitiveMap.defaultValuePrimitive, defaultValuePrimitive);
    }
}
