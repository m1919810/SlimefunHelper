package me.matl114.mixins.versioned;

import me.matl114.hacks.modules.combat.SpearEnhance;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Item.class)
public abstract class ItemVersionedSpearMixin {
    @Inject(method = "getMaxUseTime", at = @At("RETURN"), cancellable = true)
    private void fixSpearUse2(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        int val = cir.getReturnValueI();
        if (val == 0
                && SpearEnhance.INSTANCE.fixOldVersionSpear.get()
                && SpearEnhance.INSTANCE.hasRealComponent(stack)) {
            cir.setReturnValue(72000);
        }
    }
}
