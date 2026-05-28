package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import javax.annotation.Nullable;
import me.matl114.accessors.events.ItemRenderStateAccess;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerEvents {
    @Shadow
    public abstract void clearAndUpdate(
            ItemRenderState renderState,
            ItemStack stack,
            ItemDisplayContext displayContext,
            @Nullable World world,
            @Nullable HeldItemContext heldItemContext,
            int seed);

    @Inject(method = "update", at = @At("HEAD"))
    public void onItemModelLoad(
            ItemRenderState renderState,
            ItemStack stack,
            ItemDisplayContext displayContext,
            World world,
            HeldItemContext heldItemContext,
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
            method = "update",
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
            method = "update",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/item/model/ItemModel;update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/item/ItemModelManager;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/util/HeldItemContext;I)V"))
    public void onItemRenderDetached(
            ItemRenderState renderState,
            ItemStack stack,
            ItemDisplayContext displayContext,
            World world,
            HeldItemContext heldItemContext,
            int seed,
            CallbackInfo ci) {
        ItemStack info = RenderListener.getContainedItemInfo(stack);
        if (info != null) {
            ItemRenderStateAccess stateAccess = ItemRenderStateAccess.of(renderState);
            ItemRenderState state2 = stateAccess.getAttachedRenderState();
            if (state2 == null) {
                state2 = new ItemRenderState();
            }
            // init attached info
            clearAndUpdate(state2, info, displayContext, world, heldItemContext, seed);
            ItemRenderStateAccess.of(renderState).setAttachedRenderState(state2);
        } else {
            ItemRenderStateAccess.of(renderState).setAttachedRenderState(null);
        }
    }
}
