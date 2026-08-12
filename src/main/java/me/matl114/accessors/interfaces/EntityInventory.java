package me.matl114.accessors.interfaces;

import javax.annotation.Nullable;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;

public interface EntityInventory<T> {
    @Nullable
    T getOwner();

    public HandledScreen<?> castHandled();

    default ScreenHandler castHandler() {
        return castHandled().getScreenHandler();
    }

    public interface Handler<T> extends EntityInventory<T> {
        default HandledScreen<?> castHandled() {
            throw new UnsupportedOperationException();
        }

        default ScreenHandler castHandler() {
            return (ScreenHandler) this;
        }

        public void sync(EntityInventory<T> inventory);
    }
}
