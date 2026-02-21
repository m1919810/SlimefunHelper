package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.events.GlobalEventVars;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrawContext.class)
public abstract class DrawContextEvents {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(
            method =
                    "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIII)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/item/ItemRenderState;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
                            shift = At.Shift.BEFORE))
    private void onResetGuiLightFlag(
            LivingEntity entity, World world, ItemStack stack, int x, int y, int seed, int z, CallbackInfo ci) {
        GlobalEventVars.fetchThisTimeGuiLightStatus();
    }

    @WrapOperation(
            method =
                    "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIII)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;draw()V"))
    private void onCorrectingGuiLight(DrawContext instance, Operation<Void> original, @Local boolean bl) {
        boolean status = GlobalEventVars.fetchThisTimeGuiLightStatus();
        if (status && !bl) {
            // attach gui lightening fix
            DiffuseLighting.disableGuiDepthLighting();
            original.call(instance);
            DiffuseLighting.enableGuiDepthLighting();
        } else {
            original.call(instance);
        }
    }
}
