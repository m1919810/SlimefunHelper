package me.matl114.managers.input;

import it.unimi.dsi.fastutil.ints.IntList;

public interface IHotKey {
    public boolean handleKeyInput(IInputManager manager, int keyCode, boolean isStateChanged, boolean isClicked);

    public String getIdentifier();

    public IntList getRelatedKeyCode();

    public void addRegisteredManager(IInputManager manager);
}
