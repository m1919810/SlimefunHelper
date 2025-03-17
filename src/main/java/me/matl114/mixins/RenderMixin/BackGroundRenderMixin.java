package me.matl114.mixins.RenderMixin;

import me.matl114.managers.Configs;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
@Mixin(BackgroundRenderer.class)
public abstract class BackGroundRenderMixin {
    @Unique
    private static final AtomicBoolean noEffect = Configs.RENDER_CONFIG.getBoolean(Configs.RENDER_NO_EFFECT);
    @Unique
    private static final AtomicBoolean doNightVision = Configs.RENDER_CONFIG.getBoolean(Configs.RENDER_NIGHTVISION);
    @Inject(method = "getFogModifier",at = @At("HEAD"), cancellable = true)
    private static void getFogModifier(Entity entity, float tickDelta,CallbackInfoReturnable<BackgroundRenderer.StatusEffectFogModifier> cir) {
        if(noEffect.get()) {
            cir.setReturnValue(null);
        }
    }
    @Redirect(method = "render",at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;hasStatusEffect(Lnet/minecraft/entity/effect/StatusEffect;)Z",ordinal = 0))
    private static boolean render(LivingEntity instance, StatusEffect effect) {
        if(doNightVision.get()) {
            return true;
        }else {
            return instance.hasStatusEffect(effect);
        }
    }
    @Redirect(method = "render",at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;hasStatusEffect(Lnet/minecraft/entity/effect/StatusEffect;)Z",ordinal = 1))
    private static boolean render2(LivingEntity instance, StatusEffect effect) {
        if(noEffect.get()) {
            return false;
        }else {
            return instance.hasStatusEffect(effect);
        }
    }

}
