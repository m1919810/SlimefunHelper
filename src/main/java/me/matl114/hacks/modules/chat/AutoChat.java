package me.matl114.hacks.modules.chat;

import me.matl114.events.Listener;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.events.Event;
import net.minecraft.client.network.ClientPlayerEntity;

public class AutoChat extends BaseModule {
    public static final String[] CHAT_HELPER_PERIOD={"chat-helper","period"};
    public static final String[] CHAT_HELPER_MULTIPLE={"chat-helper","multiple"};

    public static final String[] AUTO_SEND = {"simple-toggle", "auto-chat"};


    public AutoChat() {
        bindFlag(autoSend);
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        counter = 0;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getGameTick(), this::onTick);
    }

    public final FlagRef autoSend = toggle(AUTO_SEND)
        .build();

    public final IntRef period = builder(Configs.CHAT_CONFIG, Integer.class)
        .path(CHAT_HELPER_PERIOD)
        .defaultValue(21)
        .build();

    public final IntRef multiple = builder(Configs.CHAT_CONFIG, Integer.class)
        .path(CHAT_HELPER_MULTIPLE)
        .defaultValue(1)
        .build();

    public int counter = 0;

    public void onTick(Event<ClientPlayerEntity> gt){
        if(mc.getNetworkHandler() != null && isActive()){
            counter += 1;
            if(counter >= period.getValue()){
                counter = 0;
                for (var  i = 0 ; i < multiple.get(); ++i){
                    ChatTasks.getChatExtra().sendCachedMessage();
                }
            }
        }
    }

}
