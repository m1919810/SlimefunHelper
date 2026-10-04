package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.hacks.modules.render.HandView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @WrapOperation(
            method = "renderFirstPersonItem",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"))
    private void applyHandTransform(
            HeldItemRenderer instance,
            LivingEntity entity,
            ItemStack item,
            ItemDisplayContext renderMode,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            Operation<Void> original) {
        if (HandView.INSTANCE == null || !HandView.INSTANCE.isActive()) {
            original.call(instance, entity, item, renderMode, matrices, queue, light);
            return;
        }

        Hand hand = renderMode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
        matrices.push();
        HandView.INSTANCE.applyHandTransform(matrices, hand);
        original.call(instance, entity, item, renderMode, matrices, queue, light);
        matrices.pop();
    }
}
