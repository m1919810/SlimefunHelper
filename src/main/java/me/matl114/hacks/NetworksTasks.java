package me.matl114.hacks;

import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.networks.ConnectionProxy;
import net.minecraft.client.MinecraftClient;

public class NetworksTasks {
    public static void init() {

    }

    private static final MinecraftClient mc = MinecraftClient.getInstance();


    public static final ModuleGroup moduleManage = new ModuleGroup("Networks");

    public static ConnectionProxy connectionProxy;
    private static void initModules(ModuleManager m) {
        connectionProxy = new ConnectionProxy()
            .register(m);
    }

    static{
        moduleManage.registerFactories(NetworksTasks::initModules);
        HackModules.registerModuleGroup(moduleManage);
    }


}
