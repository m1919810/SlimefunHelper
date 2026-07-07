package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.With;
import me.matl114.gui.basic.DynamicContentWidget;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.config.Ref;
import me.matl114.utils.config.PairLikeFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;

@Getter
@With
public class OptionalPrimitive<T> implements NBTParsable<OptionalPrimitive<T>> {
    public static final Class<OptionalPrimitive<Double>> DOUBLE_TYPE = (Class) OptionalPrimitive.class;
    public static final Class<OptionalPrimitive<Integer>> INT_TYPE = (Class) OptionalPrimitive.class;

    public static <T extends ConfigEnum> Class<OptionalPrimitive<WrapEnum<T>>> configEnum(Class<T> clazz) {
        ConfigEnum.ensureRegistered(clazz);
        return (Class) OptionalPrimitive.class;
    }

    public static <T> Class<OptionalPrimitive<T>> type(Class<T> clazz) {
        return (Class) OptionalPrimitive.class;
    }

    boolean present;
    NBTType<T> type;
    T value;

    public OptionalPrimitive(boolean present, NBTType<T> primitive, T value) {
        this.present = present;
        this.type = primitive;
        this.value = value;
    }

    public OptionalPrimitive(boolean present, Primitive<T> primitive) {
        this(present, primitive.valueType(), primitive.value());
    }

    public Primitive<T> getPrimitive() {
        return Primitive.of(this.type, value);
    }

    public boolean test(Predicate<T> predicate) {
        return isPresent() && predicate.test(this.value);
    }

    public boolean positive() {
        if (isPresent()) {
            if (this.value instanceof Double doubleValue) {
                return doubleValue > 1E-7;
            } else if (this.value instanceof Integer integerValue) {
                return integerValue > 0;
            }
        }
        return false;
    }

    public T orElse(T val) {
        return isPresent() ? value : val;
    }

    public static final NBTType<OptionalPrimitive> TYPE = new NBTType<>(
            OptionalPrimitive.class,
            RecordCodecBuilder.create(oInstance -> oInstance
                    .group(
                            Codec.BOOL.fieldOf("present").forGetter(OptionalPrimitive::isPresent),
                            Primitive.TYPE.typeCodec().fieldOf("value").forGetter(OptionalPrimitive::getPrimitive))
                    .apply(oInstance, OptionalPrimitive::new)),
            (s, x, y, dx, dy) -> {
                NBTType type = s.getOriginValue().getType();
                PairLikeFactory<Boolean, ?, OptionalPrimitive> factory = PairLikeFactory.of(
                        (bool, val) -> new OptionalPrimitive(bool, type, val),
                        OptionalPrimitive::isPresent,
                        OptionalPrimitive::getValue);
                SubScreenWidget subScreenWidget = new SubScreenWidget(x, y, dx, dy);
                subScreenWidget.addDrawableChild(new TypeConvertAttrKeyValue<>(
                                s, factory.asFirstWrapper(s::getOriginValue), NBTTypes.BOOLEAN_TYPE)
                        .generateValueWidget(0, 0, dy, dy));

                TypeConvertAttrKeyValue<OptionalPrimitive, ?> valueWidget =
                        new TypeConvertAttrKeyValue<>(s, factory.asSecondWrapper(s::getOriginValue), type);
                var widget = valueWidget.generateValueWidget(dy + 2, 0, dx - dy - 2, dy);
                DynamicContentWidget<?> widget2 = new DynamicContentWidget<>(
                        () -> {
                            if (s.getOriginValue().isPresent()) {
                                return widget;
                            } else {
                                return null;
                            }
                        },
                        0,
                        0);
                subScreenWidget.addDrawableChild(widget2);
                return subScreenWidget;
            },
            new OptionalPrimitive(false, Primitive.TYPE.empty()));

    @Override
    public NBTType<OptionalPrimitive<T>> type() {
        return TYPE.cast();
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return type instanceof OptionalPrimitive<?> optional && this.type == optional.type;
    }

    @Override
    public <W> Optional<OptionalPrimitive<T>> tryTypeConvert(Ref<W> ref) {
        var optional = Primitive.convertPrimitives(ref);
        if (optional.isPresent() && optional.get().valueType() == this.type) {
            return Optional.ofNullable(this.withValue((T) optional.get().value()));
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof OptionalPrimitive<?> pmt
                && (pmt.type == this.type && pmt.present == this.present && Objects.equals(pmt.value, this.value));
    }
}
