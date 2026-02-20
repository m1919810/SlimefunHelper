package com.jsmacrosce.jsmacros.client.api.classes.inventory;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

public class Inventory<T extends HandledScreen<?>> {

    public T getRawContainer() {
        return null;
    }

    public static Inventory<?> create() {
        return null;
    }

    public static Inventory<?> create(Screen s) {
        return null;
    }
}
