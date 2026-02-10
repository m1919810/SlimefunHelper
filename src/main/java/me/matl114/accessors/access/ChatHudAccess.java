package me.matl114.accessors.access;

import java.util.ArrayList;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;

public interface ChatHudAccess {
    public ArrayList<ChatHudLine.Visible> getVisibleLines();

    public static ChatHudAccess of(ChatHud hud) {
        return (ChatHudAccess) hud;
    }
}
