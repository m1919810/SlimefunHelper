package me.matl114.mixins.hack;

import me.matl114.hacks.modules.render.HandView;
import me.matl114.versioned.api.ModelFaceFactory;
import me.matl114.versioned.impl.HandFaceRenderer_v1_21_11;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    private static final HandFaceRenderer_v1_21_11 MODEL_FACE = ModelFaceFactory.getInstance();

    @Inject(
            method =
                    "renderItem(Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II[ILnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V",
            at = @At("RETURN"))
    private static void captureFirstPersonItem(
            ItemDisplayContext displayContext,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay,
            int[] tintLayers,
            BakedModel model,
            RenderLayer renderLayer,
            ItemRenderState.Glint glintType,
            CallbackInfo ci) {
        if (displayContext.isFirstPerson()) {
            if (HandView.INSTANCE == null || !HandView.INSTANCE.isActive() || !HandView.INSTANCE.glow.get()) return;
            MODEL_FACE.begin();
            Random random = Random.create();
            for (Direction direction : Direction.values()) {
                random.setSeed(42L);
                MODEL_FACE.submitItem(matrices, model.getQuads(null, direction, random), renderLayer, -1);
            }
            random.setSeed(42L);
            MODEL_FACE.submitItem(matrices, model.getQuads(null, null, random), renderLayer, -1);
            if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate) {
                immediate.draw(renderLayer);
            }
            MODEL_FACE.render(HandView.INSTANCE.glowColor.get().withAlpha(HandView.INSTANCE.glowAlpha.get()));
        }
    }
}
