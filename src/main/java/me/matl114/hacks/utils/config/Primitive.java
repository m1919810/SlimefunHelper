package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Optional;
import javax.annotation.Nonnull;
import me.matl114.managers.config.*;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;

public record Primitive<T>(NBTType<T> valueType, @Nonnull T value, String valueString)
        implements NBTParsable<Primitive<T>> {
    public static final String SPLITTER = "|";

    public static <T> Primitive<T> of(NBTType<T> valueType, T value) {
        return new Primitive<T>(valueType, value, valueType.stringifyFactory().get(value));
    }

    public static <T> DataResult<Primitive<T>> parse(String va) {
        int idx = va.indexOf(SPLITTER);
        String type = va.substring(0, idx);
        NBTType<T> lookup = NBTTypes.primitiveTypes(type);
        if (lookup == null) {
            return DataResult.error(() -> "No such primitive type: " + type);
        } else {
            String value = va.substring(idx + 1);
            try {
                T val = lookup.stringifyFactory().create(value);
                return DataResult.success(new Primitive<>(lookup, val, value));
            } catch (Throwable e) {
                return DataResult.error(() -> "Error parsing primitive value: " + va);
            }
        }
    }

    public String asString() {
        return valueType.typeName() + SPLITTER + valueString;
    }

    private static <T> NBTType<Primitive<T>> create() {
        return new NBTType(
                NBTType.<Primitive<T>>parameter(Primitive.class),
                Codec.STRING.<Primitive<T>>comapFlatMap(Primitive::<T>parse, Primitive::asString),
                (AttrKeyValue.CustomWidgetFactory<Primitive<T>>) (w, x, y, dx, dy) -> {
                    Primitive<T> primitive = w.getOriginValue();
                    NBTType<T> typeT = primitive.valueType;
                    return new TypeConvertAttrKeyValue<>(
                                    w,
                                    WrapperFactory.<T, Primitive<T>>of(s -> Primitive.of(typeT, s), Primitive::value),
                                    typeT)
                            .generateValueWidget(x, y, dx, dy);
                },
                WrapperFactory.<String, Primitive<T>>of(
                        (s) -> Primitive.<T>parse(s).getOrThrow(), Primitive::asString),
                Primitive.of(NBTTypes.STRING_TYPE, ""));
    }

    public static final NBTType<Primitive<Object>> TYPE = create();

    @Override
    public NBTType<Primitive<T>> type() {
        return TYPE.cast();
    }

    @Override
    public boolean isSameType(NBTParsable<?> type) {
        return NBTParsable.super.isSameType(type)
                && type instanceof Primitive<?> primitive
                && valueType == primitive.valueType;
    }

    @Override
    public <W> Optional<Primitive<T>> tryTypeConvert(Ref<W> ref) {
        return (Optional) convertPrimitives(ref);
    }

    public static Optional<Primitive<?>> convertPrimitives(Ref<?> ref) {
        if (ref instanceof FlagRef flag) {
            return Optional.of(Primitive.of(NBTTypes.BOOLEAN_TYPE, flag.get()));
        } else if (ref instanceof IntRef intRef) {
            return Optional.of(Primitive.of(NBTTypes.INT_TYPE, intRef.get()));
        } else if (ref instanceof LongRef longRef) {
            return Optional.of(Primitive.of(NBTTypes.LONG_TYPE, longRef.get()));
        } else if (ref instanceof DoubleRef doubleRef) {
            return Optional.of(Primitive.of(NBTTypes.DOUBLE_TYPE, doubleRef.get()));
        } else if (ref instanceof StringRef strRef) {
            return Optional.of(Primitive.of(NBTTypes.STRING_TYPE, strRef.get()));
        } else if (ref instanceof KeyBindRef keyBindRef) {
            return Optional.of(Primitive.of(NBTTypes.KEY_BIND_TYPE, keyBindRef.get()));
        } else if (ref instanceof EnumRef enumRef) {
            return Optional.of(Primitive.of(NBTTypes.CONFIG_ENUM_TYPE, new WrapEnum<>(enumRef)));
        }
        return Optional.empty();
    }
}
