package me.matl114.mixins.hack;

import me.matl114.hacks.modules.render.RenderExtra;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.DarknessEffectFogModifier;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DarknessEffectFogModifier.class)
public abstract class DarknessEffectFogModifierMixin {
    @Inject(
            method = "applyDarknessModifier",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/fog/DarknessEffectFogModifier;getStatusEffect()Lnet/minecraft/registry/entry/RegistryEntry;"),
            cancellable = true)
    private void applyDarknessModifier(
            LivingEntity cameraEntity, float darkness, float tickProgress, CallbackInfoReturnable<Float> cir) {
        if (RenderExtra.INSTANCE.noEffect.get()) {
            cir.setReturnValue(darkness);
        }
    }

    @Inject(
            method = "applyStartEndModifier",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/fog/DarknessEffectFogModifier;getStatusEffect()Lnet/minecraft/registry/entry/RegistryEntry;"),
            cancellable = true)
    private void applyStartEndModifier(
            FogData data,
            Camera camera,
            ClientWorld clientWorld,
            float f,
            RenderTickCounter renderTickCounter,
            CallbackInfo ci) {
        if (RenderExtra.INSTANCE.noEffect.get()) {
            ci.cancel();
        }
    }
}
