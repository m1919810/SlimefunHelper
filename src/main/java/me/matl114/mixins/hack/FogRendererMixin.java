package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.hacks.modules.render.NoRender;
import me.matl114.hacks.modules.render.RenderExtra;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @ModifyExpressionValue(
            method = "getFogColor",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/LivingEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z",
                            ordinal = 0))
    private boolean applyNightVision(boolean original) {
        if (RenderExtra.INSTANCE.nightVision.get()) {
            return true;
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "getFogColor",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/LivingEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z",
                            ordinal = 1))
    private boolean applyNoEffect(boolean original) {
        if (RenderExtra.INSTANCE != null && RenderExtra.INSTANCE.nightVision.get()) {
            return false;
        }
        return original;
    }

    @Inject(method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", shift = At.Shift.BEFORE))
    private void applyFog(Camera camera, int viewDistance, RenderTickCounter renderTickCounter, float f, ClientWorld clientWorld, CallbackInfoReturnable<Vector4f> cir, @Local FogData fogData, @Local Vector4f color) {
        if(NoRender.INSTANCE.noDistanceFog()){
            fogData.environmentalStart = Float.MAX_VALUE;
            fogData.environmentalEnd = Float.MAX_VALUE;
            fogData.renderDistanceStart = Float.MAX_VALUE;
            fogData.renderDistanceEnd = Float.MAX_VALUE;
            color.w = 0;
        }
    }
}
