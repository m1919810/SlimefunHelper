package me.matl114.SlimefunUtils;

import me.matl114.Access.DrawContextAccess;
import me.matl114.Utils.ItemStackUtils;
import me.matl114.Utils.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Math;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

public class StorageItemRenderer {
    static final ThreadLocal<Boolean> isRendering = ThreadLocal.withInitial(() -> false);
    public static void render(
            DrawContext itemRenderer,
            //#endif
           LivingEntity livingEntity,
           World world,

            ItemStack itemStack, int x, int y,int z,int seed
    ){
        ItemStack stack=null;
        try{
            stack= SlimefunUtils.getStorageContent(itemStack);
        }catch (Throwable e){
            Debug.info("An Error occurred while deserialization");
            return;
        }
        if(stack!=null){
            RenderUtils.drawItem(itemRenderer,livingEntity,world,stack,0.6f,x+4,y+4,seed,z,10);
        }
    }


}
