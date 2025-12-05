package me.matl114.managers;

import it.unimi.dsi.fastutil.ints.IntList;

import java.util.Collection;
import java.util.List;

public interface IHotKey {
    public boolean handleKeyInput(IInputManager manager, int keyCode, boolean isStateChanged,boolean isClicked);
    public void setKeyCodes(String keyCode);

    public String getIdentifier();

    public String getDefaultKeyCodes();

    public String getKeyCodes();

    public IntList getRelatedKeyCode();

    public int getTriggeredKey();

    public boolean isEmpty();

    default <T extends IHotKey> T register(IInputManager manager){
        manager.registerHotKeys(this);
        return (T) this;
    }
}
