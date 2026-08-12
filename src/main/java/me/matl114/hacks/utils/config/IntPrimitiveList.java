package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import me.matl114.gui.WidgetUtils;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.IntListAttrKeyValue;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;

public class IntPrimitiveList extends PrimitiveList<Integer> {
    public IntPrimitiveList(List<Integer> list) {
        super(NBTTypes.INT_TYPE, list);
    }

    protected IntPrimitiveList(List<Integer> list, Optional<Primitive<Integer>> defaultPrimitive) {
        super(NBTTypes.INT_TYPE, list, defaultPrimitive);
    }

    public static final WrapperFactory<String, List<Integer>> SIMPLIFIED_STRINGIFY_FACTORY = WrapperFactory.of(
            str -> {
                String[] splits = str.split(",", -1);
                List<Integer> list = new ArrayList<>();
                for (String s : splits) {
                    list.add(Integer.parseInt(s));
                }
                return list;
            },
            arr -> arr.stream().map(String::valueOf).collect(Collectors.joining(",")));

    public static final WrapperFactory<String, IntPrimitiveList> SIMPLIFIED_INT_PRIMITIVE_LIST_FACTORY =
            SIMPLIFIED_STRINGIFY_FACTORY.concat(WrapperFactory.of(IntPrimitiveList::new, IntPrimitiveList::list));

    public static final NBTType<IntPrimitiveList> TYPE = new NBTType<>(
            "intprimitivelist",
            SIMPLIFIED_INT_PRIMITIVE_LIST_FACTORY.wrapCodecComapFlatMap(Codec.STRING),
            (s, x, y, dx, dy) -> {
                return new SubScreenWidget(x, y, dx, dy)
                        .addDrawableChild(new TypeConvertAttrKeyValue<>(
                                        s, SIMPLIFIED_INT_PRIMITIVE_LIST_FACTORY, NBTTypes.STRING_TYPE)
                                .generateValueWidget(0, 0, dx - dy, dy))
                        .addDrawableChild(WidgetUtils.createOpenListModifyScreenButton(
                                () -> new IntListAttrKeyValue(
                                        s.getKeyName(), s.getOriginValue().list(), SIMPLIFIED_STRINGIFY_FACTORY),
                                (lst) -> s.valueChangeInternal(null, new IntPrimitiveList(lst)),
                                dx - dy,
                                0,
                                dy,
                                dy));
            },
            SIMPLIFIED_INT_PRIMITIVE_LIST_FACTORY,
            new IntPrimitiveList(List.of()));

    @Override
    public NBTType<PrimitiveList<Integer>> type() {
        return TYPE.cast();
    }

    @Override
    protected PrimitiveList<Integer> withDefault(List<Integer> list, Optional<Primitive<Integer>> defaultPrimitive) {
        return new IntPrimitiveList(list, defaultPrimitive);
    }
}
