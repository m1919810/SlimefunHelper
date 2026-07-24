package me.matl114.accessors.events;

import java.util.ArrayList;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;

public interface ChatHudAccess {
    public void setUniqueMessageId(String id);

    public ArrayList<ChatHudLine.Visible> getVisibleLines();

    public void clearUniqueMessages(String id);

    public static ChatHudAccess of(ChatHud chatHud) {
        return (ChatHudAccess) chatHud;
    }
}
