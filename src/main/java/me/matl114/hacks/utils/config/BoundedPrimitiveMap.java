package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.CodecUtils;
import me.matl114.utils.config.WidgetFactory;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.AttrKeyValues;
import me.matl114.utils.config.kv.WrapperAttrKeyValue;
import org.apache.commons.lang3.function.TriFunction;

public abstract class BoundedPrimitiveMap<W, T> {

    protected final Map<W, T> map;

    public BoundedPrimitiveMap(List<W> keys, Map<W, T> map, NBTType<T> type) {
        this.map = new LinkedHashMap<>(map.size());
        for (W string : keys) {
            T val = map.get(string);
            if (val == null) {
                val = type.createEmpty();
            }
            this.map.put(string, val);
        }
    }

    public Map<W, T> toMap() {
        return map;
    }

    public static <S, T, W extends BoundedPrimitiveMap<S, T>> NBTType<W> create(
            Class<W> what,
            TriFunction<List<S>, Map<S, T>, NBTType<T>, W> creator,
            List<S> baseLookup,
            Codec<S> keyCodec,
            WidgetFactory<S> keyWidget,
            NBTType<T> ptype,
            int keyLabelWidth,
            int listWidth,
            int listHeight) {
        WrapperFactory<Map<S, T>, W> wrapper =
                WrapperFactory.of(map -> creator.apply(baseLookup, map, ptype), BoundedPrimitiveMap::toMap);
        WrapperFactory<String, S> keyWrapper = WrapperFactory.fromCodec(keyCodec, JavaOps.INSTANCE);
        return new NBTType<>(
                NBTType.<W>parameter(what),
                CodecUtils.arrayMapCodec(keyCodec, ptype.typeCodec())
                        .xmap((map) -> creator.apply(baseLookup, map, ptype), BoundedPrimitiveMap::toMap),
                (w, x, y, dx, dy) -> NBTTypes.generateBoundedListModifyButton(
                        new WrapperAttrKeyValue<>(w, wrapper),
                        baseLookup,
                        ptype,
                        keyWidget,
                        x,
                        y,
                        dx,
                        dy,
                        keyLabelWidth,
                        listWidth,
                        listHeight),
                AttrKeyValues.STR_MAP_FACTORY
                        .concat(WrapperFactory.map(keyWrapper, ptype.stringifyFactory()))
                        .concat(wrapper),
                creator.apply(baseLookup, Map.of(), ptype));
    }
}
