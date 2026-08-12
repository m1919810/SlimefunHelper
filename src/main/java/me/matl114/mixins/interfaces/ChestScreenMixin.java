package me.matl114.mixins.interfaces;

import me.matl114.accessors.interfaces.EntityInventory;
import me.matl114.accessors.interfaces.TileInventory;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.InvTasks;
import me.matl114.utils.world.ContainerPosition;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.AbstractChestBoatEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.entity.vehicle.VehicleInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(GenericContainerScreen.class)
public abstract class ChestScreenMixin extends HandledScreen<GenericContainerScreenHandler>
        implements TileInventory, EntityInventory<VehicleInventory> {

    @Unique
    private BlockPos pos;

    @Unique
    public BlockPos getPos() {
        return this.pos;
    }

    @Unique
    private Block cacheBlockType;

    public ChestScreenMixin(ScreenHandler handler, PlayerInventory inventory, Text title) {
        super((GenericContainerScreenHandler) handler, inventory, title);
    }

    @Unique
    public Block getBlockType() {
        return cacheBlockType;
    }

    @Unique
    public ClientWorld getWorld() {
        return this.world;
    }

    @Unique
    public HandledScreen<?> castHandled() {
        return this;
    }

    @Override
    public ScreenHandler castHandler() {
        return this.handler;
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

    @Inject(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;<init>(Lnet/minecraft/screen/ScreenHandler;Lnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/text/Text;)V",
                            shift = At.Shift.AFTER))
    private void tryInitBlockPos(
            GenericContainerScreenHandler handler, PlayerInventory inventory, Text title, CallbackInfo ci) {
        this.world = MinecraftClient.getInstance().world;
        // everything
        this.pos = InvTasks.predictScreenFrom((b) -> true);
        if (this.pos != null && this.world != null) {
            var state = this.world.getBlockState(this.pos);
            cacheBlockType = state.getBlock();
            if (this.cacheBlockType instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                this.containerPosition = ContainerPosition.resolveDoubleChest(world, pos, state);
            } else {
                this.containerPosition = ContainerPosition.ofSingle(world, pos);
            }
        }
        if (this.handler instanceof TileInventory.Handler handler1) {
            handler1.sync(this);
        }

        var player = MinecraftClient.getInstance().player;
        vehicleEntity = (VehicleInventory) InteractionTasks.predictScreenFrom(ex -> {
            return ex instanceof StorageMinecartEntity
                    || (player != null && player.shouldCancelInteraction() && ex instanceof AbstractChestBoatEntity);
        });
        if (vehicleEntity == null
                && player != null
                && player.hasVehicle()
                && player.getVehicle() instanceof AbstractChestBoatEntity ccb) {
            vehicleEntity = ccb;
        }
        if (this.handler instanceof EntityInventory.Handler handler1) {
            handler1.sync(this);
        }
    }
}
