package me.matl114.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

public class DebugUtils {
    public static void renderItemContainerItemInfo(ItemRenderer itemRenderer, MatrixStack matrices, ModelTransformationMode renderMode, ItemStack stack, boolean leftHanded, VertexConsumerProvider vertexConsumers, int overlay){
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
            if(inGui){
                DiffuseLighting.disableGuiDepthLighting();
            }
            itemRenderer.renderItem(stack,renderMode,leftHanded,matrices,vertexConsumers,0xF000F0,overlay,bakedModel);
        }finally {
            matrices.pop();
        }
    }
}
