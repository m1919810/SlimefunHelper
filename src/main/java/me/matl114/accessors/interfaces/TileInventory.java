package me.matl114.accessors.interfaces;

import me.matl114.utils.world.ContainerPosition;
import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public interface TileInventory {
    @Nullable
    public BlockPos getPos();

    @Nullable
    public ClientWorld getWorld();

    @Nullable
    public Block getBlockType();

    @Nullable
    public ContainerPosition getContainerPosition();

    @Nullable
    default boolean isVirtual() {
        return getContainerPosition() == null;
    }

    static TileInventory of(HandledScreen<?> handledScreen) {
        return (TileInventory) handledScreen;
    }

    public HandledScreen<?> castHandled();

    default ScreenHandler castHandler() {
        return castHandled().getScreenHandler();
    }

    public static interface Handler extends TileInventory {
        default HandledScreen<?> castHandled() {
            throw new UnsupportedOperationException();
        }

        default ScreenHandler castHandler() {
            return (ScreenHandler) this;
        }

        public void sync(TileInventory tileInventory);
    }
}
