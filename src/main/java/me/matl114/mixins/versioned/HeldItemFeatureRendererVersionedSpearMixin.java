package me.matl114.mixins.versioned;

import me.matl114.hacks.modules.combat.SpearEnhance;
import me.matl114.versioned.accessors.PlayerEntityRendererStateAccess;
import me.matl114.versioned.impl.LancingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
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
                                    "Lnet/minecraft/client/render/item/ItemRenderState;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V"))
    private void onRenderItemSpear1(
            ArmedEntityRenderState entityState,
            ItemRenderState itemState,
            Arm arm,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci) {
        if (entityState instanceof PlayerEntityRendererStateAccess acc
                && acc instanceof PlayerEntityRenderState pls
                && SpearEnhance.INSTANCE.fixOldVersionSpear.get()) {
            Arm spearHand = acc.getSpearingHand();
            if (spearHand != null && spearHand == arm) {
                ItemStack stack = acc.getSpearingItem();
                if (stack != null) {
                    LancingUtils.applyHeldItemFeatureArm(
                            entityState, matrices, ((PlayerEntityRenderState) entityState).itemUseTime, arm, stack);
                }
            }
        }
    }
}
