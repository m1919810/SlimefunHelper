package me.matl114.plugins;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import me.matl114.SlimefunHelper;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.gui.GuiMain;
import me.matl114.hacks.MainTasks;
import me.matl114.jsApi.SlimefunHelperApi;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.command.MainCommand;
import me.matl114.managers.config.Config;
import me.matl114.registry.RegistryBootstrap;
import me.matl114.utils.Debug;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.impl.client.model.loading.ModelLoadingPluginManager;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public final class MainPlugin implements SlimefunHelperPlugin {
    public static final String ID = SlimefunHelper.MOD_ID;
    public final ModContainer container;

    public MainPlugin(ModContainer container) {
        this.container = container;
    }

    public static Identifier identifier(String path) {
        return Identifier.of(ID, path);
    }

    @Override
    public void onConfigBootstrap() {
        Configs.loadConfigs(this);
    }

    @Override
    public void onListenerBootstrap() {
        Listener.init();
        RenderListener.init();
        Tasks.init();
        MainCommand.init();
    }

    @Override
    public void onModuleBootstrap() {
        MainTasks.init();
    }

    @Override
    public void postInitialize() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return identifier("reload_listener");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        Debug.info("Resource reload for SlimefunHelper");
                        Config.reloadAll();
                        RenderListener.onResourceReload(manager);
                    }
                });
        ModelLoadingPluginManager.<Collection<Identifier>>registerPlugin(
                (resourceManager, executor) -> CompletableFuture.supplyAsync(() -> {
                    Config.reloadAll();
                    return Set.of();
                }),
                (PreparableModelLoadingPlugin<Collection<Identifier>>) (data, pluginContext) -> {});
        GuiMain.init();
        RegistryBootstrap.init();
        SlimefunHelperApi.init();
    }

    @Override
    public String getPluginId() {
        return ID;
    }

    @Override
    public ModContainer getContainer() {
        return container;
    }
}
