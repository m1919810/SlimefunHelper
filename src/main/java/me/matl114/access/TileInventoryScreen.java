package me.matl114.access;

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
    default boolean isVirtual(){
        return getPos()== null || getWorld() == null;
    }

    static TileInventoryScreen of(HandledScreen<?> handledScreen){
        return (TileInventoryScreen) handledScreen;
    }
}
