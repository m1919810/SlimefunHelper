package me.matl114.access;

import net.minecraft.client.gui.widget.ClickableWidget;

public interface ClickableAccess extends DepthableContent{
    public int getExtraDepth();
    public ClickableAccess setExtraDepth(int v);
    static ClickableAccess of(ClickableWidget clickableWidget){
        return (ClickableAccess) clickableWidget;
    }
}
