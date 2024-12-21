package me.matl114.HackUtils;

import me.matl114.ManageUtils.Config;
import me.matl114.ManageUtils.Configs;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.atomic.AtomicInteger;

public class ChatTasks {
    private static AtomicInteger period= Configs.CHAT_CONFIG.getInt(Configs.CHAT_HELPER_PERIOD);
    private static Config.StringRef message = Configs.CHAT_CONFIG.getString(Configs.CHAT_HELPER_CACHE);
    private static AtomicInteger counter= new AtomicInteger(0);
    public static void onAutoChatStart(){
        if(counter.getAndIncrement()>period.get()){
            counter.set(0);
            sendMessage(message.get(),true);
        }
    }
    //modified from @ChatScreen.class
    public static void sendMessage(String chatText, boolean addToHistory) {
        if(MinecraftClient.getInstance().player!=null&&MinecraftClient.getInstance().player.networkHandler!=null){
            //in world
            if (addToHistory) {
                MinecraftClient.getInstance().inGameHud.getChatHud().addToMessageHistory(chatText);
            }
            if (chatText.startsWith("/")) {
                MinecraftClient.getInstance().player.networkHandler.sendChatCommand(chatText.substring(1));
            } else {
                MinecraftClient.getInstance().player.networkHandler.sendChatMessage(chatText);
            }
        }
    }

    public static void onAutoChatStop(){
        counter.set(0);
    }
}
