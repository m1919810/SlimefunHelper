package me.matl114.managers;

import net.minecraft.client.MinecraftClient;

public interface IInputManager {
    void registerHotKeys(IHotKey key);
    InputState getKeyState(int key);
    MinecraftClient getClient();
}
