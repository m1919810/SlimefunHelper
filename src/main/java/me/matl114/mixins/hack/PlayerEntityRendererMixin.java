package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.hacks.modules.move.ElytraExtra;
import me.matl114.hacks.modules.render.HandView;
import me.matl114.versioned.api.ModelFaceFactory;
import me.matl114.versioned.impl.HandFaceRenderer_v1_21_11;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    private static final HandFaceRenderer_v1_21_11 MODEL_FACE = ModelFaceFactory.getInstance();

    @Inject(method = "renderArm", at = @At("RETURN"))
    private void captureFirstPersonArm(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            AbstractClientPlayerEntity player,
            ModelPart arm,
            ModelPart sleeve,
            CallbackInfo ci) {
        if (HandView.INSTANCE == null || !HandView.INSTANCE.isActive() || !HandView.INSTANCE.glow.get()) return;
        MODEL_FACE.begin();
        Identifier texture = player.getSkinTextures().texture();
        MODEL_FACE.submitArm(arm, matrices, texture, light);
        // 1.21.1: the sleeve is a sibling of the arm (a child of it in 1.21.2+), so capture it separately
        MODEL_FACE.submitArm(sleeve, matrices, texture, light);
        if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }
        MODEL_FACE.render(HandView.INSTANCE.glowColor.get().withAlpha(HandView.INSTANCE.glowAlpha.get()));
    }

    @ModifyExpressionValue(
            method =
                    "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isFallFlying()Z"))
    public boolean onSetUpTransforms(boolean original, @Local(argsOnly = true) AbstractClientPlayerEntity player) {
        if (player == MinecraftClient.getInstance().player) {
            if (original && ElytraExtra.INSTANCE.isCurrentArmorGliding() && ElytraExtra.INSTANCE.renderFix.get()) {
                return false;
            }
        }
        return original;
    }
}
