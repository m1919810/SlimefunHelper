package me.matl114.mixins.RenderMixin;

import me.matl114.access.ItemRendererAccess;
import me.matl114.ModConfig;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.renders.RenderMain;
import me.matl114.utils.Debug;
import me.matl114.renders.SlimefunCustomModelManager;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.RenderUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemModels;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
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


//    public void printInfo(){
//        ObjectCollection<ModelIdentifier> mod= models.modelIds.values();
//        for(ModelIdentifier id:mod){
//            Debug.info(id);
//        }
//
//    }
    private final Config.FlagRef sfCmdOverride = Configs.MODEL_CONFIG.getBoolean(Configs.SLIMEFUN_MODEL_ID);
    @ModifyVariable(method =
//            "Lnet.minecraft.client.render.item.ItemRender;getModel(Lnet.minecraft.item.ItemStack;Lnet.minecraft.world.World;Lnet.minecraft.entity.LivingEntity;I)Lnet.minecraft.client.render.model.BakedModel;"
            "getModel"
            , at = @At("HEAD"), index = 1, argsOnly = true)
    public ItemStack onItemModelLoad(ItemStack stack){
        if(sfCmdOverride.get()){
            int specialCmd = SlimefunCustomModelManager.getOverridingModelData(stack);
            if(specialCmd > 0){
                ItemStack cloned=stack.copy();
                ItemStackUtils.setCustomModelData(cloned, specialCmd);
                return cloned;
            }
            String sfid= ItemStackUtils.getSfId(stack);
            if(sfid!=null){
                int cmd= SlimefunCustomModelManager.getCustomModelData(sfid);
                if(cmd==0) return stack;
                ItemStack cloned=stack.copy();
                ItemStackUtils.setCustomModelData(cloned, cmd);

                return cloned;
            }
        }
        return stack;
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
    private final Config.FlagRef enableStorageItemDisplay = Configs.MODEL_CONFIG.getBoolean(Configs.ENABLE_STORAGE_DISPLAY);

    @Inject(method = "renderItem",at = @At("RETURN"))
    public void onItemRender(ItemStack item, ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model,CallbackInfo ci){
        if(enableStorageItemDisplay.get()){
            ItemStack stack=null;
            try{
                stack= RenderMain.getContainedItemInfo(item);
            }catch (Throwable e){
                Debug.info("An Error occurred while deserialization");
                return;
            }
            if(stack!=null){
                RenderUtils.renderItemAt((ItemRenderer) (Object)this, matrices, renderMode, stack, leftHanded, vertexConsumers, overlay);
            }
        }
    }


    Random random=new Random();
    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
    public void onObfuscatedItemRender(ItemStack item, ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model,CallbackInfo ci){

        if(ItemStackUtils.getCustomDataReadOnly(item).contains("obfuscated")){
            matrices.translate(random.nextFloat(-3.0f,3.0f),random.nextFloat(-3.0f,3.0f),random.nextFloat(-3.0f,3.0f));
        }

    }
//    @Inject(method = "renderBakedItemQuads",at = @At("HEAD"))
//    public void checkInject(MatrixStack matrices, VertexConsumer vertices, List<BakedQuad> quads, ItemStack stack, int light, int overlay, CallbackInfo ci){
//        Debug.info("render called");
//    }
//
//    @Redirect(method = "renderBakedItemQuads",at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isEmpty()Z"))
//    public boolean shouldRenderLayerColors(ItemStack instance){
//        if(instance.isEmpty()){
//            return true;
//        }
//        boolean bl = RenderMain.shouldStopVanillaColoring(instance);
//        Debug.info("checking colorRender",bl);
//        return bl;
//    }
}
