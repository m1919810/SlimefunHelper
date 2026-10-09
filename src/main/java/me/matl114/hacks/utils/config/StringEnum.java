package me.matl114.hacks.utils.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.EnumAttrKeyValue;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;

/** A finite string choice whose value is normalized to its values list. */
public record StringEnum(String value, List<String> values) implements NBTParsable<StringEnum> {
    public StringEnum {
        values = List.copyOf(values == null ? List.of() : values);
        if (values.isEmpty()) {
            value = value == null ? "" : value;
        } else if (!values.contains(value)) {
            value = values.get(0);
        }
    }

    public StringEnum(List<String> values) {
        this(null, values);
    }

    public StringEnum(String value, String... values) {
        this(value, List.of(values));
    }

    public static final Codec<StringEnum> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("value", "").forGetter(StringEnum::value),
                    Codec.STRING
                            .listOf()
                            .optionalFieldOf("values", List.<String>of())
                            .forGetter(StringEnum::values))
            .apply(instance, StringEnum::new));

    public static final NBTType<StringEnum> TYPE = new NBTType<>(
            "stringenum",
            CODEC,
            (factory, source, x, y, dx, dy) -> {
                java.util.LinkedHashMap<String, String> choices = new java.util.LinkedHashMap<>();
                source.get().values().forEach(value -> choices.put(value, value));
                WrapperFactory<String, StringEnum> wrapper = WrapperFactory.of(
                        value -> new StringEnum(value, source.get().values()), StringEnum::value);
                AttrKeyValue<String> converted = new TypeConvertAttrKeyValue<>(
                        source,
                        wrapper,
                        EnumAttrKeyValue.createFiniteLookupWidgetFactory(choices),
                        WrapperFactory.of(value -> value, value -> value));
                if (choices.isEmpty()) {
                    return factory.createInput(converted, x, y, dx, dy);
                }
                return converted.getCustomWidgetFactory().generateWidget(factory, converted, x, y, dx, dy);
            },
            WrapperFactory.of(value -> new StringEnum(value, List.of(value)), StringEnum::value),
            new StringEnum(List.of()));

    @Override
    public NBTType<StringEnum> type() {
        return TYPE;
    }
}
