package me.matl114.managers;

import net.minecraft.client.MinecraftClient;

public interface IInputManager {
    void registerHotKeys(IHotKey key);
    public void unregisterHotKeys(IHotKey key);
    public IHotKey getHotkey(String id);
    InputState getKeyState(int key);
    MinecraftClient getClient();
}
