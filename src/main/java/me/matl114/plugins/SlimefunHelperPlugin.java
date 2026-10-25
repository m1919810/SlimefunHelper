package me.matl114.plugins;

import me.matl114.hacks.api.ModuleGroup;
import me.matl114.managers.config.Config;
import me.matl114.managers.config.ConfigLoader;
import me.matl114.versioned.ModVersion;
import net.fabricmc.loader.api.ModContainer;

/** Entry point implemented by extensions loaded through the {@code slimefunhelper} Fabric entrypoint. */
public interface SlimefunHelperPlugin {
    ModVersion DEFAULT_MINIMUM_VERSION = ModVersion.parse("1.2.1.1");

    /** Called before core configuration is read. Register plugin-owned configs here. */
    public void onConfigBootstrap();

    /** Called after all global listener channels have been initialized. */
    public void onListenerBootstrap();

    /** Called after built-in modules initialize. Register plugin modules here. */
    public void onModuleBootstrap();

    /** Called after all regular bootstrap phases have completed. */
    public void postInitialize();

    /** Returns the mod id that owns this plugin. */
    public String getPluginId();

    /** Returns the minimum SlimefunHelper host version required by this plugin. */
    default ModVersion getMinimumVersion() {
        return DEFAULT_MINIMUM_VERSION;
    }

    default String getId() {
        return getPluginId();
    }

    public ModContainer getContainer();

    /** Registers a plugin module group in the global module registry. */
    default void registerModuleGroup(ModuleGroup group) {
        PluginManager.getInstance().registerModuleGroup(this, group);
    }

    default <T extends Config> T loadRuntimeConfig(String fileName, String customName) {
        return (T) ConfigLoader.loadRuntimeConfig(fileName, customName).markForSave();
    }

    /** Makes a plugin config visible to the global config screen and reload lifecycle. */
    default <T extends Config> T registerConfig(T config) {
        return PluginManager.getInstance().registerConfig(this, config);
    }

    default void onDiscovered(ModContainer container) {}
}
