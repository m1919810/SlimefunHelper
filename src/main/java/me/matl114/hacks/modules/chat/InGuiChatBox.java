package me.matl114.hacks.modules.chat;

import java.util.Objects;
import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.SubScreenWidget;
import me.matl114.gui.complex.other.ChatLikeInputSubScreen;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

public class InGuiChatBox extends BaseModule {
    public final ModulePath chat = makePath(Configs.CHAT_CONFIG, "chat-helper");

    public final FlagRef enable = flagBuilder(chat.add("chat-box-in-gui")).build();

    public InGuiChatBox() {
        bindFlag(enable);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostInitializeScreen().getChannel(HandledScreen.class), this::onScreenInitialize);
    }

    public void onScreenInitialize(Event<HandledScreen<?>> event) {
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
