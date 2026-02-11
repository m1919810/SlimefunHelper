package me.matl114.mixins.hack;

import me.matl114.hacks.RenderTasks;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.BlindnessEffectFogModifier;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlindnessEffectFogModifier.class)
public abstract class BlindnessEffectFogModifierMixin {
    @Inject(
            method = "applyDarknessModifier",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/fog/BlindnessEffectFogModifier;getStatusEffect()Lnet/minecraft/registry/entry/RegistryEntry;"),
            cancellable = true)
    private void applyDarknessModifier(
            LivingEntity cameraEntity, float darkness, float tickProgress, CallbackInfoReturnable<Float> cir) {
        if (RenderTasks.getRenderExtra().noEffect.get()) {
            cir.setReturnValue(darkness);
        }
    }

    @Inject(
            method = "applyStartEndModifier",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/fog/BlindnessEffectFogModifier;getStatusEffect()Lnet/minecraft/registry/entry/RegistryEntry;"),
            cancellable = true)
    private void applyStartEndModifier(
            FogData data,
            Camera camera,
            ClientWorld clientWorld,
            float f,
            RenderTickCounter renderTickCounter,
            CallbackInfo ci) {
        if (RenderTasks.getRenderExtra().noEffect.get()) {
            ci.cancel();
        }
    }
}
