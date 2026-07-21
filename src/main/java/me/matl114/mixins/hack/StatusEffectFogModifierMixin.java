package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Objects;
import me.matl114.hacks.modules.render.NoRender;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.fog.StatusEffectFogModifier;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(StatusEffectFogModifier.class)
public abstract class StatusEffectFogModifierMixin {
    @WrapOperation(
            method = "shouldApply",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/LivingEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z"))
    private boolean onNoRenderEffect(
            LivingEntity instance, RegistryEntry<StatusEffect> effect, Operation<Boolean> original) {
        if (NoRender.INSTANCE.noDarkNess() && Objects.equals(effect, StatusEffects.DARKNESS)) {
            return false;
        }
        if (NoRender.INSTANCE.noBlindness() && Objects.equals(effect, StatusEffects.BLINDNESS)) {
            return false;
        }
        return original.call(instance, effect);
    }
}
