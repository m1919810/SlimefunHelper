package me.matl114;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.Getter;
import me.matl114.bridge.BridgeMain;
import me.matl114.bukkit.BukkitItemStackUtils;
import me.matl114.bukkit.BukkitSerializationMock;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.gui.GuiMain;
import me.matl114.hacks.Tasks;
import me.matl114.jsApi.SlimefunHelperApi;
import me.matl114.managers.TaskManagers;
import me.matl114.utils.CommonUtils;
import me.matl114.utils.Debug;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.impl.client.model.loading.ModelLoadingPluginManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public class SlimefunHelper implements ModInitializer {
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final String MOD_ID = "slimefunhelper";
    // public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    @Getter
    public static SlimefunHelper instance;

    public static boolean DEV = false;

    public static void authentication() {
        if (Objects.equals(MinecraftClient.getInstance().getSession().getUsername(), "matl114")) {
            DEV = true;
        }
    }

    @Override
    public void onInitialize() {
        instance = this;
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
        authentication();
        Debug.info("SlimefunHelper, start!");
        ModConfig.reloadModConfig();
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return CommonUtils.getNamespaceKey("reload_listener");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        Debug.info("Resource reload called for SlimefunHelper");
                        ModConfig.reloadModConfig();
                        RenderListener.onResourceReload(manager);
                    }
                });
        ModelLoadingPluginManager.<Collection<Identifier>>registerPlugin(
                (resourceManager, executor) -> CompletableFuture.supplyAsync(() -> {
                    Debug.info("check model plugin work");
                    ModConfig.reloadModConfig();
                    // we removed the itemModel auto register to ItemAssetsLoader
                    return Set.of(); // RenderListener.getReloadingResources(resourceManager.getResourceManager());
                }),
                (PreparableModelLoadingPlugin<Collection<Identifier>>) (data, pluginContext) -> {
                    // here we should auto register these to BasicItemModel s or SpecialItemModels
                    //				pluginContext.addModels(data);
                });

        BukkitSerializationMock.init();
        BukkitItemStackUtils.init();

        TaskManagers.init();
        Listener.init();
        RenderListener.init();
        GuiMain.init();
        Tasks.init();
        BridgeMain.init();
        SlimefunHelperApi.init();
    }
    // todo 接下来要做什么
    // todo 已知的冲突:
    // 大饼: 实现指令系统，接入聊天框 !!开头
    // 大饼: 客户端实现/give指令劫持
    // 大饼: 发射器界面实现一键放入+合成(?)+交互合成按钮  有了
    // 大饼: 通过客户端指令listRegisty
    // 大饼 Stats modify
    // todo 大饼 下单系统; 需要实现vanilla walk 模块，拉取baritone api
    // 大病: 新配置体系 有了
    // todo 更多hacks
    // villager trade utils 有了
    // todo slimefun textures to sprites
    // todo: generalize sf id to some nbt path -> id
    // todo: add JsonMapRef , store data as json string
    // todo: add shulker display and shulker preview
    //

    // todo: js dev: tp+ litematica, tp + breakblock

    // todo: breakSystem problem
    // todo: sneak packets
}
