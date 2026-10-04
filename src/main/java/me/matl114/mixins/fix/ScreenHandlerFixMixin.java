package me.matl114.mixins.fix;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.hacks.InvTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerFixMixin {

    @WrapOperation(
            method = "onSlotClick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/screen/ScreenHandler;internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V"))
    private void suppressDropItemSpawn(
            ScreenHandler instance,
            int slotIndex,
            int button,
            SlotActionType actionType,
            PlayerEntity player,
            Operation<Void> original) {
        boolean clientWorld = player.getEntityWorld().isClient();
        if (!clientWorld) {
            original.call(instance, slotIndex, button, actionType, player);
            return;
        }

        boolean previous = InvTasks.SUPPRESS_DROPITEM_SPAWN.get();
        InvTasks.SUPPRESS_DROPITEM_SPAWN.set(true);
        try {
            original.call(instance, slotIndex, button, actionType, player);
        } finally {
            InvTasks.SUPPRESS_DROPITEM_SPAWN.set(previous);
        }
    }
}
