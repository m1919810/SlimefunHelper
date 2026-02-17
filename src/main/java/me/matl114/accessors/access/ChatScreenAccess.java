package me.matl114.accessors.access;

import me.matl114.accessors.gui.ScreenAccess;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;

public interface ChatScreenAccess extends ScreenAccess {
    public TextFieldWidget getInputWidget();

    public void resetMessageHistoryIndex();

    public ChatInputSuggestor getSuggestor();

    static ChatScreenAccess of(ChatScreen screen) {
        return (ChatScreenAccess) screen;
    }
}
