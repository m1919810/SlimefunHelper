package me.matl114;

import lombok.Getter;
import me.matl114.bridge.BridgeMain;
import me.matl114.bukkitUtiils.BukkitSerializationMock;
import me.matl114.bukkitUtiils.ItemStackHelper;
import me.matl114.hackUtils.Tasks;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.HotKeys;
import me.matl114.utils.Debug;
import me.matl114.renders.SlimefunCustomModelManager;
import me.matl114.renders.RenderMain;
import me.matl114.utils.Utils;
import net.fabricmc.api.ModInitializer;


import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import net.fabricmc.fabric.impl.client.model.loading.ModelLoaderHooks;
import net.fabricmc.fabric.impl.client.model.loading.ModelLoadingPluginManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.data.client.BlockStateModelGenerator;
import net.minecraft.data.client.ItemModelGenerator;
import net.minecraft.data.client.ModelProvider;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;


public class SlimefunHelper implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final String MOD_ID = "slimefunhelper";
    //public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	@Getter
    public static SlimefunHelper instance;
    public static final boolean HACK_VERSION = true;
	public static boolean DEV = false;
	public static void authentication(){
		Debug.info(MinecraftClient.getInstance().getSession().getUsername());
		if(Objects.equals( MinecraftClient.getInstance().getSession().getUsername(),"matl114")){
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
		Debug.info("Slimefun, start!");
		ModConfig.reloadModConfig();
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Utils.getNamespaceKey("reload_listener");
			}
			@Override
			public void reload(ResourceManager manager) {
				Debug.info("reload called");
				ModConfig.reloadModConfig();
				SlimefunCustomModelManager.init();
				if(ModConfig.isEnableSlimefunCmdOverride()){
					Debug.info("Reloading SlimefunHelper resources");
					SlimefunCustomModelManager.loadCustomModelDatas();
				}
			}
		});
//		ModelLoadingPluginManager.registerPlugin(new ModelLoadingPlugin() {
//			@Override
//			public void onInitializeModelLoader(Context pluginContext) {
//				ModConfig.reloadModConfig();
//				if(ModConfig.isEnableItemModelOvevrride()) {
//					Debug.info("Force Load Model enabled");
//					//pluginContext.addModels(new Identifier("networks","ntw_grid"));
//					pluginContext.addModels(SlimefunItemModelManager.walkThroughResourcePacks(MinecraftClient.getInstance().getResourceManager()));
//				}
//			}
//		});
		ModelLoadingPluginManager.<Collection<Identifier>>registerPlugin(
            (resourceManager, executor) -> CompletableFuture.supplyAsync(()->{
				Debug.info("check plugin work");
				Debug.info("is it a reload?");
				ModConfig.reloadModConfig();
				if(ModConfig.isEnableItemModelOvevrride()) {
					Debug.info("Force Load Model enabled");
					//pluginContext.addModels(new Identifier("networks","ntw_grid"));
					return SlimefunCustomModelManager.walkThroughResourcePacks(resourceManager,true);
				}else{
					return SlimefunCustomModelManager.walkThroughResourcePacks(resourceManager,false);
				}
			}),
            (PreparableModelLoadingPlugin<Collection<Identifier>>) (data, pluginContext) -> {
				pluginContext.addModels(data);
            }

        );

		BukkitSerializationMock.init();
		Debug.info("loading bukkitMock!");
		ItemStackHelper.init();
		RenderMain.init();
		HotKeys.init();
		Tasks.init();
		Listener.init();
		BridgeMain.init();
	}
	//todo 接下来要做什么
	//todo 已知的冲突:
	//大饼: 实现指令系统，接入聊天框 !!开头
	//大饼: 客户端实现/give指令劫持
	//大饼: 发射器界面实现一键放入+合成(?)+交互合成按钮  有了
	//大饼: 通过客户端指令listRegisty
	//大饼 Stats modify
	//todo 大饼 下单系统; 需要实现vanilla walk 模块，拉取baritone api
	//大病: 新配置体系 有了
	//todo 更多hacks
	//villager trade utils 有了
	//todo slimefun textures to sprites
	//
}