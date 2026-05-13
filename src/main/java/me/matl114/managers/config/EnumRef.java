package me.matl114.managers.config;

import com.google.common.base.Preconditions;
import java.util.Objects;
import me.matl114.utils.Debug;
import me.matl114.utils.config.AttrKeyValue;

public class EnumRef<T extends ConfigEnum> extends LazilyRegisterTypeRef<T, String> {
    public static <T extends ConfigEnum> Class<T> parameter(Class<?> enumClass) {
        return (Class<T>) enumClass;
    }

    public EnumRef(ConfigEnum enumR) {
        super(enumR.getConfigEnumType(), (T) enumR);
    }

    public EnumRef(String value) {
        super(value);
    }

    @Override
    protected void tryRegisterType(T value) {
        ConfigEnum.ensureRegistered(value.cast().getClass());
    }

    @Override
    protected String toLazy(T val) {
        return val.cast().name();
    }

    @Override
    protected String fromStringToLazy(String string) {
        return string;
    }

    @Override
    protected String fromLazyToString(String val) {
        return val;
    }

    @Override
    protected String prefix() {
        return "enum";
    }

    protected void tryResolve() {
        if (this.resolved) return;
        var re = ConfigEnum.registeredConfigs.get(enumType);
        if (re == null) {
            this.resolved = false;
            return;
        }
        var val = re.get(enumValue);
        Preconditions.checkNotNull(
                val,
                "Unregistered enum value %s in enum type %s with %s".formatted(enumValue, enumType, re.toString()));
        this.resolved = true;
        this.set((T) val);
    }

    public void setEnumType(Class<? extends Enum> clazz) {
        if (!Objects.equals(enumType, ConfigEnum.getConfigEnumType(clazz))) {
            throw new IllegalArgumentException("Enum type mismatch the class name: " + enumType + " and " + clazz);
        }
        if (!resolved) {
            ConfigEnum.ensureRegistered(clazz);
            tryResolve();
        }
    }

    @Override
    protected T validateAndCast(Object val) {
        if (!resolved) {
            setEnumType((Class<? extends Enum>) val.getClass());
        }
        T configEnum = (T) val;

        Preconditions.checkArgument(Objects.equals(enumType, configEnum.getConfigEnumType()), "Enum type mismatch !");
        return configEnum;
    }

    public static EnumRef<ConfigEnum> fromString(String value) {
        if (value.startsWith("enum:")) {
            try {
                return new EnumRef<>(value);
            } catch (Throwable e) {
                Debug.info("Parse config as Enum Selection failed: ", value, ", Error Message: ", e.getMessage());
            }
        }
        return null;
    }

    @Override
    public AttrKeyValue<T> _createKeyValue0(String key) {
        if (!resolved) {
            tryResolve();
        }
        if (resolved) {
            return (AttrKeyValue<T>)
                    AttrKeyValue.enumMap(key, this.getValue(), this.getValue().getMap());
        } else {
            throw new IllegalStateException("Access to a config enum instance before it is registered");
            // return AttrKeyValue.enumMap(key, null, Map.of());
        }
    }
}
