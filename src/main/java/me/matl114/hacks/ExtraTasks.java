package me.matl114.hacks;

import lombok.Getter;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.extra.*;

public class ExtraTasks {
    public static void init() {}

    @Getter
    public static final ModuleGroup moduleManager = new ModuleGroup("Extra");

    @Getter
    public static ClientExtra clientExtra;

    @Getter
    public static PacketDebugger packetDebugger;

    @Getter
    public static BadPacketsFix badPacketsFix;

    @Getter
    public static BeaconEnhance beaconEnhance;

    @Getter
    public static EnderEyeLog enderEyeLog;

    @Getter
    public static GuiFix guiFix;

    @Getter
    public static ServerScanner serverScanner;

    @Getter
    public static AutoReconnect autoReconnect;

    @Getter
    public static Warps warps;

    @Getter
    public static BoatVClip boatVClip;

    @Getter
    public static IQBoost iqBoost;

    @Getter
    public static SkinBlink skinBlink;

    @Getter
    public static EventNotify eventNotify;

    private static void initModules(ModuleManager m) {
        clientExtra = new ClientExtra().register(m);
        packetDebugger = new PacketDebugger().register(m);
        badPacketsFix = new BadPacketsFix().register(m);
        beaconEnhance = new BeaconEnhance().register(m);

        enderEyeLog = new EnderEyeLog().register(m);
        guiFix = new GuiFix().register(m);

        serverScanner = new ServerScanner().register(m);
        autoReconnect = new AutoReconnect().register(m);
        warps = new Warps().register(m);
        boatVClip = new BoatVClip().register(m);
        iqBoost = new IQBoost().register(m);
        skinBlink = new SkinBlink().register(m);
        eventNotify = new EventNotify().register(m);
    }

    static {
        moduleManager.registerFactories(ExtraTasks::initModules);
        HackModules.registerModuleGroup(moduleManager);
    }
}
