package me.matl114.hooks;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import me.matl114.utils.config.ValueAccessor;

public abstract class BaritoneHooks implements IHooks {

    private static BaritoneHooks instance;

    public static BaritoneHooks getInstance() {
        if (instance == null) {
            try {
                instance = new Impl();
            } catch (Throwable e) {
                instance = new Default();
            }
        }
        return instance;
    }

    public abstract <T> ValueAccessor<T> getSetting(String name);

    public static class Impl extends BaritoneHooks {
        Settings settings;
        Map<String, ValueAccessor<?>> settingsMap = new LinkedHashMap<>();

        public Impl() {
            settings = BaritoneAPI.getSettings();
            buildMap();
        }

        private void buildMap() {
            for (Settings.Setting re : settings.allSettings) {
                String nameLowerCase = re.getName().toLowerCase();
                ValueAccessor accessor = ValueAccessor.of(() -> re.value, (va) -> re.value = va);
                settingsMap.put(nameLowerCase, accessor);
            }
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public <T> ValueAccessor<T> getSetting(String name) {
            return (ValueAccessor<T>) settingsMap.get(name.toLowerCase(Locale.ROOT));
        }
    }

    public static class Default extends BaritoneHooks {

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public <T> ValueAccessor<T> getSetting(String name) {
            return null;
        }
    }
}
