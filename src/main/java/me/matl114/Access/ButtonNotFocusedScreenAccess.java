package me.matl114.Access;

import net.minecraft.client.gui.Element;

public interface ButtonNotFocusedScreenAccess {
    public Element getDefaultElement();
    boolean doKeepButtonAfterClicked();
    default boolean autoSelectDefaultElementWhenNotFocused(){
        return true;
    }
}
