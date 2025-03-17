package me.matl114.access;

import net.minecraft.client.option.KeyBinding;

public interface KeyBindAccess {
    public void resetKeyState();
    static KeyBindAccess of(KeyBinding keyBinding){
        return (KeyBindAccess)keyBinding;
    }
}
