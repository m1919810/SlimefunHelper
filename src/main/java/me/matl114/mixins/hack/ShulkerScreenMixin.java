package me.matl114.mixins.hack;

import me.matl114.accessors.access.TileInventoryScreen;
import me.matl114.hacks.InvTasks;
import me.matl114.utils.world.ContainerPosition;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ShulkerBoxScreen.class)
public abstract class ShulkerScreenMixin extends HandledScreen<ShulkerBoxScreenHandler> implements TileInventoryScreen {
    @Unique
    private BlockPos pos;

    public ShulkerScreenMixin(ShulkerBoxScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
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
    public HandledScreen<?> castHandled() {
        return this;
    }

    @Unique
    private ClientWorld world;

    @Unique
    private ContainerPosition containerPosition;

    @Unique
    public ContainerPosition getContainerPosition() {
        return this.containerPosition;
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
            ShulkerBoxScreenHandler handler, PlayerInventory inventory, Text title, CallbackInfo ci) {
        this.world = MinecraftClient.getInstance().world;
        // everything
        this.pos = InvTasks.predictScreenFrom((b) -> b instanceof ShulkerBoxBlock);
        if (this.pos != null && this.world != null) {
            var state = this.world.getBlockState(this.pos);
            cacheBlockType = state.getBlock();
            if (this.cacheBlockType instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                this.containerPosition = ContainerPosition.resolveDoubleChest(world, pos, state);
            } else {
                this.containerPosition = ContainerPosition.ofSingle(world, pos);
            }
        }
    }
}
