package me.matl114.mixins.events;

import me.matl114.accessors.events.ItemRenderStateAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.KeyedItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderState.class)
public abstract class ItemRenderStateEvents implements ItemRenderStateAccess {
    @Shadow
    ItemDisplayContext displayContext;

    @Shadow
    public abstract void addModelKey(Object modelKey);

    @Unique
    ItemRenderState attachedRender;

    public ItemRenderState getAttachedRenderState() {
        return attachedRender;
    }

    public void setAttachedRenderState(ItemRenderState state) {
        attachedRender = state;
        // mark a difference in the cache
        if (state != null) {
            addModelKey(state instanceof KeyedItemRenderState keyed ? keyed.getModelKey() : state);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRender1(
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, CallbackInfo ci) {
        if (attachedRender != null) {
            matrices.push();
            try {
                final float scale = 0.54f;
                final float scale_ground = 0.8f;
                boolean inGui = false;
                var renderMode = this.displayContext;
                if (renderMode == ItemDisplayContext.GUI) {
                    inGui = true;
                    matrices.translate(0.26, -0.26, 1f);
                    matrices.scale(scale, scale, scale);
                } else if (renderMode == ItemDisplayContext.GROUND) {
                    matrices.translate(0.15, -0.15, 0);
                    matrices.scale(scale_ground, scale_ground, scale_ground);
                } else if (renderMode == ItemDisplayContext.FIXED) {
                    matrices.translate(-0.25, -0.25, -0.05);
                    matrices.scale(scale_ground, scale_ground, scale_ground);
                } else if (renderMode == ItemDisplayContext.HEAD) {
                    // seems too wierd, give up
                    return;
                    //                    matrices.translate(-0.25,0.5,-0.05);
                    //    //                matrices. scale(scale_ground, scale_ground, scale_ground);
                    //                    renderMode = ModelTransformationMode.FIXED;
                } else if (renderMode == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                    // seems too wierd
                    //                matrices.translate(0.25,0.25,0.05);
                    //               matrices. scale(scale, scale, scale);
                    //                renderMode = ModelTransformationMode.GUI;
                    return;
                } else if (renderMode == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
                    // seems too wierd
                    //                matrices.translate(0.25,0.25,0.05);
                    //                matrices. scale(scale, scale, scale);
                    //                renderMode = ModelTransformationMode.GUI;
                    return;
                } else {
                    return;
                }
                if (inGui) {
                    MinecraftClient.getInstance()
                            .gameRenderer
                            .getDiffuseLighting()
                            .setShaderLights(DiffuseLighting.Type.ITEMS_FLAT);
                }

                attachedRender.render(matrices, vertexConsumers, light, overlay);
            } finally {
                matrices.pop();
            }
        }
    }
}
