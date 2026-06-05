package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.modules.move.ElytraExtra;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntityRenderer.class)
public abstract class EntityRenderStateMixin {
    @ModifyExpressionValue(
            method =
                    "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isFallFlying()Z"))
    private boolean updateBipedRenderState(
            boolean original, @Local(argsOnly = true) AbstractClientPlayerEntity livingEntity) {
        if (livingEntity.isFallFlying() && livingEntity == MinecraftClient.getInstance().player) {
            if (ElytraExtra.INSTANCE.renderFix.get() && ElytraExtra.INSTANCE.isCurrentArmorGliding()) {
                return false;
            }
        }
        return original;
    }
}
