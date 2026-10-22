package me.matl114.managers.config;

import com.mojang.serialization.Lifecycle;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import me.matl114.managers.ScheduleService;
import me.matl114.plugins.MainPlugin;
import me.matl114.plugins.SlimefunHelperPlugin;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntryInfo;

public interface Config extends RefMap {
    static final Logger logger = Logger.getLogger(MainPlugin.ID);

    default Config registerAutoSave() {
        Holder.autoSaveConfigs.add(this);
        return this;
    }

    default void registerGlobal(SlimefunHelperPlugin plugin) {
        var reKey = this.getRegistryKey();
        if (reKey != null) {
            REGISTRY.add(reKey, this, RegistryEntryInfo.DEFAULT);
        }
    }
    // names and keys
    @Nullable
    public RegistryKey<Config> getRegistryKey();

    public String getTranslationKey();

    public String getConfigName();

    // dirty flag
    public Config markForSave();

    public boolean isMarkForSave();
    // save operation
    public void save();
    // reload operation
    public void reload();

    // get the definition order
    public Set<String> getVisiblePaths();
    // pre define a path
    public void visitPath(String path);

    // get or create a node,
    public <T> Ref<T> getOrCreate(Ref<T> defaultValue, @Nonnull String... path);

    public <T> boolean setValue(T value, @Nonnull String... path);

    // getters

    @Nullable
    default StringRef getString(@Nonnull String... path) {
        Object node = get(path);
        if (node instanceof StringRef ref) {
            return ref;
        } else {

            return null;
        }
    }

    default KeyBindRef getKeyBind(String... path) {
        Object node = get(path);
        if (node instanceof KeyBindRef ref) {
            return ref;
        } else {

            return null;
        }
    }

    default ListRef getList(String... path) {
        Object node = get(path);
        if (node instanceof ListRef ref) {
            return ref;
        } else {
            return null;
        }
    }

    default <T extends ConfigEnum> EnumRef<T> getEnum(@Nonnull String... path) {
        Object node = get(path);
        if (node instanceof EnumRef<?> enumRef) {
            return (EnumRef<T>) enumRef;
        } else {
            return null;
        }
    }

    default IntRef getInt(@Nonnull String... path) {
        Object node = get(path);
        if (node instanceof IntRef ref) {
            return ref;
        } else {
            return null;
        }
    }

    default DoubleRef getDouble(String... path) {
        Object node = get(path);
        if (node instanceof DoubleRef ref) {
            return ref;
        } else {
            return null;
        }
    }

    default FlagRef getBoolean(@Nonnull String... path) {
        Object node = get(path);
        if (node instanceof FlagRef ref) {
            return ref;
        } else {
            return null;
        }
    }

    public static final SimpleRegistry<Config> REGISTRY =
            new SimpleRegistry<>(RegistryKey.ofRegistry(MainPlugin.identifier("configs")), Lifecycle.stable());

    public static void launchSaveTasks() {
        ScheduleService.launchAsyncDelayedTask(Config.Holder::configSaveTasks, 1000);
    }

    public static void reloadAll() {
        Holder.reloadAll();
    }

    public static String[] cutToPath(String rawPath) {
        return rawPath.split("\\.");
    }

    static class Holder {
        @Getter
        private static final Set<Config> autoSaveConfigs = new LinkedHashSet<>();

        private static final long SAVE_TIME = 1000 * 15;

        static {
            ScheduleService.launchAsyncRepeatTask(Config.Holder::configSaveTasks, SAVE_TIME, SAVE_TIME);
            Runtime.getRuntime().addShutdownHook(new Thread(Config.Holder::configSaveTasks, "Config-Shutdown-Save"));
        }

        static void configSaveTasks() {
            for (var config : autoSaveConfigs) {
                config.save();
            }
        }

        static void reloadAll() {
            autoSaveConfigs.forEach(v -> {
                // only reload the not dirty configs
                // and now trigger save
                if (v.isMarkForSave()) {
                    v.save();
                } else {
                    // may be ...
                    v.reload();
                }
            });
        }
    }
}
