package me.matl114.managers.input;

import it.unimi.dsi.fastutil.ints.IntList;

public interface IHotKey {
    public boolean handleKeyInput(IInputManager manager, int keyCode, boolean isStateChanged, boolean isClicked);

    public String getIdentifier();

    public MultiKeyBind getDefaultKeyCodes();

    public MultiKeyBind getKeyCodes();

    public IntList getRelatedKeyCode();

    public int getTriggeredKey();

    public boolean isEmpty();

    public void setKeyCodes(MultiKeyBind keyCode);

    public void setInputHandler(SimpleHotKey.InputHandler handler);

    default <T extends IHotKey> T register(IInputManager manager) {
        manager.registerHotKeys(this);
        return (T) this;
    }
}
