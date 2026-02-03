package me.matl114.mixins.events;

import me.matl114.events.RenderListener;
import me.matl114.events.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.item.ItemModels;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemModels.class)
@Environment(EnvType.CLIENT)
public abstract class ItemModelsEvents {
    @Inject(method = "getModel(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/client/render/model/BakedModel;",at=@At("HEAD"), cancellable = true)
    public void getCustomItemModel(ItemStack stack, CallbackInfoReturnable<BakedModel> cir) {
        Event<BakedModel> bakedModelEvent = new Event<>(null, true, true, stack);
        RenderListener.getCustomModelOverride().handleValue(bakedModelEvent);
        if(!bakedModelEvent.isCancelled()) {
            BakedModel model = bakedModelEvent.context();
            if(model != null) {
                cir.setReturnValue(model);
                cir.cancel();;
            }
        }
    }
}
