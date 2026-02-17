package me.matl114.accessors.gui;

import net.minecraft.client.gui.Element;

public interface CustomFocusBehaviourScreenAccess {
    //
    public Element getDefaultElement();
    // do not focus on the buttonWidget!
    boolean canFocusButtonWhenClicked();
    // as the name is
    default boolean autoSelectDefaultElementWhenNotFocused() {
        return true;
    }
    // save method

    default boolean enableSwitchUsingNavigation() {
        return false;
    }
}
