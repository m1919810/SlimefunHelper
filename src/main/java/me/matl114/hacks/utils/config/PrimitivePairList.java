package me.matl114.hacks.utils.config;

import com.google.common.base.Preconditions;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.PairLikeFactory;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.AttrKeyValues;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;
import net.minecraft.text.Text;

@Getter
@Accessors(fluent = true)
public class PrimitivePairList<T, W> implements NBTParsable<PrimitivePairList<T, W>> {
    final NBTType<T> firstType;
    final NBTType<W> secondType;
    final String firstName;
    final String secondName;
    final List<Pair<T, W>> list;
    List<Pair<Primitive<T>, Primitive<W>>> originValue;

    public PrimitivePairList(
            String firstName, String secondName, NBTType<T> firstType, NBTType<W> secondType, List<Pair<T, W>> list) {
        this.firstName = firstName;
        this.secondName = secondName;
        this.firstType = firstType;
        this.secondType = secondType;
        this.list = new ArrayList<>(list);
    }

    public PrimitivePairList(
            String firstName,
            String secondName,
            List<Pair<Primitive<T>, Primitive<W>>> primitiveList,
            NBTType<T> firstType,
            NBTType<W> secondType) {
        this.firstName = firstName;
        this.secondName = secondName;
        this.firstType = firstType;
        this.secondType = secondType;
        this.originValue = primitiveList;
        this.list = new ArrayList<>(primitiveList.size());
        for (var entry : primitiveList) {
            Primitive<T> first = entry.getFirst();
            Primitive<W> second = entry.getSecond();
            Preconditions.checkArgument(first.valueType() == firstType);
            Preconditions.checkArgument(second.valueType() == secondType);
            this.list.add(Pair.of(first.value(), second.value()));
        }
    }

    public List<Pair<Primitive<T>, Primitive<W>>> toPrimitivePairList() {
        if (originValue == null) {
            List<Pair<Primitive<T>, Primitive<W>>> cached = new ArrayList<>();
            for (var entry : list) {
                cached.add(Pair.of(
                        Primitive.of(firstType, entry.getFirst()), Primitive.of(secondType, entry.getSecond())));
            }
            this.originValue = cached;
        }
        return originValue;
    }

    NBTType<List<Pair<Primitive<T>, Primitive<W>>>> cachedEntryType;

