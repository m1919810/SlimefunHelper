package me.matl114.mixins.interfaces;

import me.matl114.accessors.interfaces.TileInventory;
import me.matl114.utils.world.ContainerPosition;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Environment(EnvType.CLIENT)
@Mixin(Generic3x3ContainerScreen.class)
public abstract class DispenserCraftScreenHandlerMixin implements TileInventory.Handler {
    @Unique
    private BlockPos pos;

    @Unique
    public BlockPos getPos() {
        return this.pos;
    }

    @Unique
    private Block cacheBlockType;

    @Unique
    public Block getBlockType() {
        return cacheBlockType;
    }

    @Unique
    private ClientWorld world;

    @Unique
    public ClientWorld getWorld() {
        return this.world;
    }

    @Unique
    private ContainerPosition containerPosition;

    @Unique
    public ContainerPosition getContainerPosition() {
        return this.containerPosition;
    }

    @Override
    public void sync(TileInventory tileInventory) {
        this.pos = tileInventory.getPos();
        this.cacheBlockType = tileInventory.getBlockType();
        this.world = tileInventory.getWorld();
        this.containerPosition = tileInventory.getContainerPosition();
    }
}
