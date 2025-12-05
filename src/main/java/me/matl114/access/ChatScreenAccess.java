package me.matl114.access;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;

public interface ChatScreenAccess {
    public TextFieldWidget getInputWidget();

    static ChatScreenAccess of(ChatScreen screen){
        return (ChatScreenAccess) screen;
    }
}
