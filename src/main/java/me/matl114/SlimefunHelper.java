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

import net.fabricmc.fabric.api.client.model.ExtraModelProvider;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.function.Consumer;


public class SlimefunHelper implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final String MOD_ID = "slimefunhelper";
    //public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	@Getter
    public static SlimefunHelper instance;
    public static final boolean HACK_VERSION = false;
	@Override
	public void onInitialize() {
		instance = this;
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		Debug.info("Slimefun, start!");
		ModConfig.reloadModConfig();
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Utils.getNamespaceKey("reload_listener");
			}
			@Override
			public void reload(ResourceManager manager) {
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
		ModelLoadingRegistry.INSTANCE.registerModelProvider (new ExtraModelProvider() {
			@Override
			public void provideExtraModels(ResourceManager manager, Consumer<Identifier> out) {
				ModConfig.reloadModConfig();
				if(ModConfig.isEnableItemModelOvevrride()) {
					Debug.info("Force Load Model enabled");
					//pluginContext.addModels(new Identifier("networks","ntw_grid"));
					SlimefunCustomModelManager.walkThroughResourcePacks(manager,true).forEach(out);
				}else{
					SlimefunCustomModelManager.walkThroughResourcePacks(manager,false).forEach(out);
				}
			}
		});
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
	//todo 大饼: 实现指令系统，接入聊天框 !!开头
	//todo 大饼: 客户端实现/give指令劫持
	//todo 大饼: 发射器界面实现一键放入+合成(?)+交互合成按钮  有了
}