package me.matl114.mixins.versioned;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.hacks.modules.combat.SpearEnhance;
import me.matl114.versioned.impl.LancingUtils;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemVersionedSpearMixin {
    @WrapOperation(
            method = "renderFirstPersonItem",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V",
                            ordinal = 2))
    private void onSpearUseRender(
            HeldItemRenderer instance,
            MatrixStack matrices,
            Arm arm,
            float equipProgress,
            Operation<Void> original,
            @Local(argsOnly = true) AbstractClientPlayerEntity player,
            @Local(argsOnly = true, ordinal = 0) float tickProgress,
            @Local(argsOnly = true) ItemStack itemStack) {
        if (SpearEnhance.INSTANCE.fixOldVersionSpear.get() && SpearEnhance.INSTANCE.hasRealComponent(itemStack)) {
            // do spear
            boolean bl2 = arm == Arm.RIGHT;
            int l = bl2 ? 1 : -1;
            matrices.translate((float) l * 0.56F, -0.52F, -0.72F);
            float f = (float) itemStack.getMaxUseTime(player)
                    - ((float) player.getItemUseTimeLeft() - tickProgress + 1.0F);
            LancingUtils.applySpearKineticTransform(
                    itemStack.getItem(),
                    SpearEnhance.INSTANCE.getTimeSinceLastKineticAttack(player, tickProgress),
                    matrices,
                    f,
                    arm,
                    itemStack);
            return;
        }
        original.call(instance, matrices, arm, equipProgress);
    }
}
