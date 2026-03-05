package me.matl114.jsApi;

import java.util.concurrent.locks.LockSupport;
import me.matl114.utils.ApiMethod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;

@ApiMethod
public class ClientHelper {
    static MinecraftClient mc = MinecraftClient.getInstance();

    public static MinecraftClient getClient() {
        return mc;
    }

    public static ClientPlayerEntity getPlayer() {
        return mc.player;
    }

    public static ClientPlayerInteractionManager getInteractions() {
        return mc.interactionManager;
    }

    public static ClientWorld getWorld() {
        return mc.world;
    }

    public static GameOptions getGameOptions() {
        return mc.options;
    }

    public static void runTask(Runnable runnable) {
        mc.execute(runnable);
    }

    public static void sleep(long ms, long ns) throws Throwable {
        if (ms > 0) {
            Thread.sleep(ms);
        }
        if (ns > 0) {
            LockSupport.parkNanos(ns);
        }
    }

    public static void sleepNs(long ns) throws Throwable {
        long ms = ns / 1000;
        sleep(ms, ns % 1000);
    }

    public static void sleepMs(long ms) throws Throwable {
        sleep(ms, 0);
    }

    public static boolean isOnThread() {
        return mc.isOnThread();
    }
}
