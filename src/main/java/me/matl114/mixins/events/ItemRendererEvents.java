package me.matl114.mixins.events;

import me.matl114.events.RenderListener;
import me.matl114.events.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ItemRenderer.class)
public abstract class ItemRendererEvents {
    @ModifyVariable(method = "getModel", at = @At("HEAD"), index = 1, argsOnly = true)
    public ItemStack onItemModelLoad(ItemStack stack){
        Event<ItemStack> itemStackEvent = new Event<>(stack, true, true);
        RenderListener.getItemDataOverrideForModel().handleValue(itemStackEvent);
        if(itemStackEvent.isCancelled()){
            return stack;
        }else{
            return itemStackEvent.context();
        }
    }

    @Inject(method = "renderItem",at = @At("RETURN"))
    public void onItemRenderDetached(ItemStack item, ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model, CallbackInfo ci){
        ItemStack stack = RenderListener.getContainedItemInfo(item);
        if(stack != null){
            renderItemContainerItemInfo((ItemRenderer) (Object)this, matrices, renderMode, stack, leftHanded, vertexConsumers, overlay);
        }

    }
    @Unique
    private static void renderItemContainerItemInfo(ItemRenderer itemRenderer, MatrixStack matrices, ModelTransformationMode renderMode, ItemStack stack, boolean leftHanded, VertexConsumerProvider vertexConsumers, int overlay){
        matrices.push();
        try{
            final float scale=0.54f;
            final float scale_ground=0.8f;
            boolean inGui = false;
            if(renderMode == ModelTransformationMode.GUI){
                inGui = true;
                matrices.translate(0.26,-0.26,1f);
                matrices. scale(scale, scale, scale);
            }else if(renderMode == ModelTransformationMode.GROUND){
                matrices.translate(0.15,-0.15,0);
                matrices. scale(scale_ground, scale_ground, scale_ground);
            }else if(renderMode == ModelTransformationMode.FIXED){
                matrices.translate(-0.25,-0.25,-0.05);
                matrices. scale(scale_ground, scale_ground, scale_ground);
            }else if(renderMode == ModelTransformationMode.HEAD) {
                //seems too wierd, give up
                return;
//                    matrices.translate(-0.25,0.5,-0.05);
//    //                matrices. scale(scale_ground, scale_ground, scale_ground);
//                    renderMode = ModelTransformationMode.FIXED;
            }else if(renderMode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND){
                //seems too wierd
//                matrices.translate(0.25,0.25,0.05);
//               matrices. scale(scale, scale, scale);
//                renderMode = ModelTransformationMode.GUI;
                return;
            }else if(renderMode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND){
                //seems too wierd
//                matrices.translate(0.25,0.25,0.05);
//                matrices. scale(scale, scale, scale);
//                renderMode = ModelTransformationMode.GUI;
                return;
            }else{
                return;
            }
            BakedModel bakedModel=itemRenderer.getModel(stack, MinecraftClient.getInstance().world, MinecraftClient.getInstance().player, 0);
            //fixme: renderer error here
            if(inGui)
                DiffuseLighting.enableGuiDepthLighting();
            itemRenderer.renderItem(stack,renderMode,leftHanded,matrices,vertexConsumers,0xF000F0,overlay,bakedModel);
            if(inGui)
                DiffuseLighting.disableGuiDepthLighting();
        }finally {
            matrices.pop();
        }
    }


    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
    public void onItemRenderStart(ItemStack item, ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model,CallbackInfo ci){
        Event<ItemStack> itemStackEvent = new Event<>(item, false, false, matrices, renderMode, leftHanded);
        RenderListener.getItemRender().handleValue(itemStackEvent);
    }
}
