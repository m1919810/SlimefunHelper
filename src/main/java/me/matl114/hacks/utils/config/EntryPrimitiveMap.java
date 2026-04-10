package me.matl114.hacks.utils.config;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.function.Function;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.CodecUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

@Getter
@Accessors(fluent = true)
public class EntryPrimitiveMap<T, W> implements NBTParsable<EntryPrimitiveMap<T, W>> {
    final Registry<T> keyType;
    final NBTType<W> valueType;
    final W defaultValue;
    final Map<T, W> map;
    Map<Holder<T>, Primitive<W>> originValue;

    public static <T, W> Class<EntryPrimitiveMap<T, W>> parameter() {
        return (Class<EntryPrimitiveMap<T, W>>) (Class) EntryPrimitiveMap.class;
    }

    public EntryPrimitiveMap(Registry<T> registry, NBTType<W> type, Map<T, W> map) {
        this(registry, type, map, null);
    }

    public EntryPrimitiveMap(Registry<T> registry, NBTType<W> type, Map<T, W> map, W defaultValue) {
        this.keyType = registry;
        this.valueType = type;
        this.map = new LinkedHashMap<>(map);
        this.defaultValue = defaultValue;
    }

    public EntryPrimitiveMap(Map<Holder<T>, Primitive<W>> map, Registry<T> registry, NBTType<W> type)
            throws RuntimeException {
        this.keyType = registry;
        this.valueType = type;
        originValue = map;
        this.map = new LinkedHashMap<>(map.size());
        W defa = null;
        for (Map.Entry<Holder<T>, Primitive<W>> entry : map.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            Preconditions.checkArgument(registry.getKey() == key.registry().getKey());
            Preconditions.checkArgument(value.valueType() == type);
            if (key.entry() == null) {
                defa = value.value();
            } else {
                this.map.put(key.entry(), value.value());
            }
        }
        this.defaultValue = defa;
    }

    public Map<Holder<T>, Primitive<W>> toMap() {
        if (originValue == null) {
            Map<Holder<T>, Primitive<W>> originValue = new LinkedHashMap<>();
            for (var re : map.entrySet()) {
                originValue.put(Holder.of(keyType, re.getKey()), Primitive.of(valueType, re.getValue()));
            }
            if (defaultValue != null) {
                originValue.put(Holder.of(keyType, null), Primitive.of(valueType, defaultValue));
            }
            this.originValue = originValue;
        }
        return originValue;
    }

    NBTType<Map<Holder<T>, Primitive<W>>> cachedEntryType;

    public static final <T, W> NBTType<EntryPrimitiveMap<T, W>> create() {
        Function<EntryPrimitiveMap<T, W>, NBTType<Map<Holder<T>, Primitive<W>>>> typeGenerator = (w) -> {
            if (w.cachedEntryType == null) {
                w.cachedEntryType = NBTTypes.createArrayMapLike(
                        "parametered_map",
                        Holder.TYPE.<Holder<T>>cast(),
                        () -> Holder.of(w.keyType, null),
                        "key",
                        Primitive.TYPE.cast(),
                        () -> Primitive.of(w.valueType, w.valueType.empty()),
                        "value",
                        WrapperFactory.identity(),
                        AttrKeyValue.CustomWidgetFactory.cutSizeXLeft(0.5),
                        AttrKeyValue.CustomWidgetFactory.cutSizeXRight(0.5),
                        300,
                        20);
            }
            return w.cachedEntryType;
        };
        return new NBTType<EntryPrimitiveMap<T, W>>(
                NBTType.<EntryPrimitiveMap<T, W>>parameter(EntryPrimitiveMap.class),
                RecordCodecBuilder.<EntryPrimitiveMap<T, W>>create(oInstance -> oInstance
                        .group(
                                CodecUtils.arrayMapCodec(
                                                Holder.TYPE.<Holder<T>>cast().typeCodec(),
                                                Primitive.TYPE
                                                        .<Primitive<W>>cast()
                                                        .typeCodec())
                                        .fieldOf("data")
                                        .forGetter(EntryPrimitiveMap::toMap),
                                ((Codec<Registry<T>>) Registries.REGISTRIES.getCodec())
                                        .fieldOf("key_type")
                                        .forGetter(EntryPrimitiveMap::keyType),
                                NBTTypes.<W>codec().fieldOf("value_type").forGetter(EntryPrimitiveMap::valueType))
                        .apply(oInstance, EntryPrimitiveMap::new)),
                (w, x, y, dx, dy) -> {
                    EntryPrimitiveMap<T, W> map = w.getOriginValue();
                    WrapperFactory<Map<Holder<T>, Primitive<W>>, EntryPrimitiveMap<T, W>> wrapperFactory =
                            WrapperFactory.of(
                                    mp -> new EntryPrimitiveMap<>(mp, map.keyType, map.valueType),
                                    EntryPrimitiveMap::toMap);
                    return new TypeConvertAttrKeyValue<>(w, wrapperFactory, typeGenerator.apply(map))
                            .generateValueWidget(x, y, dx, dy);
                },
                null,
                (EntryPrimitiveMap<T, W>) new EntryPrimitiveMap<>(Map.of(), Registries.BLOCK, NBTTypes.STRING_TYPE));
    }

    public static final NBTType<EntryPrimitiveMap<Object, Object>> TYPE = create();

    public W getOrDefault(T va) {
        return map.getOrDefault(va, defaultValue);
    }

    @Override
    public NBTType<EntryPrimitiveMap<T, W>> type() {
        return TYPE.cast();
    }
}
