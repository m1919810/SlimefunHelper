package me.matl114.jsApi;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;

public class ClientHelper {
    static MinecraftClient mc = MinecraftClient.getInstance();
    public static MinecraftClient getClient(){
        return mc;
    }

    public static ClientPlayerEntity getPlayer(){
        return mc.player;
    }

    public static ClientWorld getWorld(){
        return mc.world;
    }

    public static GameOptions getGameOptions(){
        return mc.options;
    }

    public static void runTask(Runnable runnable){
        mc.execute(runnable);
    }


}
