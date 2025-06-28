package me.matl114.utils;

import net.minecraft.client.MinecraftClient;

public class ClientUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static boolean isPlayerOnline(){
        return mc.player != null && !mc.disconnecting;
    }
    public static boolean isNetworkConnecting(){
        return mc.getServer() != null;
    }

}
