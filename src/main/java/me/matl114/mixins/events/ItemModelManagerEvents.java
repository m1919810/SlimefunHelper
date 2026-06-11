package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.List;
import javax.annotation.Nullable;
import me.matl114.accessors.events.ItemRenderStateAccess;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import me.matl114.events.model.GuiModel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerEvents {

    @Shadow
    public abstract void update(
            ItemRenderState renderState,
            ItemStack stack,
            ItemDisplayContext transformationMode,
            boolean leftHand,
            @Nullable World world,
            @Nullable LivingEntity entity,
            int seed);

    @Inject(
            method =
                    "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)V",
            at = @At("HEAD"))
    public void onItemModelLoad(
            ItemRenderState renderState,
            ItemStack stack,
            ItemDisplayContext transformationMode,
            World world,
            LivingEntity entity,
            int seed,
            CallbackInfo ci,
            @Local(argsOnly = true) LocalRef<ItemStack> argument) {
        Event<ItemStack> itemStackEvent = new Event<>(stack, true, true);
        RenderListener.getItemDataOverrideForModel().handleValue(itemStackEvent);
        if (!itemStackEvent.isCancelled() && itemStackEvent.context() != stack) {
            argument.set(itemStackEvent.context());
        }
    }

    @ModifyExpressionValue(
            method =
                    "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"))
    public Object onItemModelOverride(Object original, @Local(argsOnly = true) ItemStack stack) {
        Event<Identifier> bakedModelEvent = new Event<>(null, true, true, stack);
        RenderListener.getCustomModelOverride().handleValue(bakedModelEvent);
        if (!bakedModelEvent.isCancelled()) {
            Identifier model = bakedModelEvent.context();
            if (model != null) {
                return model;
            }
        }
        // if no modification, just return the origin, do not return the null
        return original;
    }

    @Inject(
            method =
                    "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/item/model/ItemModel;update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/item/ItemModelManager;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/entity/LivingEntity;I)V"))
    public void onItemRenderDetached(
            ItemRenderState renderState,
            ItemStack stack,
            ItemDisplayContext transformationMode,
            World world,
            LivingEntity entity,
            int seed,
            CallbackInfo ci) {
        List<GuiModel> info = RenderListener.getContainedItemInfo(stack);
        GuiModel modelPack = GuiModel.packOrder(info);
        modelPack.update(
                renderState,
                stack,
                (ItemModelManager) (Object) this,
                displayContext,
                world instanceof ClientWorld cli ? cli : null,
                heldItemContext,
                seed);
    }
}
