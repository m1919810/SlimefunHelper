package me.matl114.hacks.utils.config;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.AttrKeyValues;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;

@Getter
@Accessors(fluent = true)
public class PrimitiveList<W> implements NBTParsable<PrimitiveList<W>> {
    final NBTType<W> elementType;
    final List<W> list;
    List<Primitive<W>> _cached;

    public static <R> Class<PrimitiveList<R>> type(Class<R> clazz) {
        return (Class) PrimitiveList.class;
    }

    public PrimitiveList(NBTType<W> primitive, List<W> list) {
        this.elementType = primitive;
        this.list = new ArrayList<>(list);
    }

    public PrimitiveList(List<Primitive<W>> primitiveList, NBTType<W> primitive) {
        this.elementType = primitive;
        this._cached = primitiveList;
        this.list = new ArrayList<>(primitiveList.size());
        for (var entry : primitiveList) {
            Preconditions.checkArgument(entry.valueType() == primitive);
            this.list.add(entry.value());
        }
    }

    public List<Primitive<W>> toPrimitiveList() {
        if (_cached == null) {
            List<Primitive<W>> cached = new ArrayList<>();
            for (var entry : list) {
                cached.add(Primitive.of(elementType, entry));
            }
            this._cached = cached;
        }
        return _cached;
    }

    public static final <W> NBTType<PrimitiveList<W>> create() {
        Codec<Primitive<W>> primitiveCodec = (Codec<Primitive<W>>) (Codec) Primitive.TYPE.typeCodec();
        return new NBTType<PrimitiveList<W>>(
                NBTType.<PrimitiveList<W>>parameter(PrimitiveList.class),
                RecordCodecBuilder.<PrimitiveList<W>>create(oInstance -> oInstance
                        .group(
                                Codec.list(primitiveCodec)
                                        .fieldOf("data")
                                        .<PrimitiveList<W>>forGetter(PrimitiveList::toPrimitiveList),
                                NBTTypes.<W>codec()
                                        .fieldOf("element_type")
                                        .<PrimitiveList<W>>forGetter(PrimitiveList::elementType))
                        .apply(oInstance, PrimitiveList::new)),
                (AttrKeyValue.CustomWidgetFactory<PrimitiveList<W>>) (w, x, y, dx, dy) -> {
                    PrimitiveList<W> map = w.getOriginValue();
                    NBTType<W> type = map.elementType();
                    AttrKeyValue.CustomWidgetFactory<List<W>> widgetFactory = (w1, x1, y1, dx1, dy1) -> {
                        return NBTTypes.generateListModifyButton(
                                w1, type, type::createEmpty, x1, y1, dx1, dy1, 300, 20);
                    };
                    WrapperFactory<String, List<W>> stringListWrapperFactory =
                            AttrKeyValues.STR_LIST_FACTORY.concat(WrapperFactory.list(type.stringifyFactory()));

                    WrapperFactory<List<W>, PrimitiveList<W>> wrapperFactory =
                            WrapperFactory.of(mp -> new PrimitiveList<>(map.elementType, mp), PrimitiveList::list);
                    return new TypeConvertAttrKeyValue<>(w, wrapperFactory, widgetFactory, stringListWrapperFactory)
                            .generateValueWidget(x, y, dx, dy);
                },
                (PrimitiveList<W>) new PrimitiveList<>(List.of(), NBTTypes.STRING_TYPE));
    }

    public static final NBTType<PrimitiveList<Object>> TYPE = create();

    public static <W> Class<PrimitiveList<W>> parameter() {
        return (Class<PrimitiveList<W>>) (Class) PrimitiveList.class;
    }

    @Override
    public NBTType<PrimitiveList<W>> type() {
        return TYPE.cast();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof PrimitiveList<?> that)) return false;
        return Objects.equals(elementType, that.elementType) && Objects.equals(list, that.list);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elementType, list);
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return NBTParsable.super.isSameType(type)
                && type instanceof PrimitiveList<?> that
                && that.elementType == elementType;
    }
}
