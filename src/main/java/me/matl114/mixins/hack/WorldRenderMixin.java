package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Objects;
import me.matl114.hacks.modules.render.NoRender;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

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

    @WrapWithCondition(
            method = "method_62216",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/WeatherRendering;renderPrecipitation(Lnet/minecraft/world/World;Lnet/minecraft/client/render/VertexConsumerProvider;IFLnet/minecraft/util/math/Vec3d;)V"))
    private boolean noRenderRainSnow(
            WeatherRendering instance,
            World world,
            VertexConsumerProvider vertexConsumers,
            int ticks,
            float delta,
            Vec3d pos) {
        if (NoRender.INSTANCE.noWeather()) {
            return false;
        }
        return true;
    }
}
