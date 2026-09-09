package me.matl114.hacks.modules.chat;

import java.util.Objects;
import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.accessors.gui.ScreenAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.complex.other.ChatLikeInputWidget;
import me.matl114.hacks.ChatTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.WidgetPos;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;

public class InGuiChatBox extends BaseModule {
    public final ModulePath chat = makePath(Configs.CHAT_CONFIG, "chat-helper");

    public final FlagRef enableGui = flagBuilder(chat.add("chat-box-in-gui")).build();

    public final FlagRef enableOther =
            flagBuilder(chat.add("chat-box-in-any-screen")).build();

    public final NBTRef<WidgetPos> otherScreenInputPos = builder(chat.add("chat-box-other-pos"), WidgetPos.class)
            .defaultValue(new WidgetPos(0, 0.5, 1.0, 0, 0))
            .build();

    public final DoubleRef otherScreenInputLength = doubleBuilder(chat.add("chat-box-other-length"))
            .defaultValue(100.0D)
            .build();
    public static InGuiChatBox INSTANCE;

    public InGuiChatBox() {
        super("InGuiChatBox");
        INSTANCE = this;
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPostInitializeScreen(), this::onScreenInitialize);
    }

    public ClickableWidget createInputWidget(int x, int y, int width) {
        return new ChatLikeInputWidget(mc.textRenderer, x, y, width, 12, (str) -> {
            if (str != null && !str.isEmpty() && !Objects.equals(str, "/")) {
                // do not let blanks or / shits into it
                ChatTasks.sayMessage(str, true);
            }
        });
    }

    public ClickableWidget createDefaultInputWidget() {
        WidgetPos pos = otherScreenInputPos.get();
        double x = pos.getWindowX(mc.getWindow());
        double y = pos.getWindowY(mc.getWindow());
        return createInputWidget(
                (int) (x - otherScreenInputLength.get() / 2.0), (int) y - 12, (int) otherScreenInputLength.get());
    }

    public void onScreenInitialize(Event<Screen> event) {
        if (event.context instanceof HandledScreenAccess access && enableGui.get()) {
            ClickableWidget newChat = createInputWidget(
                    access.getScreenX() + 2,
                    access.getScreenY()
                            + access.getScreenBackgroundY()
                            + (access instanceof CreativeInventoryScreen ? 40 : 10),
                    access.getScreenBackgroundX() - 4);
            access.addDrawableChildTo(newChat);
        } else if (!(event.context instanceof HandledScreen<?>)) {
            if (checkNull()) return;
            if (enableOther.get()) {
                if (event.context.getFocused() instanceof TextFieldWidget || event.context instanceof ChatScreen) {
                    return;
                }
                ClickableWidget newChat = createDefaultInputWidget();
                ScreenAccess.of(event.context).addDrawableChildTo(newChat);
            }
        }
    }
}
