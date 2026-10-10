package me.matl114.hacks;

import lombok.Getter;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.ac.DisablerManager;
import me.matl114.hacks.modules.ac.PacketOrderManager;
import me.matl114.hacks.modules.ac.PostManager;
import net.minecraft.client.MinecraftClient;

public class ACTasks {
    public static void init() {}

    // represent that is there any anti-cheats transactions

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    // anti grim's post check
    // sent after pong packet
    @Getter
    private static PostManager postManager;

    @Getter
    private static DisablerManager disablerManager;

    @Getter
    private static PacketOrderManager packetOrderManager;

    private static void initModules(ModuleManager moduleManager) {
        postManager = new PostManager().register(moduleManager);
        disablerManager = new DisablerManager().register(moduleManager);
        packetOrderManager = new PacketOrderManager().register(moduleManager);
    }

    static {
        ExtraTasks.getModuleManager().registerFactories(ACTasks::initModules);
    }
}
