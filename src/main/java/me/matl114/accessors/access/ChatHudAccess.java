package me.matl114.accessors.access;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;

import java.util.ArrayList;

public interface ChatHudAccess {
    public ArrayList<ChatHudLine.Visible> getVisibleLines();

    public static ChatHudAccess of(ChatHud hud){
        return (ChatHudAccess) hud;
    }
}
