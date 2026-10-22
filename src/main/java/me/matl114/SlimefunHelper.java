package me.matl114;

import java.util.Set;
import lombok.Getter;
import me.matl114.plugins.MainPlugin;
import me.matl114.plugins.PluginManager;
import me.matl114.plugins.PluginManager.BootstrapPhase;
import me.matl114.utils.Debug;
import me.matl114.versioned.impl.Render_v1_21_1;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public class SlimefunHelper implements ModInitializer {
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final String MOD_ID = "slimefunhelper";
    // public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    @Getter
    public static SlimefunHelper instance;

    @Getter
    public static ModContainer modContainer;

    public static boolean DEV_ENV = false;

    public static Set<String> DEV_NAME =
            Set.of("matl114", "matl_test", "matl_test2", "mtl", "||matl_test", "||matl_test2", "||mtl");

    public static void authentication() {
        try {
            Class.forName("net.minecraft.client.MinecraftClient");
            DEV_ENV = true;
            Debug.info("Dev Environment Detected !");
        } catch (Throwable e) {
        }
    }

    public static void initializeEnvironment() {
        modContainer = FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .orElseThrow(() -> new IllegalStateException("Missing mod container for " + MOD_ID));
    }

    @Override
    public void onInitialize() {
        instance = this;
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
                Identifier.of(MOD_ID, "entity_highlight"),
                VertexFormats.POSITION_TEXTURE_COLOR,
                Render_v1_21_1::setEntityHighlightProgram));
        authentication();
        initializeEnvironment();
        Debug.info("SlimefunHelper, start!");
        Debug.info("SlimefunHelper start loading!");
        PluginManager pluginManager = PluginManager.getInstance();
        pluginManager.begin(new MainPlugin(getModContainer()));
        pluginManager.discover();
        pluginManager.advance(BootstrapPhase.CONFIG_READ);
        pluginManager.advance(BootstrapPhase.LISTENERS);
        pluginManager.advance(BootstrapPhase.MODULES);
        pluginManager.advance(BootstrapPhase.OTHER);
        pluginManager.complete();
        Debug.info("SlimefunHelper loading finish");
    }
}
