package me.matl114.hacks.modules.chat;

import java.util.Objects;
import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.other.ChatLikeInputSubScreen;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;

public class InGuiChatBox extends BaseModule {
    public static final String[] CHAT_BOX_IN_GUI = {"chat-helper", "chat-box-in-gui"};

    public final FlagRef enable =
            flagBuilder(Configs.CHAT_CONFIG, CHAT_BOX_IN_GUI).build();

    public InGuiChatBox() {
        bindFlag(enable);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostInitializeScreen(), this::onScreenInitialize);
    }

    public void onScreenInitialize(Event<Screen> event) {
        if (event.context instanceof HandledScreenAccess access && enable.get()) {
            SubScreenWidget newChat = new ChatLikeInputSubScreen(
                    access.getScreenX() + 2,
                    access.getScreenY()
                            + access.getScreenBackgroundY()
                            + (access instanceof CreativeInventoryScreen ? 40 : 10),
                    access.getScreenBackgroundX() - 4,
                    12,
                    (str) -> {
                        if (str != null && !str.isEmpty() && !Objects.equals(str, "/")) {
                            // do not let blanks or / shits into it
                            ChatTasks.sayMessage(str, true);
                        }
                    });
            access.addDrawableChildTo(newChat);
        }
    }
}
