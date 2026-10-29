package me.matl114.mixins.hack;

import me.matl114.hacks.modules.render.HandView;
import me.matl114.versioned.api.ModelFaceFactory;
import me.matl114.versioned.impl.HandFaceRenderer_v1_21_11;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
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

    @Inject(
            method =
                    "renderArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Identifier;Lnet/minecraft/client/model/ModelPart;Z)V",
            at = @At("RETURN"))
    private void captureFirstPersonArm(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            Identifier texture,
            ModelPart arm,
            boolean sleeveVisible,
            CallbackInfo ci) {
        if (HandView.INSTANCE == null || !HandView.INSTANCE.isActive() || !HandView.INSTANCE.glow.get()) return;
        MODEL_FACE.begin();
        MODEL_FACE.submitArm(arm, matrices, texture, light);
        if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }
        MODEL_FACE.render(HandView.INSTANCE.glowColor.get().withAlpha(HandView.INSTANCE.glowAlpha.get()));
        MODEL_FACE.render2D(0);
    }
}
