package me.matl114.mixins.fix;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.hacks.InvTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerFixMixin {
    @WrapOperation(method = "onSlotClick", at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/ScreenHandler;internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V"))
    private void wrapSlotClick(ScreenHandler instance, int slotIndex, int button, SlotActionType actionType, PlayerEntity player, Operation<Void> original) {
        boolean isClient = MinecraftClient.getInstance().world != null && MinecraftClient.getInstance().world.isClient;
        try{
            if(isClient){
                InvTasks.SUPPRESS_DROPITEM_SPAWN.set(true);
            }
            original.call(instance, slotIndex, button, actionType, player);
        }finally {
            if(isClient){
                InvTasks.SUPPRESS_DROPITEM_SPAWN.set(false);
            }
        }
    }
}
