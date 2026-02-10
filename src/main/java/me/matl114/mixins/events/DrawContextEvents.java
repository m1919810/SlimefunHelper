package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.events.GlobalEventVars;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DrawContext.class)
public abstract class DrawContextEvents {
    @Shadow
    @Final
    private MinecraftClient client;

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
