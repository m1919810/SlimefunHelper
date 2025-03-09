package me.matl114.HackUtils;

import lombok.Getter;
import me.matl114.ManageUtils.Config;
import me.matl114.ManageUtils.Configs;
import me.matl114.ManageUtils.HotKeys;
import me.matl114.Utils.UtilClass.LimitedSpeedExecutor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.atomic.AtomicInteger;

public class ChatTasks {
    public static void init(){

    }
    private static MinecraftClient mc = MinecraftClient.getInstance();
    private static AtomicInteger period= Configs.CHAT_CONFIG.getInt(Configs.CHAT_HELPER_PERIOD);
    private static Config.StringRef message = Configs.CHAT_CONFIG.getString(Configs.CHAT_HELPER_CACHE);
    private static AtomicInteger multiple= Configs.CHAT_CONFIG.getInt(Configs.CHAT_HELPER_MULTIPLE);
    private static AtomicInteger counter= new AtomicInteger(0);

    public static void onAutoChatStart(){
        if(counter.getAndIncrement()>period.get()){
            counter.set(0);
            for (int i=0;i<multiple.get();i++){
                sendMessage(message.get(),true);
            }
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
    @Getter
    private static final LimitedSpeedExecutor chatExecutor=new LimitedSpeedExecutor(new AtomicInteger(5));
    public static void sendDelayChatMessage(Text text){
        chatExecutor.addDelayedExecuteTask(()->mc.player.sendMessage(text));
    }
    static {
        Tasks.registerGameTask((player -> {
            if(HotKeys.getSimpleToggleManager().getState(HotKeys.AUTO_CHAT)){
                ChatTasks.onAutoChatStart();
            }else {
                ChatTasks.onAutoChatStop();
            }
        }));
        Tasks.registerGameTask(player -> {
            chatExecutor.reset();
        });
    }
}
