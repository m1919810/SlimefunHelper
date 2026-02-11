package me.matl114.managers.config;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import me.matl114.api.Displayable;
import net.minecraft.util.StringIdentifiable;

public interface ConfigEnum extends StringIdentifiable, Displayable {
    public static Map<String, Map<String, ConfigEnum>> registeredConfigs = new HashMap<>();

    static void register(Class<? extends Enum> configEnum) {
        Map<String, ConfigEnum> maps = new LinkedHashMap<>();
        for (var e : configEnum.getEnumConstants()) {
            maps.put(e.name(), (ConfigEnum) e);
        }
        registeredConfigs.put(configEnum.getSimpleName().toLowerCase(Locale.ROOT), maps);
    }

    static void ensureRegistered(Class<? extends Enum> configEnum) {
        if (!registeredConfigs.containsKey(configEnum.getSimpleName().toLowerCase(Locale.ROOT))) {
            register(configEnum);
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
}
