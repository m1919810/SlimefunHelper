package me.matl114.managers.task;

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import me.matl114.managers.Configs;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.Debug;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

public interface ToggleManager extends TaskManager {
    public static ToggleManager of() {
        final HashMap<String, FlagRef> toggles = new LinkedHashMap<>();
        return new ToggleManagerImpl(toggles);
    }

    public static ToggleManager of(HashMap<String, FlagRef> map) {
        return new ToggleManagerImpl(map);
    }

    public boolean getState(String value);

    public Runnable getToggle(String value);

    public void register(String value, boolean defaultValue);

    public FlagRef getFlag(String value);

    public FlagRef getOrRegister(String value, boolean defaultValue);

    public static Runnable wrapFlagAsToggle(String path, FlagRef flagRef) {
        return () -> {
            boolean result = !flagRef.get();
            flagRef.set(result);
            Configs.TOGGLE_CONFIG.markForSave();
            Debug.chat("Toggle", Text.translatableWithFallback(path, path), (result ? "on" : "off"));
        };
    }

    public static class ToggleManagerImpl implements ToggleManager {
        Map<String, FlagRef> flags;

        public ToggleManagerImpl() {
            this.flags = new LinkedHashMap<>();
        }

        public ToggleManagerImpl(Map<String, FlagRef> flags) {
            this.flags = flags;
        }

        static FlagRef FALSE = new FlagRef(false);

        public boolean getState(String value) {

            return this.flags.getOrDefault(value, FALSE).get();
        }

        public Runnable getToggle(String value) {
            final FlagRef toggle = this.flags.getOrDefault(value, FALSE);
            return toggle == FALSE ? () -> {} : wrapFlagAsToggle(value, toggle);
        }

        public Map<String, Runnable> getTasks() {
            LinkedHashMap<String, Runnable> toggles = new LinkedHashMap<>();
            for (String toggle : this.flags.keySet()) {
                toggles.put(toggle, getToggle(toggle));
            }
            return toggles;
        }

        public void register(String value, boolean defaultValue) {
            Preconditions.checkArgument(!this.flags.containsKey(value));

            this.flags.put(value, getToggleFlag(value, defaultValue));
        }

        @Override
        public FlagRef getFlag(String value) {
            return this.flags.get(value);
        }

        @Override
        public FlagRef getOrRegister(String value, boolean defaultValue) {
            return this.flags.computeIfAbsent(value, (s) -> getToggleFlag(value, defaultValue));
        }

        @NotNull
        @Override
        public Runnable getTask(String value) {
            return getToggle(value);
        }

        @Override
        public void register(String value, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Runnable getOrRegister(String value, Runnable task) {
            throw new UnsupportedOperationException();
        }
    }

    public static FlagRef getToggleFlag(String value, boolean defaultValue) {
        String[] path = Config.cutToPath(value);
        FlagRef toggle = Configs.TOGGLE_CONFIG.getBoolean(path);
        if (toggle == null) {
            Configs.TOGGLE_CONFIG
                    .builder(Boolean.class)
                    .path(path)
                    .defaultValue(defaultValue)
                    .build();
            toggle = Configs.TOGGLE_CONFIG.getBoolean(path);
            Preconditions.checkNotNull(toggle);
        }
        return toggle;
    }
}
