package me.matl114.mixins.events;

import java.util.ArrayList;
import java.util.List;
import me.matl114.accessors.events.ItemRenderStateAccess;
import me.matl114.events.model.GuiModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
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

    @Shadow
    public abstract void clear();

    @Unique
    List<GuiModel.Entry> attachedRenders;

    public List<GuiModel.Entry> getAttachedRenderState() {
        if (attachedRenders == null) {
            attachedRenders = new ArrayList<>();
            addModelKey(attachedRenders);
        }
        return attachedRenders;
    }

    public void clearAttachedRenderState() {
        if (attachedRenders != null) {
            attachedRenders.clear();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRender1(
            MatrixStack matrices,
            OrderedRenderCommandQueue orderedRenderCommandQueue,
            int light,
            int overlay,
            int i,
            CallbackInfo ci) {
        if (attachedRenders != null && !attachedRenders.isEmpty()) {
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
                for (var entry : attachedRenders) {
                    if (entry.stackTransformer() != null) {
                        matrices.push();
                        entry.stackTransformer().apply(matrices);
                        entry.state().render(matrices, orderedRenderCommandQueue, light, overlay, i);
                        matrices.pop();
                    } else {
                        entry.state().render(matrices, orderedRenderCommandQueue, light, overlay, i);
                    }
                }
            } finally {
                matrices.pop();
            }
        }
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void onClear(CallbackInfo ci) {
        clearAttachedRenderState();
    }
}
