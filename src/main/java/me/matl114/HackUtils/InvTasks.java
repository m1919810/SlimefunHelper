package me.matl114.HackUtils;

import me.matl114.Access.ClientPlayerAccess;
import me.matl114.ManageUtils.Config;
import me.matl114.ManageUtils.ConfigureScreen;
import me.matl114.ManageUtils.SelectScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;

public class InvTasks {
    public static void clearKeepedInv(){
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if(player!=null){
            ClientPlayerAccess access=ClientPlayerAccess.of(player);
            access.clearKeepedInventory(true);
            player.sendMessage(Text.literal("已清除界面历史记录"));
        }
    }

    public static void openSelectScreen(){
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if(player!=null){
            HashMap<String,Runnable> map = new HashMap<>();
            for(Config config:Config.getConfigs()){
                map.put(config.getConfigName(),()->openConfigScreen(config));
            }
            MinecraftClient.getInstance().setScreen(new SelectScreen(map,4,Text.of("")));
        }
    }
    public static void openConfigScreen(Config config){
        MinecraftClient.getInstance().setScreen(new ConfigureScreen(config,Text.of("")));
    }

}
