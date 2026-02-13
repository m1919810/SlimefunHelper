package me.matl114.accessors.access;

import me.matl114.utils.world.ContainerPosition;
import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public interface TileInventoryScreen {
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

    static TileInventoryScreen of(HandledScreen<?> handledScreen) {
        return (TileInventoryScreen) handledScreen;
    }

    public HandledScreen<?> castHandled();
}
