package me.matl114.hacks.utils.config;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.CodecUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;

@Getter
@Accessors(fluent = true)
public class UnboundPrimitiveMap<T, W> implements NBTParsable<UnboundPrimitiveMap<T, W>> {
    final NBTType<T> keyType;
    final NBTType<W> valueType;
    final Map<T, W> map;
    Map<Primitive<T>, Primitive<W>> originValue;

    public UnboundPrimitiveMap(NBTType<T> keyType, NBTType<W> valueType, Map<T, W> map) {
        this.keyType = keyType;
        this.valueType = valueType;
        this.map = new LinkedHashMap<>(map);
    }

    public UnboundPrimitiveMap(Map<Primitive<T>, Primitive<W>> map, NBTType<T> keyType, NBTType<W> valueType) {
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

    NBTType<Map<Primitive<T>, Primitive<W>>> cachedEntryType;

    public static final <T, W> NBTType<UnboundPrimitiveMap<T, W>> create() {
        Codec<Primitive<T>> keyCodec = Primitive.TYPE.<Primitive<T>>cast().typeCodec();
        Codec<Primitive<W>> valueCodec = Primitive.TYPE.<Primitive<W>>cast().typeCodec();
        Function<UnboundPrimitiveMap<T, W>, NBTType<Map<Primitive<T>, Primitive<W>>>> typeGenerator = (w) -> {
            if (w.cachedEntryType == null) {
                w.cachedEntryType = NBTTypes.createArrayMapLike(
                        "unbound_primitive_map",
                        Primitive.TYPE.<Primitive<T>>cast(),
                        () -> Primitive.of(w.keyType, w.keyType.createEmpty()),
                        "key",
                        Primitive.TYPE.<Primitive<W>>cast(),
                        () -> Primitive.of(w.valueType, w.valueType.createEmpty()),
                        "value",
                        WrapperFactory.identity(),
                        AttrKeyValue.CustomWidgetFactory.cutSizeXLeft(0.5),
                        AttrKeyValue.CustomWidgetFactory.cutSizeXRight(0.5),
                        300,
                        20);
            }
            return w.cachedEntryType;
        };
        return new NBTType<UnboundPrimitiveMap<T, W>>(
                NBTType.<UnboundPrimitiveMap<T, W>>parameter(UnboundPrimitiveMap.class),
                RecordCodecBuilder.<UnboundPrimitiveMap<T, W>>create(instance -> instance.group(
                                CodecUtils.arrayMapCodec(keyCodec, valueCodec)
                                        .fieldOf("data")
                                        .forGetter(UnboundPrimitiveMap::toPrimitiveMap),
                                NBTTypes.<T>codec().fieldOf("key_type").forGetter(UnboundPrimitiveMap::keyType),
                                NBTTypes.<W>codec().fieldOf("value_type").forGetter(UnboundPrimitiveMap::valueType))
                        .apply(instance, UnboundPrimitiveMap::new)),
                (attr, x, y, dx, dy) -> {
                    UnboundPrimitiveMap<T, W> map = attr.getOriginValue();
                    WrapperFactory<Map<Primitive<T>, Primitive<W>>, UnboundPrimitiveMap<T, W>> wrapperFactory =
                            WrapperFactory.of(
                                    mp -> new UnboundPrimitiveMap<>(mp, map.keyType, map.valueType),
                                    UnboundPrimitiveMap::toPrimitiveMap);
                    return new TypeConvertAttrKeyValue<>(attr, wrapperFactory, typeGenerator.apply(map))
                            .generateValueWidget(x, y, dx, dy);
                },
                null,
                (UnboundPrimitiveMap<T, W>)
                        new UnboundPrimitiveMap<>(NBTTypes.STRING_TYPE, NBTTypes.STRING_TYPE, Map.of()));
    }

    public static final NBTType<UnboundPrimitiveMap<Object, Object>> TYPE = create();

    public static <T, W> Class<UnboundPrimitiveMap<T, W>> parameter() {
        return (Class<UnboundPrimitiveMap<T, W>>) (Class) UnboundPrimitiveMap.class;
    }

    public W get(T key) {
        return map.get(key);
    }

    @Override
    public NBTType<UnboundPrimitiveMap<T, W>> type() {
        return TYPE.cast();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof UnboundPrimitiveMap<?, ?> that)) return false;
        return Objects.equals(keyType, that.keyType)
                && Objects.equals(valueType, that.valueType)
                && Objects.equals(map, that.map);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyType, valueType, map);
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return NBTParsable.super.isSameType(type)
                && type instanceof UnboundPrimitiveMap<?, ?> unboundPrimitiveMap
                && unboundPrimitiveMap.keyType == keyType
                && unboundPrimitiveMap.valueType == valueType;
    }
}
