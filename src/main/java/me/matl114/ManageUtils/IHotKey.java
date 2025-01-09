package me.matl114.ManageUtils;

import java.util.Collection;

public interface IHotKey {
    public boolean handleKeyInput(IInputManager manager, int keyCode, boolean isStateChanged,boolean isClicked);
    public Collection<Integer> getRelatedKeyCode();
    default <T extends IHotKey> T register(IInputManager manager){
        manager.registerHotKeys(this);
        return (T) this;
    }
}
