package me.matl114.mixins.versioned;

import me.matl114.hacks.modules.combat.SpearEnhance;
import me.matl114.versioned.impl.LancingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererVersionedSpearMixin {
    @Inject(
            method = "renderItem",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void onRenderItemSpear1(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext transformationMode,
            Arm arm,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci) {
        if (entity instanceof PlayerEntity pl
                && SpearEnhance.INSTANCE.fixOldVersionSpear.get()
                && SpearEnhance.isUsingSpear(pl)) {
            Hand hand = pl.getActiveHand();
            Arm arm1 =
                    hand == Hand.MAIN_HAND ? pl.getMainArm() : pl.getMainArm().getOpposite();
            if (arm1 == arm) {
                LancingUtils.applyHeldItemFeatureArm(matrices, pl.getItemUseTime(), arm, stack);
            }
        }
    }
}