    public static final <T, W> NBTType<PrimitivePairList<T, W>> create() {
        NBTType<Pair<Primitive<T>, Primitive<W>>> pairCodecType =
                NBTTypes.<Pair<Primitive<T>, Primitive<W>>, Primitive<T>, Primitive<W>>createPairLike(
                        (Class) Pair.class,
                        Primitive.TYPE.cast(),
                        "first",
                        Primitive.TYPE.cast(),
                        "second",
                        PairLikeFactory.of(Pair::of, Pair::getFirst, Pair::getSecond),
                        AttrKeyValue.CustomWidgetFactory.cutSizeXLeft(0.5),
                        AttrKeyValue.CustomWidgetFactory.cutSizeXRight(0.5));
        Codec<Pair<Primitive<T>, Primitive<W>>> pairCodec = pairCodecType.typeCodec();
        return new NBTType<PrimitivePairList<T, W>>(
                NBTType.<PrimitivePairList<T, W>>parameter(PrimitivePairList.class),
                RecordCodecBuilder.<PrimitivePairList<T, W>>create(instance -> instance.group(
                                Codec.STRING.optionalFieldOf("first_name", "").forGetter(PrimitivePairList::firstName),
                                Codec.STRING
                                        .optionalFieldOf("second_name", "")
                                        .forGetter(PrimitivePairList::secondName),
                                Codec.list(pairCodec).fieldOf("data").forGetter(PrimitivePairList::toPrimitivePairList),
                                NBTTypes.<T>codec().fieldOf("first_type").forGetter(PrimitivePairList::firstType),
                                NBTTypes.<W>codec().fieldOf("second_type").forGetter(PrimitivePairList::secondType))
                        .apply(instance, PrimitivePairList::new)),
                (AttrKeyValue.CustomWidgetFactory<PrimitivePairList<T, W>>) (attr, x, y, dx, dy) -> {
                    PrimitivePairList<T, W> pairList = attr.getOriginValue();
                    String firstName = pairList.firstName();
                    String secondName = pairList.secondName();
                    NBTType<Pair<Primitive<T>, Primitive<W>>> pairType =
                            NBTTypes.<Pair<Primitive<T>, Primitive<W>>, Primitive<T>, Primitive<W>>createPairLike(
                                    (Class) Pair.class,
                                    Primitive.TYPE.cast(),
                                    "first",
                                    Primitive.TYPE.cast(),
                                    "second",
                                    PairLikeFactory.of(Pair::of, Pair::getFirst, Pair::getSecond),
                                    (w) -> {
                                        return AttrKeyValue.CustomWidgetFactory.cutSizeXLeft(0.5)
                                                .apply(AttrKeyValue.CustomWidgetFactory.withLabel(
                                                                Text.translatableWithFallback(firstName, firstName))
                                                        .apply((AttrKeyValue.CustomWidgetFactory) w));
                                    },
                                    (w) -> {
                                        return AttrKeyValue.CustomWidgetFactory.cutSizeXRight(0.5)
                                                .apply(AttrKeyValue.CustomWidgetFactory.withLabel(
                                                                Text.translatableWithFallback(secondName, secondName))
                                                        .apply((AttrKeyValue.CustomWidgetFactory) w));
                                    });
                    AttrKeyValue.CustomWidgetFactory<List<Pair<Primitive<T>, Primitive<W>>>> widgetFactory =
                            (w1, x1, y1, dx1, dy1) -> {
                                return NBTTypes.generateListModifyButton(
                                        w1,
                                        pairType,
                                        () -> Pair.of(
                                                Primitive.of(pairList.firstType, pairList.firstType.createEmpty()),
                                                Primitive.of(pairList.secondType, pairList.secondType.createEmpty())),
                                        x1,
                                        y1,
                                        dx1,
                                        dy1,
                                        300,
                                        20);
                            };
                    WrapperFactory<String, List<Pair<Primitive<T>, Primitive<W>>>> stringListWrapperFactory =
                            AttrKeyValues.STR_LIST_FACTORY.concat(WrapperFactory.list(pairType.stringifyFactory()));
                    WrapperFactory<List<Pair<Primitive<T>, Primitive<W>>>, PrimitivePairList<T, W>> wrapperFactory =
                            WrapperFactory.of(
                                    mp -> new PrimitivePairList<>(
                                            firstName, secondName, mp, pairList.firstType, pairList.secondType),
                                    PrimitivePairList::toPrimitivePairList);
                    return new TypeConvertAttrKeyValue<>(attr, wrapperFactory, widgetFactory, stringListWrapperFactory)
                            .generateValueWidget(x, y, dx, dy);
                },
                null,
                (PrimitivePairList<T, W>)
                        new PrimitivePairList<>("", "", NBTTypes.STRING_TYPE, NBTTypes.STRING_TYPE, List.of()));
    }

    public static final NBTType<PrimitivePairList<Object, Object>> TYPE = create();

    public static <T, W> Class<PrimitivePairList<T, W>> parameter() {
        return (Class<PrimitivePairList<T, W>>) (Class) PrimitivePairList.class;
    }

    @Override
    public NBTType<PrimitivePairList<T, W>> type() {
        return TYPE.cast();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof PrimitivePairList<?, ?> that)) return false;
        return Objects.equals(firstType, that.firstType)
                && Objects.equals(secondType, that.secondType)
                && Objects.equals(list, that.list);
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstType, secondType, list);
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return NBTParsable.super.isSameType(type)
                && type instanceof PrimitivePairList pairList
                && pairList.firstType == firstType
                && pairList.secondType == secondType
                && Objects.equals(pairList.firstName, firstName)
                && Objects.equals(pairList.secondName, secondName);
    }

    @Override
    public <R> Optional<PrimitivePairList<T, W>> tryTypeConvert(Ref<R> ref) {
        if (ref instanceof NBTRef nbt
                && nbt.get() instanceof PrimitivePairList<?, ?> pairList
                && pairList.firstType == firstType
                && pairList.secondType == secondType) {
            return Optional.of(new PrimitivePairList<>(
                    this.firstName, this.secondName, firstType, secondType, (List) pairList.list()));
        }
        return Optional.empty();
    }
}
