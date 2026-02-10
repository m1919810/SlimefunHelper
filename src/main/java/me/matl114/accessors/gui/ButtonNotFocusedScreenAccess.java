package me.matl114.accessors.gui;

import net.minecraft.client.gui.Element;

public interface ButtonNotFocusedScreenAccess {
    //
    public Element getDefaultElement();
    // do not focus on the buttonWidget!
    boolean doFocusButtonWhenClicked();
    // as the name is
    default boolean autoSelectDefaultElementWhenNotFocused() {
        return true;
    }
    // save method

    default boolean enableSwitchUsingKey() {
        return false;
    }
}
