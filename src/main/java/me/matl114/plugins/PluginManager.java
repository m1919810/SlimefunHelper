package me.matl114.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.modules.HackModules;
import me.matl114.managers.config.Config;
import me.matl114.utils.Debug;
import me.matl114.versioned.ModVersion;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

/** Discovers and initializes SlimefunHelper plugins registered through Fabric. */
public final class PluginManager {
    public static final String ENTRYPOINT = "slimefunhelper";

    public enum BootstrapPhase {
        NOT_STARTED("starting"),
        CONFIG_READ("configuration read"),
        LISTENERS("global listener channels"),
        MODULES("module initialization"),
        OTHER("other systems"),
        COMPLETE("complete");

        private final String displayName;

        BootstrapPhase(String displayName) {
            this.displayName = displayName;
        }
    }

    private static final PluginManager INSTANCE = new PluginManager();

    private final List<SlimefunHelperPlugin> plugins = new ArrayList<>();
    private boolean begun;
    private boolean discovered;
    private BootstrapPhase bootstrapPhase = BootstrapPhase.NOT_STARTED;

    private PluginManager() {}

    public static PluginManager getInstance() {
        return INSTANCE;
    }

    /** Starts bootstrap and adds the host plugin before any discovered extensions. */
    public void begin(SlimefunHelperPlugin builtin) {
        if (begun) {
            throw new IllegalStateException("Plugin bootstrap has already started.");
        }
        SlimefunHelperPlugin plugin = Objects.requireNonNull(builtin, "builtin");
        plugins.add(plugin);
        begun = true;
    }

    /** Discovers and eagerly creates extension plugins after the built-in plugin has been added. */
    public void discover() {
        if (!begun) {
            throw new IllegalStateException("Plugin bootstrap has not started.");
        }
        if (discovered) {
            throw new IllegalStateException("Plugin extensions have already been discovered.");
        }
        discoverExtensions();
        discovered = true;
    }

    /** Runs the next startup phase and invokes the plugin hooks assigned to it. */
    public void advance(BootstrapPhase nextPhase) {
        Objects.requireNonNull(nextPhase, "nextPhase");
        if (!begun || !discovered) {
            throw new IllegalStateException("Plugin bootstrap has not started.");
        }
        if (nextPhase.ordinal() != bootstrapPhase.ordinal() + 1 || nextPhase == BootstrapPhase.COMPLETE) {
            throw new IllegalStateException(
                    "Invalid bootstrap transition: %s -> %s".formatted(bootstrapPhase, nextPhase));
        }

        switch (nextPhase) {
            case CONFIG_READ -> invokePlugins(SlimefunHelperPlugin::onConfigBootstrap, nextPhase);
            case LISTENERS -> invokePlugins(SlimefunHelperPlugin::onListenerBootstrap, nextPhase);
            case MODULES -> invokePlugins(SlimefunHelperPlugin::onModuleBootstrap, nextPhase);
            case OTHER -> invokePlugins(SlimefunHelperPlugin::postInitialize, nextPhase);
        }
        bootstrapPhase = nextPhase;
    }

    public void complete() {
        bootstrapPhase = BootstrapPhase.COMPLETE;
    }

    /**
     * Discovers plugin entrypoints once. Calling {@code getEntrypoint()} here eagerly creates every
     * SlimefunHelper plugin before any bootstrap callback runs; it does not instantiate unrelated Fabric
     * {@code main} entrypoints.
     */
    private void discoverExtensions() {
        ModVersion hostVersion = getHostVersion();
        for (EntrypointContainer<SlimefunHelperPlugin> entrypoint :
                FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT, SlimefunHelperPlugin.class)) {
            ModMetadata metadata = entrypoint.getProvider().getMetadata();
            SlimefunHelperPlugin plugin;
            try {
                plugin = entrypoint.getEntrypoint();
            } catch (Throwable throwable) {
                throw new RuntimeException(
                        "Exception during plugin init \"%s\".".formatted(metadata.getName()), throwable);
            }
            if (plugin == null) {
                throw new IllegalStateException(
                        "Plugin entrypoint \"%s\" returned null.".formatted(metadata.getName()));
            }
            ModVersion minimumVersion = Objects.requireNonNull(
                    plugin.getMinimumVersion(), "Plugin minimum version cannot be null: " + metadata.getName());
            if (!hostVersion.higherOrEqual(minimumVersion)) {
                throw new IllegalStateException(
                        "Plugin \"%s\" requires SlimefunHelper %s or newer, but the current version is %s."
                                .formatted(metadata.getName(), minimumVersion, hostVersion));
            }
            plugin.onDiscovered(entrypoint.getProvider());
            plugins.add(plugin);
        }
    }

    private ModVersion getHostVersion() {
        String version =
                plugins.get(0).getContainer().getMetadata().getVersion().getFriendlyString();
        return ModVersion.parseTrailing(version);
    }

    public List<SlimefunHelperPlugin> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    public ModuleGroup registerModuleGroup(SlimefunHelperPlugin plugin, ModuleGroup group) {
        HackModules.registerModuleGroup(Objects.requireNonNull(group, "group"));
        return group;
    }

    public <T extends Config> T registerConfig(SlimefunHelperPlugin plugin, T config) {
        config.registerGlobal(plugin);
        return config;
    }

    private void invokePlugins(Consumer<SlimefunHelperPlugin> callback, BootstrapPhase phase) {
        for (SlimefunHelperPlugin plugin : plugins) {
            invoke(plugin, phase, () -> callback.accept(plugin));
        }
    }

    private void invoke(SlimefunHelperPlugin plugin, BootstrapPhase phase, Runnable callback) {
        try {
            callback.run();
        } catch (Throwable throwable) {
            Debug.info("Plugin %s failed during %s".formatted(plugin.getPluginId(), phase.displayName), throwable);
            throw new RuntimeException(
                    "Plugin \"%s\" failed during %s.".formatted(plugin.getPluginId(), phase.displayName), throwable);
        }
    }
}
