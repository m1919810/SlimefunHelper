package me.matl114.hacks;

import lombok.Getter;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.extra.BeaconEnhance;
import me.matl114.hacks.modules.extra.ClientExtra;
import me.matl114.hacks.modules.extra.PacketDebugger;
import me.matl114.hacks.modules.extra.Tests;
import me.matl114.utils.Debug;

public class ExtraTasks {
    public static void init() {}

    public static final ModuleGroup moduleManager = new ModuleGroup("Extra");
    public static boolean DEBUG_INTO_CHAT = true;

    public static void debug(Object... val) {
        if (DEBUG_INTO_CHAT) {
            Debug.chat(val);
        } else {
            Debug.info(val);
        }
    }

    @Getter
    public static ClientExtra clientExtra;

    @Getter
    public static Tests tests;

    @Getter
    public static PacketDebugger packetDebugger;

    @Getter
    public static BeaconEnhance beaconEnhance;

    private static void initModules(ModuleManager m) {
        clientExtra = new ClientExtra().register(m);
        ;
        tests = new Tests().register(m);
        packetDebugger = new PacketDebugger().register(m);
        beaconEnhance = new BeaconEnhance().register(m);
    }

    static {
        moduleManager.registerFactories(ExtraTasks::initModules);
        HackModules.registerModuleGroup(moduleManager);
    }
}
