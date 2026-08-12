package me.matl114.mixins.interfaces;

import me.matl114.accessors.interfaces.EntityInventory;
import me.matl114.accessors.interfaces.TileInventory;
import me.matl114.utils.world.ContainerPosition;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.vehicle.VehicleInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Environment(EnvType.CLIENT)
@Mixin(GenericContainerScreenHandler.class)
public abstract class ChestScreenHandlerMixin extends ScreenHandler
        implements TileInventory.Handler, EntityInventory.Handler<VehicleInventory> {
    @Unique
    private BlockPos pos;

    protected ChestScreenHandlerMixin(@org.jspecify.annotations.Nullable ScreenHandlerType<?> type, int syncId) {
        super(type, syncId);
    }

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
    public ClientWorld getWorld() {
        return this.world;
    }

    @Unique
    private ClientWorld world;

    @Unique
    private ContainerPosition containerPosition;

    @Unique
    public ContainerPosition getContainerPosition() {
        return this.containerPosition;
    }

    @Unique
    VehicleInventory vehicleEntity;

    @Nullable
    @Override
    public VehicleInventory getOwner() {
        return vehicleEntity;
    }

    @Override
    public void sync(EntityInventory<VehicleInventory> inventory) {
        this.vehicleEntity = inventory.getOwner();
    }

    @Override
    public HandledScreen<?> castHandled() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScreenHandler castHandler() {
        return this;
    }

    @Override
    public void sync(TileInventory tileInventory) {
        this.world = tileInventory.getWorld();
        this.containerPosition = tileInventory.getContainerPosition();
        this.pos = this.containerPosition.getFirst().getPos();
        this.cacheBlockType = this.world.getBlockState(this.pos).getBlock();
    }
}
