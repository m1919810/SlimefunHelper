package me.matl114.hacks.utils.config;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.NBTParsable;
import me.matl114.managers.config.NBTType;
import me.matl114.utils.config.AttrKeyValue;
import me.matl114.utils.config.WrapperFactory;
import me.matl114.utils.config.kv.EnumAttrKeyValue;
import me.matl114.utils.config.kv.TypeConvertAttrKeyValue;

public class WrapEnum<T extends ConfigEnum> implements NBTParsable<WrapEnum<T>> {
    public String type;
    public String valueString;
    public T value;
    public boolean resolved;

    private WrapEnum() {}

    public WrapEnum(T configEnum) {
        ConfigEnum.ensureRegistered(configEnum.getClass());
        this.value = configEnum;
        this.type = configEnum.getConfigEnumType();
        this.valueString = configEnum.cast().name();
        this.resolved = true;
    }

    public WrapEnum(String strstr) {
        try {
            EnumRef<?> ref = new EnumRef<>(strstr);
            this.type = ref.enumType;
            this.valueString = ref.enumValue;
            this.resolved = ref.resolved;
            if (this.resolved) {
                this.value = (T) ref.get();
            }
        } catch (Throwable e) {
            this.type = null;
            this.value = null;
            this.valueString = null;
            this.resolved = false;
        }
    }

    public WrapEnum(EnumRef<?> ref) {
        this.type = ref.enumType;
        this.valueString = ref.enumValue;
        this.resolved = ref.resolved;
        if (this.resolved) {
            this.value = (T) ref.get();
        }
    }

    public String asString() {
        return "enum:" + type + ":" + valueString;
    }

    public static DataResult<WrapEnum<?>> fromString(String str) {
        var re = new WrapEnum<>(str);
        if (re.type != null && re.valueString != null) {
            return DataResult.success(re);
        } else {
            return DataResult.error(() -> "Can not parse ConfigEnum type: " + str);
        }
    }

    public T get() {
        if (!this.resolved) {
            tryResolve();
        }
        return this.value;
    }

    protected void tryResolve() {
        if (this.resolved) return;
        var re = ConfigEnum.registeredConfigs.get(this.type);
        if (re == null) {
            this.resolved = false;
            return;
        }
        var val = re.get(this.valueString);
        Preconditions.checkNotNull(
                val,
                "Unregistered enum value %s in enum type %s with %s".formatted(valueString, this.type, re.toString()));
        this.resolved = true;
        this.value = (T) val;
    }

    public static final NBTType<WrapEnum> TYPE = create().cast();

    private static <T extends ConfigEnum> NBTType<WrapEnum<T>> create() {
        return new NBTType<WrapEnum<T>>(
                "wrapenum",
                (Codec) Codec.STRING.comapFlatMap(WrapEnum::fromString, WrapEnum::asString),
                (s, x, y, dx, dy) -> {
                    WrapEnum<T> wrapEnum = s.getOriginValue();
                    if (!wrapEnum.resolved) {
                        wrapEnum.tryResolve();
                    }
                    if (wrapEnum.resolved) {
                        Map<String, T> configEnumType = (Map) wrapEnum.value.getMap();
                        Map<T, String> inversedMap = configEnumType.entrySet().stream()
                                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
                        WrapperFactory<String, T> factory = WrapperFactory.of(configEnumType::get, inversedMap::get);
                        return EnumAttrKeyValue.createFiniteLookupWidgetFactory(configEnumType)
                                .generateWidget(
                                        new TypeConvertAttrKeyValue<WrapEnum<T>, T>(
                                                (AttrKeyValue) s,
                                                WrapperFactory.of(WrapEnum::new, WrapEnum::get),
                                                null,
                                                factory),
                                        x,
                                        y,
                                        dx,
                                        dy);

                    } else {
                        throw new IllegalStateException("Access to a config enum instance before it is registered");
                    }
                },
                WrapperFactory.of(WrapEnum::new, WrapEnum::asString),
                new WrapEnum<>());
    }

    @Override
    public NBTType<WrapEnum<T>> type() {
        return TYPE.cast();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof WrapEnum wrap)) {
            return false;
        }
        return Objects.equals(wrap.type, type) && Objects.equals(wrap.valueString, valueString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, valueString);
    }
}
