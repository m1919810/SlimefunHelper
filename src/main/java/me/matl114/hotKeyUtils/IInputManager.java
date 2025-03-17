package me.matl114.hotKeyUtils;

import net.minecraft.client.MinecraftClient;

public interface IInputManager {
    void registerHotKeys(IHotKey key);
    InputState getKeyState(int key);
    MinecraftClient getClient();
}
