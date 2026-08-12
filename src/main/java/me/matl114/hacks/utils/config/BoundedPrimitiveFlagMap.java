package me.matl114.hacks.utils.config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.elements.ButtonElement;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.CodecUtils;
import net.minecraft.text.Text;
import org.apache.commons.lang3.function.TriFunction;

public class BoundedPrimitiveFlagMap<E extends Enum<E>> extends BoundedPrimitiveMap<E, Boolean> {

    public static <S extends Enum<S>, T extends BoundedPrimitiveFlagMap<S>> NBTType<T> createEnumMap(
            String clazzT, Class<S> enumS, TriFunction<List<S>, Map<S, Boolean>, NBTType<Boolean>, T> creator) {
        return create(
                clazzT,
                creator,
                Arrays.asList(enumS.getEnumConstants()),
                CodecUtils.enumCodec(enumS),
                (v, x, y, width, height) -> {
                    int estimateWidth = 180;
                    int startX = (width - estimateWidth) / 2;
                    return ExecutableWidget.instance(x + startX, y, estimateWidth, height)
                            .setElementHandler(
                                    new ButtonElement(TextProvider.of(Text.literal(v.name())), ButtonAction.empty()));
                },
                NBTTypes.BOOLEAN_TYPE,
                250,
                320,
                20);
    }

    public BoundedPrimitiveFlagMap(List<E> keys, Map<E, Boolean> map, NBTType<Boolean> type) {
        super(keys, map, type);
    }

    public BoundedPrimitiveFlagMap(Class<E> clazz) {
        this(Arrays.asList(clazz.getEnumConstants()), Map.of(), NBTTypes.BOOLEAN_TYPE);
    }

    public boolean getState(E element) {
        return map.get(element);
    }
}
