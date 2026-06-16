package me.matl114.hacks.utils.config;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.Getter;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.CodecUtils;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.PairLikeFactory;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;

public class EnumPrimitiveList<T extends ConfigEnum, W> implements NBTParsable<EnumPrimitiveList<T, W>> {
    @Getter
    String type;

    boolean resolve = false;

    @Getter
    NBTType<W> primitiveType;

    List<LazyEntry> lazyEntryList = new ArrayList<>();

    public EnumPrimitiveList(List<Pair<String, Primitive<W>>> lazyEntryList, String type, NBTType<W> primitiveType) {
        this.type = type;
        this.primitiveType = primitiveType;
        this.lazyEntryList = lazyEntryList.stream()
                .map(p -> new LazyEntry(p.getFirst(), p.getSecond().value()))
                .toList();
    }

    public EnumPrimitiveList(Class<T> type, NBTType<W> primitiveType, List<Pair<T, W>> lazyEntryList) {
        this(ConfigEnum.getConfigEnumType(type), primitiveType, lazyEntryList);
        ConfigEnum.ensureRegistered(type);
    }

    public EnumPrimitiveList(String type, NBTType<W> primitiveType, List<Pair<T, W>> lazyEntryList) {
        this.type = type;
        tryResolve();
        this.primitiveType = primitiveType;
        this.lazyEntryList = lazyEntryList.stream()
                .map(s -> new LazyEntry(s.getFirst(), s.getSecond()))
                .toList();
    }

    public static <T extends ConfigEnum, W> EnumPrimitiveList<T, W> of(
            List<Pair<T, Primitive<W>>> lazyEntryList, String type, NBTType<W> primitiveType) {
        return new EnumPrimitiveList<>(
                type,
                primitiveType,
                lazyEntryList.stream()
                        .map(s -> Pair.of(s.getFirst(), s.getSecond().value()))
                        .toList());
    }

    public void forEach(BiConsumer<T, W> consumer) {
        if (resolve) {
            lazyEntryList.forEach(entry -> {
                if (entry.tryResolve()) {
                    consumer.accept(entry.lazyValue, entry.value);
                }
            });
        }
    }

    private void tryResolve() {
        if (ConfigEnum.registeredConfigs.containsKey(type)) {
            resolve = true;
        }
    }

    private List<Pair<String, Primitive<W>>> toPrimitive() {
        return lazyEntryList.stream()
                .map(s -> Pair.of(s.name, Primitive.of(this.primitiveType, s.value)))
                .toList();
    }

    private List<Pair<T, Primitive<W>>> toList() {
        return lazyEntryList.stream()
                .map(s -> Pair.of(s.lazyValue(), Primitive.of(this.primitiveType, s.value)))
                .toList();
    }

    @Override
    public NBTType<EnumPrimitiveList<T, W>> type() {
        return TYPE.cast();
    }

    private class LazyEntry {
        public LazyEntry(String name, W value) {
            this.name = name;
            this.value = value;
            tryResolve();
        }

        public LazyEntry(T key, W value) {
            this.name = key.cast().name();
            this.value = value;
            this.lazyValue = key;
        }

        String name;
        T lazyValue;
        W value;

        public boolean tryResolve() {
            if (!resolve) {
                EnumPrimitiveList.this.tryResolve();
            }
            if (resolve) {
                if (lazyValue == null) {
                    var re = ConfigEnum.registeredConfigs.get(type);
                    lazyValue = (T) re.get(name);
                }
                return lazyValue != null;
            } else {
                return false;
            }
        }

        public T lazyValue() {
            tryResolve();
            return lazyValue;
        }
    }

    public static final NBTType<EnumPrimitiveList<?, ?>> TYPE = (NBTType) create();

    NBTType<List<Pair<T, Primitive<W>>>> cachedEntryType;

    public static final <T extends ConfigEnum, W> NBTType<EnumPrimitiveList<T, W>> create() {
        Function<EnumPrimitiveList<T, W>, NBTType<List<Pair<T, Primitive<W>>>>> typeGenerator = (w) -> {
            if (w.cachedEntryType == null) {
                w.tryResolve();
                var finiteMap = ConfigEnum.registeredConfigs.get(w.type);

                NBTType<Pair<T, Primitive<W>>> pairLikeNbtType = NBTTypes.createPairLike(
                        (Class) Pair.class,
                        NBTTypes.createEnumLike(
                                "config_enum_lookup", finiteMap, s -> s.cast().name()),
                        "key",
                        Primitive.TYPE.cast(),
                        "value",
                        PairLikeFactory.pair(),
                        AttrKeyValue.CustomWidgetFactory.cutSizeXLeft(0.5),
                        AttrKeyValue.CustomWidgetFactory.cutSizeXRight(0.5));
                w.cachedEntryType =
                        NBTTypes.createListLke("parametered_map", pairLikeNbtType, WrapperFactory.identity(), 300, 20);
            }
            return w.cachedEntryType;
        };
        Codec<EnumPrimitiveList<T, W>> codec = RecordCodecBuilder.create(oInstance -> oInstance
                .group(
                        CodecUtils.pairListCodec(
                                        Codec.STRING,
                                        Primitive.TYPE.<Primitive<W>>cast().typeCodec())
                                .fieldOf("data")
                                .forGetter(EnumPrimitiveList::toPrimitive),
                        Codec.STRING.fieldOf("key_type").forGetter(EnumPrimitiveList::getType),
                        NBTTypes.<W>codec().fieldOf("value_type").forGetter(EnumPrimitiveList::getPrimitiveType))
                .apply(oInstance, EnumPrimitiveList::new));
        return new NBTType<EnumPrimitiveList<T, W>>(
                NBTType.<EnumPrimitiveList<T, W>>parameter(EnumPrimitiveList.class),
                codec,
                (w, x, y, dx, dy) -> {
                    EnumPrimitiveList<T, W> map = w.getOriginValue();
                    map.tryResolve();
                    WrapperFactory<List<Pair<T, Primitive<W>>>, EnumPrimitiveList<T, W>> wrapperFactory =
                            WrapperFactory.of(
                                    mp -> EnumPrimitiveList.of(mp, map.getType(), map.getPrimitiveType()),
                                    EnumPrimitiveList::toList);
                    return new TypeConvertAttrKeyValue<>(w, wrapperFactory, typeGenerator.apply(map))
                            .generateValueWidget(x, y, dx, dy);
                },
                null,
                (EnumPrimitiveList<T, W>) new EnumPrimitiveList<>(List.of(), "config_enum", NBTTypes.STRING_TYPE));
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return NBTParsable.super.isSameType(type)
                && type instanceof EnumPrimitiveList enumList
                && Objects.equals(enumList.type, this.type)
                && this.primitiveType == enumList.primitiveType;
    }
}
