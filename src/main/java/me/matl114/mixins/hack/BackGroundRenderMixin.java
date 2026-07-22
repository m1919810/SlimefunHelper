package me.matl114.mixins.hack;

import java.util.Objects;
import me.matl114.hacks.modules.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.matl114.hacks.RenderTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
        if (RenderTasks.getRenderExtra().noEffect.get()) {
            return false;
        } else {
            return original;
        }
    }
    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true)
    private static void applyFog(
            Camera camera,
            BackgroundRenderer.FogType fogType,
            Vector4f color,
            float viewDistance,
            boolean thickenFog,
            float tickDelta,
            CallbackInfoReturnable<Fog> cir) {
        if (fogType == BackgroundRenderer.FogType.FOG_TERRAIN && NoRender.INSTANCE.noDistanceFog()) {
            Fog fog = cir.getReturnValue();
            if (fog != null) {
                cir.setReturnValue(
                        new Fog(fog.end() * 2, fog.end() * 2, fog.shape(), fog.red(), fog.green(), fog.blue(), 0));
            }
        }
    }
}
