package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.DynamicContentWidget;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.managers.config.*;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;

@Getter
public class DispatchData<T extends ConfigEnum> implements NBTParsable<DispatchData<T>> {
    WrapEnum<T> enumType;
    Map<String, RecordData> dispatchMap;

    public DispatchData(T currentValue, Map<T, RecordData> dispatchMap) {
        this(new WrapEnum<>(currentValue), ensureFullFilled((Class<T>) currentValue.getClass(), dispatchMap));
    }

    private static <T extends ConfigEnum> Map<String, RecordData> ensureFullFilled(
            Class<T> clazzType, Map<T, RecordData> map) {
        Map<String, RecordData> data = new LinkedHashMap<>();
        for (var re : clazzType.getEnumConstants()) {
            RecordData data2 = map.get(re);
            String name = re.cast().name();
            data.put(name, data2 == null ? new RecordData(Map.of()) : data2);
        }
        return data;
    }

    private DispatchData(WrapEnum<T> enumType, Map<String, RecordData> dispatchMap) {
        this.enumType = enumType;
        this.dispatchMap = new LinkedHashMap<>(dispatchMap);
    }

    public DispatchData<T> withDispatchData(T type, RecordData data) {
        Map<String, RecordData> newMap = new LinkedHashMap<>(dispatchMap);
        newMap.put(type.cast().name(), data);
        return new DispatchData<>(enumType, newMap);
    }

    public DispatchData<T> withSelection(T type) {
        return new DispatchData<>(new WrapEnum<>(type), dispatchMap);
    }

    private DispatchData<T> withSelection(WrapEnum<T> type) {
        return new DispatchData<>(type, dispatchMap);
    }

    public RecordData getDispatch(T value) {
        return dispatchMap.get(value.cast().name());
    }

    public RecordData getDispatch() {
        return getDispatch(this.enumType.get());
    }

    public T getType() {
        return this.enumType.get();
    }

    private static <T extends ConfigEnum> NBTType<DispatchData<T>> create() {
        return new NBTType<>(
                "dispatchdata",
                RecordCodecBuilder.<DispatchData<T>>create(oInstance -> oInstance
                        .group(
                                WrapEnum.TYPE
                                        .<WrapEnum<T>>cast()
                                        .typeCodec()
                                        .fieldOf("sample")
                                        .forGetter(DispatchData::getEnumType),
                                Codec.unboundedMap(Codec.STRING, RecordData.TYPE.typeCodec())
                                        .fieldOf("data")
                                        .forGetter(DispatchData::getDispatchMap))
                        .apply(oInstance, DispatchData::new)),
                (custom, x, y, dx, dy) -> {
                    SubScreenWidget currentWidget = new SubScreenWidget(x, y, dx, dy);
                    TypeConvertAttrKeyValue<DispatchData<T>, WrapEnum<T>> typeConvert = new TypeConvertAttrKeyValue<>(
                            custom,
                            WrapperFactory.of(
                                    (s) -> custom.getOriginValue().withSelection(s), DispatchData::getEnumType),
                            WrapEnum.TYPE.cast());
                    currentWidget.addDrawableChild(typeConvert.generateValueWidget(0, 0, dx / 2, dy));
                    Map<T, DrawableWidget> cacheMap = new HashMap<>();
                    DynamicContentWidget<DrawableWidget> dynamic = new DynamicContentWidget<>(
                            () -> {
                                return cacheMap.computeIfAbsent(
                                        custom.getOriginValue().getEnumType().get(), (v) -> {
                                            TypeConvertAttrKeyValue<DispatchData<T>, RecordData> typeConvert2 =
                                                    new TypeConvertAttrKeyValue<>(
                                                            custom,
                                                            WrapperFactory.of(
                                                                    (s) -> custom.getOriginValue()
                                                                            .withDispatchData(v, s),
                                                                    (s) -> s.getDispatch(v)),
                                                            RecordData.TYPE);
                                            return typeConvert2.generateValueWidget(0, 0, dx / 2, dy);
                                        });
                            },
                            dx / 2 + 1,
                            0);
                    currentWidget.addDrawableChild(dynamic);
                    return currentWidget;
                },
                new DispatchData<>(WrapEnum.TYPE.empty(), Map.of()));
    }

    public static final NBTType<DispatchData<?>> TYPE = create().cast();

    @Override
    public NBTType<DispatchData<T>> type() {
        return TYPE.cast();
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return NBTParsable.super.isSameType(type)
                && type instanceof DispatchData<?> data
                && data.enumType.isSameType(enumType);
    }

    @Override
    public boolean equals(Object obj) {
        return (this == obj)
                || (obj instanceof DispatchData<?> data
                        && Objects.equals(this.enumType, data.enumType)
                        && Objects.equals(data.dispatchMap, dispatchMap));
    }

    @Override
    public int hashCode() {
        return Objects.hash(enumType, dispatchMap);
    }
}
