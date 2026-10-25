package me.matl114.mixins.hack;

import java.util.List;
import me.matl114.hacks.modules.render.HandView;
import me.matl114.utils.render.EntityModelCollector;
import me.matl114.versioned.api.ModelFaceFactory;
import me.matl114.versioned.impl.HandFaceRenderer_v1_21_11;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
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
                    "renderItem(Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II[ILjava/util/List;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V",
            at = @At("HEAD"))
    private static void captureEntityItemStart(
            ItemDisplayContext displayContext,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay,
            int[] tintLayers,
            List<BakedQuad> quads,
            RenderLayer renderLayer,
            ItemRenderState.Glint glintType,
            CallbackInfo ci) {
        EntityModelCollector.captureItemQuads(vertexConsumers, matrices, quads, renderLayer);
    }

    @Inject(
            method =
                    "renderItem(Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II[ILjava/util/List;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V",
            at = @At("RETURN"))
    private static void captureEntityItemEnd(
            ItemDisplayContext displayContext,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay,
            int[] tintLayers,
            List<BakedQuad> quads,
            RenderLayer renderLayer,
            ItemRenderState.Glint glintType,
            CallbackInfo ci) {
        EntityModelCollector.finishItemQuads(vertexConsumers);
    }

    @Inject(
            method =
                    "renderItem(Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II[ILjava/util/List;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V",
            at = @At("RETURN"))
    private static void captureFirstPersonItem(
            ItemDisplayContext displayContext,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay,
            int[] tintLayers,
            List<BakedQuad> quads,
            RenderLayer renderLayer,
            ItemRenderState.Glint glintType,
            CallbackInfo ci) {
        if (!displayContext.isFirstPerson()
                || HandView.INSTANCE == null
                || !HandView.INSTANCE.isActive()
                || !HandView.INSTANCE.glow.get()) return;
        MODEL_FACE.begin();
        MODEL_FACE.submitItem(matrices, quads, renderLayer, -1);
        MODEL_FACE.render(HandView.INSTANCE.glowColor.get().withAlpha(HandView.INSTANCE.glowAlpha.get()));
    }
}
