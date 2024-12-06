package me.matl114.HackUtils;

import me.matl114.Access.ClientPlayerAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

public class Tasks {
    public static void clearKeepedInv(){
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if(player!=null){
            ClientPlayerAccess access=ClientPlayerAccess.of(player);
            access.clearKeepedInventory(true);
            player.sendMessage(Text.literal("已清除界面历史记录"));
        }
    }
}
