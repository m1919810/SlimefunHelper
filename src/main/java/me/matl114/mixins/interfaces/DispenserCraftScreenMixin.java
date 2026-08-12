package me.matl114.mixins.interfaces;

import me.matl114.accessors.interfaces.TileInventory;
import me.matl114.hacks.InvTasks;
import me.matl114.utils.world.ContainerPosition;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Generic3x3ContainerScreen.class)
public abstract class DispenserCraftScreenMixin extends HandledScreen<Generic3x3ContainerScreenHandler>
        implements TileInventory {

    @Shadow
    protected abstract void init();

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

    @Unique
    public HandledScreen<?> castHandled() {
        return this;
    }

    public DispenserCraftScreenMixin(ScreenHandler handler, PlayerInventory inventory, Text title) {
        super((Generic3x3ContainerScreenHandler) handler, inventory, title);
    }

    @Inject(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;<init>(Lnet/minecraft/screen/ScreenHandler;Lnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/text/Text;)V",
                            shift = At.Shift.AFTER))
    protected void tryInitBlockPos(
            Generic3x3ContainerScreenHandler handler, PlayerInventory inventory, Text title, CallbackInfo ci) {
        this.world = MinecraftClient.getInstance().world;
        this.pos = InvTasks.predictScreenFrom((b) -> b == Blocks.DISPENSER || b == Blocks.DROPPER);
        if (this.pos != null && this.world != null) {
            cacheBlockType = this.world.getBlockState(this.pos).getBlock();
            containerPosition = ContainerPosition.ofSingle(world, pos);
        }
        if (this.handler instanceof TileInventory.Handler handler1) {
            handler1.sync(this);
        }
    }
}
