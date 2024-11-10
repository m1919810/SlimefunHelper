package me.matl114.SlimefunMixin;

import it.unimi.dsi.fastutil.objects.ObjectCollection;
import me.matl114.Access.BakedModelManagerAccess;
import me.matl114.Access.ItemRendererAccess;
import me.matl114.SlimefunUtils.Debug;
import me.matl114.SlimefunUtils.SlimefunItemModelManager;
import me.matl114.SlimefunUtils.SlimefunUtils;
import me.matl114.Utils.RenderUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemModels;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Random;

@Environment(value= EnvType.CLIENT)
@Mixin(ItemRenderer.class)
public abstract class CustomNbtTextureRendererMixin implements ItemRendererAccess {
    @Shadow
    @Final
    private ItemModels models;
    @Shadow public abstract void  renderItem(ItemStack stack, ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model);
    @Shadow public abstract ItemModels getModels();
    @Shadow public abstract BakedModel getModel(ItemStack stack, @Nullable World world, @Nullable LivingEntity entity, int seed);

    @Shadow @Final private MinecraftClient client;

    public void printInfo(){
        ObjectCollection<ModelIdentifier> mod= models.modelIds.values();
        for(ModelIdentifier id:mod){
            Debug.info(id);
        }

    }
    @ModifyVariable(method =
//            "Lnet.minecraft.client.render.item.ItemRender;getModel(Lnet.minecraft.item.ItemStack;Lnet.minecraft.world.World;Lnet.minecraft.entity.LivingEntity;I)Lnet.minecraft.client.render.model.BakedModel;"
            "getModel"
            , at = @At("HEAD"), index = 1, argsOnly = true)
    public ItemStack onItemModelLoad(ItemStack stack){
        String sfid= SlimefunUtils.getSfId(stack);
        if(sfid!=null){
            int cmd= SlimefunItemModelManager.getCustomModelData(sfid);
            if(cmd==0) return stack;
            ItemStack cloned=stack.copy();
            SlimefunUtils.setCustomModelData(cloned, cmd);

            return cloned;
        }else{

//                   BakedModel model1= BakedModelManagerAccess.of( this.getModels().getModelManager()).getBakedModel(new ModelIdentifier("minecraft","light_blue_wool","inventory"));
            return stack;
        }
    }
//    @Inject(method = "getModel",at=@At("HEAD"), cancellable = true)
//    public void onCustomModelReplace(ItemStack stack, @Nullable World world, @Nullable LivingEntity entity, int seed, CallbackInfoReturnable<BakedModel> ci){
//        Optional<ModelIdentifier> data= RenderUtils.getCustomItemModel(stack);
//        if(data.isPresent()&&!ci.isCancelled()){
//            BakedModelManagerAccess access= BakedModelManagerAccess.of(this.getModels().getModelManager());
//            BakedModel model=access.getBakedModel(data.get());
//            if(model==access.getThisMissingModel()){
//                Identifier id=new Identifier(data.get().getNamespace(),data.get().getPath());
//                model=access.getBakedModel(id);
//            }
//
//            ci.setReturnValue(model);
//            ci.cancel();
//        }
//    }

    @Inject(method = "renderItem",at = @At("RETURN"))
    public void onItemRender(ItemStack item, ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model,CallbackInfo ci){
        ItemStack stack=null;
        try{
            stack= SlimefunUtils.getStorageContent(item);
        }catch (Throwable e){
            Debug.info("An Error occurred while deserialization");
            return;
        }
        if(stack!=null){
            matrices.push();
            try{
                final float scale=0.54f;
                final float scale_ground=0.8f;
                switch(renderMode){
                    case GUI -> {
                        matrices.translate(0.26,-0.26,1f);
                        matrices. scale(scale, scale, scale);
                    }case HEAD -> {
                        return;
                    }case FIRST_PERSON_LEFT_HAND -> {
                        return;
                    }case FIRST_PERSON_RIGHT_HAND -> {
                        return;
                    }case THIRD_PERSON_LEFT_HAND -> {
                        return;
                    }case THIRD_PERSON_RIGHT_HAND -> {
                        return;
                    }case GROUND -> {
                        matrices.translate(0.15,-0.15,0);
                        matrices. scale(scale_ground, scale_ground, scale_ground);
                    }case FIXED -> {
                        matrices.translate(-0.25,-0.25,-0.05);
                        matrices. scale(scale_ground, scale_ground, scale_ground);
                    }case NONE -> {
                        return;
                    }
                }

                BakedModel bakedModel=this.getModel(stack,client.world, client.player, 0);

                DiffuseLighting.enableGuiDepthLighting();
                this.renderItem(stack,renderMode,leftHanded,matrices,vertexConsumers,0xF000F0,overlay,bakedModel);
                DiffuseLighting.disableGuiDepthLighting();
            }finally {
                matrices.pop();
            }
        }

    }
    Random random=new Random();
    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
    public void onObfuscatedItemRender(ItemStack item, ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model,CallbackInfo ci){
        if(item.hasNbt()){
            if(item.getNbt().contains("obfuscated")){
                matrices.translate(random.nextFloat(-3.0f,3.0f),random.nextFloat(-3.0f,3.0f),random.nextFloat(-3.0f,3.0f));
            }
        }

    }

}
