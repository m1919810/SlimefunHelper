package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Objects;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.modules.render.NoRender;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(BackgroundRenderer.class)
public abstract class BackGroundRenderMixin {

    @Inject(method = "getFogModifier", at = @At("HEAD"), cancellable = true)
    private static void getFogModifier(
            Entity entity, float tickDelta, CallbackInfoReturnable<BackgroundRenderer.StatusEffectFogModifier> cir) {
        BackgroundRenderer.StatusEffectFogModifier modifier = cir.getReturnValue();
        if (modifier != null) {
            if (NoRender.INSTANCE.noBlindness()
                    && Objects.equals(modifier.getStatusEffect(), StatusEffects.BLINDNESS)) {
                cir.setReturnValue(null);
            }
            if (NoRender.INSTANCE.noDarkNess() && Objects.equals(modifier.getStatusEffect(), StatusEffects.DARKNESS)) {
                cir.setReturnValue(null);
            }
        }
    }

    @ModifyExpressionValue(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/LivingEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z",
                            ordinal = 0))
    private static boolean render(boolean original) {
        if (RenderTasks.getRenderExtra().nightVision.get()) {
            return true;
        } else {
            return original;
        }
    }

    @ModifyExpressionValue(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/LivingEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z",
                            ordinal = 1))
    private static boolean render2(boolean original) {
        if (NoRender.INSTANCE.noDarkNess()) {
            return false;
        } else {
            return original;
        }
    }

    @ModifyArg(
            method = "applyFog",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFogStart(F)V"))
    private static float applyFog(
            float shaderFogStart,
            @Local(argsOnly = true) BackgroundRenderer.FogType fogType,
            @Local(argsOnly = true, ordinal = 0) float viewDistance) {
        if (fogType == BackgroundRenderer.FogType.FOG_TERRAIN && NoRender.INSTANCE.noDistanceFog()) {
            return viewDistance * 4;
        }
        return shaderFogStart;
    }

    @ModifyArg(
            method = "applyFog",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFogEnd(F)V"))
    private static float applyFogEnd(
            float shaderFogEnd,
            @Local(argsOnly = true) BackgroundRenderer.FogType fogType,
            @Local(argsOnly = true, ordinal = 0) float viewDistance) {
        if (fogType == BackgroundRenderer.FogType.FOG_TERRAIN && NoRender.INSTANCE.noDistanceFog()) {
            return viewDistance * 4;
        }
        return shaderFogEnd;
    }
}
