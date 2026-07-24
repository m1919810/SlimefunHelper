package me.matl114.accessors.events;

import net.minecraft.client.gui.hud.ChatHudLine;

public interface ChatHudLineAccess {
    public void setUniqueMessageId(String uniqueMessageId);

    public String getUniqueMessageId();

    public static ChatHudLineAccess of(ChatHudLine chatHudLine) {
        return (ChatHudLineAccess) (Object) chatHudLine;
    }

    public static ChatHudLineAccess of(ChatHudLine.Visible uniqueMessageId) {
        return (ChatHudLineAccess) (Object) uniqueMessageId;
    }
}
