package me.matl114.accessors.access;

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
    default boolean isVirtual(){
        return getPos()== null || getWorld() == null;
    }

    static TileInventoryScreen of(HandledScreen<?> handledScreen){
        return (TileInventoryScreen) handledScreen;
    }

    public HandledScreen<?> castHandled();
}
