package me.matl114.managers.input;

import me.matl114.managers.InputState;
import net.minecraft.client.MinecraftClient;

public interface IInputManager {
    void registerHotKeys(IHotKey key);

    public void unregisterHotKeys(IHotKey key);

    public IHotKey getHotkey(String id);

    InputState getKeyState(int key);

    InputState getKeyStateOrCreate(int key);

    boolean isKeyPressed(int key);

    MinecraftClient getClient();
}
