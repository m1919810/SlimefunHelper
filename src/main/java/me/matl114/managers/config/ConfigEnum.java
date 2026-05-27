package me.matl114.managers.config;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import me.matl114.api.Displayable;
import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;

public interface ConfigEnum extends StringIdentifiable, Displayable, AutoRegisterType {
    public static Map<String, Map<String, ConfigEnum>> registeredConfigs = new HashMap<>();
    public static Map<String, Class<? extends ConfigEnum>> registeredEnumsClasses = new HashMap<>();

    static void register(String type, Class<? extends Enum> configEnum) {
        if (registeredEnumsClasses.containsKey(type)) {
            throw new IllegalArgumentException("Duplicate config enum name: " + type);
        }
        registeredEnumsClasses.put(type, (Class) configEnum);
        Map<String, ConfigEnum> maps = new LinkedHashMap<>();
        for (var e : configEnum.getEnumConstants()) {
            maps.put(e.name(), (ConfigEnum) e);
        }
        registeredConfigs.put(type, maps);
    }

    static String getConfigEnumType(Class<? extends Enum> configEnum) {
        if (configEnum.getEnumConstants().length == 0) {
            return configEnum.getSimpleName().toLowerCase(Locale.ROOT);
        } else {
            return ((ConfigEnum) configEnum.getEnumConstants()[0]).getConfigEnumType();
        }
    }

    static void ensureRegistered(Class<? extends Enum> configEnum) {
        String configTypeName = getConfigEnumType(configEnum);
        if (!registeredConfigs.containsKey(configTypeName)) {
            register(configTypeName, configEnum);
        }
    }
    //        public Text getDisplay();
    default Enum cast() {
        return (Enum) this;
    }

    default String getConfigEnumType() {
        return this.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    default String asString() {
        return "enum:" + getConfigEnumType() + ":" + cast().name();
    }

    default Map<String, ConfigEnum> getMap() {
        return registeredConfigs.get(this.getConfigEnumType());
    }

    static void onLoad(Class<?> clazz) {
        if (ConfigEnum.class.isAssignableFrom(clazz) && Enum.class.isAssignableFrom(clazz)) {
            ConfigEnum.ensureRegistered((Class<? extends Enum>) clazz);
        }
    }

    default Text getDisplay() {
        return Text.translatable("configenum." + this.getConfigEnumType().replace("_", "-") + "."
                + cast().name().toLowerCase(Locale.ROOT));
    }

    default boolean isIn(ConfigEnum... e) {
        for (var re : e) {
            if (re == this) return true;
        }
        return false;
    }

    default boolean isNotIn(ConfigEnum... e) {
        for (var re : e) {
            if (re == this) return false;
        }
        return true;
    }
}
