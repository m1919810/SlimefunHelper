package me.matl114;

import me.matl114.SlimefunUtils.Debug;
import me.matl114.SlimefunUtils.SlimefunItemModelManager;
import me.matl114.SlimefunUtils.SlimefunUtils;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class SlimefunHelper implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final String MOD_ID = "slimefunhelper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static SlimefunHelper instance;
	public static SlimefunHelper getInstance() {
		return instance;
	}
	@Override
	public void onInitialize() {
		instance = this;
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		LOGGER.info("Slimefun, start!");
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return SlimefunUtils.getNamespaceKey("reload_listener");
			}
			@Override
			public void reload(ResourceManager manager) {
				Debug.info("Reloading SlimefunHelper resources");
				SlimefunItemModelManager.init();
				SlimefunItemModelManager.loadCustomModelDatas();
			}
		});

	}
}