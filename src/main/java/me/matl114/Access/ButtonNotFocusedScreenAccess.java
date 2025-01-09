package me.matl114.Access;

import net.minecraft.client.gui.Element;

public interface ButtonNotFocusedScreenAccess {
    //
    public Element getDefaultElement();
    //do not focus on the buttonWidget!
    boolean doFocusButtonWhenClicked();
    //as the name is
    default boolean autoSelectDefaultElementWhenNotFocused(){
        return true;
    }
    //save method
    public void saveEntryToValues();
    default boolean enableSwitchUsingKey(){
        return false;
    }
}
