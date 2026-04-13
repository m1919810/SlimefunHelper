package me.matl114.hacks;

import java.util.function.Consumer;
import lombok.Getter;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.ac.PostManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

public class ACPostTasks {
    public static void init() {}

    // represent that is there any anti-cheats transactions

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    // anti grim's post check
    // sent after pong packet

    public static void addPostTickAction(Consumer<ClientPlayNetworkHandler> handler) {
        postManager.addPostTickAction(handler);
    }

    public static void addPostTransactionAction(Consumer<ClientPlayNetworkHandler> packet) {
        postManager.addNextPreTickAction(packet);
    }

    @Getter
    private static PostManager postManager;

    private static void initModules(ModuleManager moduleManager) {
        postManager = new PostManager().register(moduleManager);
    }

    static {
        ExtraTasks.getModuleManager().registerFactories(ACPostTasks::initModules);
    }
}
