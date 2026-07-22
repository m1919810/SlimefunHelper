package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Objects;
import me.matl114.hacks.modules.render.NoRender;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public abstract class WorldRenderMixin {

    @WrapOperation(
            method = "hasBlindnessOrDarkness",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/LivingEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z"))
    public boolean hasBlindnessOrDarkness(
            LivingEntity instance, RegistryEntry<StatusEffect> effect, Operation<Boolean> original) {
        if (NoRender.INSTANCE.noDarkNess() && Objects.equals(effect, StatusEffects.DARKNESS)) {
            return false;
        }
        if (NoRender.INSTANCE.noBlindness() && Objects.equals(effect, StatusEffects.BLINDNESS)) {
            return false;
        }
        return original.call(instance, effect);
    }

    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void noRenderRainSnow(
            LightmapTextureManager manager,
            float tickDelta,
            double cameraX,
            double cameraY,
            double cameraZ,
            CallbackInfo ci) {
        if (NoRender.INSTANCE.noWeather()) {
            ci.cancel();
        }
    }
}
