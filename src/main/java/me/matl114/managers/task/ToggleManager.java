package me.matl114.managers.task;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.managers.config.FlagRef;
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

    public void register(String value, FlagRef flagRef);

    public FlagRef getFlag(String value);

    public FlagRef getOrRegister(String value, boolean defaultValue);

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
            return toggle == FALSE ? () -> {} : HotKeyUtils.wrapFlagAsToggle(value, toggle);
        }

        @Override
        public void register(String value, boolean defaultValue) {
            register(value, new FlagRef(defaultValue));
        }

        public Map<String, Runnable> getTasks() {
            LinkedHashMap<String, Runnable> toggles = new LinkedHashMap<>();
            for (String toggle : this.flags.keySet()) {
                toggles.put(toggle, getToggle(toggle));
            }
            return toggles;
        }

        public void register(String value, FlagRef flagRef) {
            this.flags.put(value, flagRef);
        }

        @Override
        public FlagRef getFlag(String value) {
            return this.flags.get(value);
        }

        @Override
        public FlagRef getOrRegister(String value, boolean defaultValue) {
            return this.flags.computeIfAbsent(value, (s) -> new FlagRef(defaultValue));
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
}
