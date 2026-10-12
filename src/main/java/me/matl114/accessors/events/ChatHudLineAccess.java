package me.matl114.accessors.events;

import java.util.Set;
import net.minecraft.client.gui.hud.ChatHudLine;

public interface ChatHudLineAccess {
    public void addUniqueMessageId(String uniqueMessageId);

    public Set<String> getUniqueMessageIds();

    public static ChatHudLineAccess of(ChatHudLine chatHudLine) {
        return (ChatHudLineAccess) (Object) chatHudLine;
    }

    public static ChatHudLineAccess of(ChatHudLine.Visible uniqueMessageId) {
        return (ChatHudLineAccess) (Object) uniqueMessageId;
    }
}
