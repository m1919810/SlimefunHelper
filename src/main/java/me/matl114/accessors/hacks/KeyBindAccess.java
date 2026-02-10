package me.matl114.accessors.hacks;

import net.minecraft.client.option.KeyBinding;

public interface KeyBindAccess {
    public void resetKeyState();

    static KeyBindAccess of(KeyBinding keyBinding) {
        return (KeyBindAccess) keyBinding;
    }
}
