package me.matl114.mixins.HackMixin;

import lombok.Getter;
import me.matl114.access.TileInventoryScreen;
import me.matl114.hackUtils.InteractionTasks;
import me.matl114.hackUtils.InvTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(GenericContainerScreen.class)
public abstract class ChestScreenMixin  extends HandledScreen<GenericContainerScreenHandler> implements TileInventoryScreen {

    @Unique
    private BlockPos pos;

    @Unique
    public BlockPos getPos(){
        return this.pos;
    }


    @Unique
    private Block cacheBlockType;

    public ChestScreenMixin(ScreenHandler handler, PlayerInventory inventory, Text title) {
        super((GenericContainerScreenHandler) handler, inventory, title);
    }

    @Unique
    public Block getBlockType(){
        return cacheBlockType;
    }

    @Unique
    public ClientWorld getWorld(){
        return this.world;
    }

    @Unique
    private ClientWorld world;
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;<init>(Lnet/minecraft/screen/ScreenHandler;Lnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/text/Text;)V", shift = At.Shift.AFTER))
    private void tryInitBlockPos(GenericContainerScreenHandler handler, PlayerInventory inventory, Text title, CallbackInfo ci){
        this.world = MinecraftClient.getInstance().world;
        //everything
        this.pos = InteractionTasks.predictScreenFrom((b)->true);
        if(this.pos != null && this.world != null){
            cacheBlockType = this.world.getBlockState(this.pos).getBlock();
        }
        InvTasks.registerTracedHandledScreen(this);
    }
}
