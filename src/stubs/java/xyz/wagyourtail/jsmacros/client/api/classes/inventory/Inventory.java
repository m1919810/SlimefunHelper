package xyz.wagyourtail.jsmacros.client.api.classes.inventory;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.jspecify.annotations.Nullable;

public class Inventory<T extends HandledScreen<?>> {

    public T getRawContainer() {
        return null;
    }

    public static Inventory<?> create() {
        return null;
    }

    public static Inventory<?> create(@Nullable Screen s) {
        return null;
    }
}
