package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.matl114.hacks.RenderTasks;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @ModifyArg(method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"), index = 5)
    private float applyFog(float environmentalStart) {
        if(RenderTasks.getRenderExtra().noEffect.get()){
            return 1000000;
        }
        return environmentalStart;
    }

    @ModifyArg(method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"), index = 6)
    private float applyFog2(float value) {
        if(RenderTasks.getRenderExtra().noEffect.get()){
            return 1000000;
        }
        return value;
    }

    @ModifyExpressionValue(method = "getFogColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z", ordinal = 0))
    private boolean applyNightVision(boolean original) {
        if(RenderTasks.getRenderExtra().nightVision.get()){
            return true;
        }
        return original;
    }

    @ModifyExpressionValue(method = "getFogColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z", ordinal = 1))
    private boolean applyNoEffect(boolean original) {
        if(RenderTasks.getRenderExtra().noEffect.get()){
            return false;
        }
        return original;
    }
}
