package me.matl114.ManageUtils;

import net.minecraft.client.MinecraftClient;

public interface IInputManager {
    void registerHotKeys(IHotKey key);
    InputState getKeyState(int key);
    MinecraftClient getClient();
}
