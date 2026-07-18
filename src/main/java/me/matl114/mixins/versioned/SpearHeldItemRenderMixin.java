package me.matl114.mixins.versioned;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import me.matl114.events.RenderListener;
import me.matl114.hacks.modules.combat.SpearEnhance;
import me.matl114.versioned.api.VItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ItemRenderer.class)
public abstract class SpearHeldItemRenderMixin {
    @Inject(
            method =
                    "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/util/math/MatrixStack;push()V",
                            shift = At.Shift.AFTER))
    private void onRenderItem(
            ItemStack stack,
            ItemDisplayContext renderMode,
            boolean leftHanded,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay,
            BakedModel model,
            CallbackInfo ci,
            @Local(argsOnly = true) LocalRef<BakedModel> modelRef) {
        boolean bl = renderMode == ItemDisplayContext.GUI
                || renderMode == ItemDisplayContext.GROUND
                || renderMode == ItemDisplayContext.FIXED;
        if (!bl) {
            Item item = stack.getItem();
            if (item instanceof SwordItem
                    && SpearEnhance.INSTANCE.replaceSpearModel.get()
                    && VItem.getInstance().isSpear(stack)) {
                Identifier id = SpearEnhance.INSTANCE.materialSwordToSpearInHandMap.get(item);
                if (id != null) {
                    var model2 = RenderListener.getCustomModelOf(id);
                    if (model2 != null) {
                        modelRef.set(model2);
                    }
                }
            }
        }
    }
}
