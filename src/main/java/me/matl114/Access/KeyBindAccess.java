package me.matl114.Access;

import net.minecraft.client.option.KeyBinding;

public interface KeyBindAccess {
    public void resetKeyState();
    static KeyBindAccess of(KeyBinding keyBinding){
        return (KeyBindAccess)keyBinding;
    }
}
