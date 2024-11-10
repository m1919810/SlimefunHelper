package me.matl114.SlimefunMixin;

import me.matl114.Access.BakedModelManagerAccess;
import me.matl114.Utils.RenderUtils;
import net.minecraft.client.render.item.ItemModels;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemModels.class)
public abstract class CustomNbtItemModelMixin {
    @Shadow @Final private BakedModelManager modelManager;

    @Inject(method = "getModel(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/client/render/model/BakedModel;",at=@At("HEAD"), cancellable = true)
    public void getCustomItemModel(ItemStack stack, CallbackInfoReturnable<BakedModel> cir) {
        var re= RenderUtils.getCustomItemModel(stack);
        if(re.isPresent()){
            BakedModelManagerAccess access= BakedModelManagerAccess.of(this.modelManager);
            BakedModel model=access.getBakedModel(re.get());
            if(model==access.getThisMissingModel()){
                Identifier id=new Identifier(re.get().getNamespace(),re.get().getPath());
                model=access.getBakedModel(id);
            }

            cir.setReturnValue(model);
            cir.cancel();
        }
    }
}
