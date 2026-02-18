package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.hacks.RenderTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FogShape;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(BackgroundRenderer.class)
public abstract class BackGroundRenderMixin {
    @Shadow
    @Nullable
    protected static BackgroundRenderer.StatusEffectFogModifier getFogModifier(Entity entity, float tickDelta) {
        return null;
    }

    @Inject(method = "getFogModifier", at = @At("HEAD"), cancellable = true)
    private static void getFogModifier0(
            Entity entity, float tickDelta, CallbackInfoReturnable<BackgroundRenderer.StatusEffectFogModifier> cir) {
        if (RenderTasks.getRenderExtra().noEffect.get()) {
            cir.setReturnValue(null);
        }
    }

    @WrapOperation(
            method = "applyFog",
            at =
                    @At(
                            value = "NEW",
                            target = "(FFLnet/minecraft/client/render/FogShape;FFFF)Lnet/minecraft/client/render/Fog;"))
    private static Fog applyFogNoEffect(
            float start,
            float end,
            FogShape shape,
            float red,
            float green,
            float blue,
            float alpha,
            Operation<Fog> original,
            @Local(argsOnly = true) Camera camera,
            @Local(argsOnly = true) BackgroundRenderer.FogType fogType,
            @Local(argsOnly = true) Vector4f color,
            @Local(argsOnly = true, ordinal = 0) float viewDistance,
            @Local(argsOnly = true) boolean thickenFog,
            @Local(argsOnly = true, ordinal = 1) float tickDelta) {
        if (!RenderTasks.getRenderExtra().noEffect.get() || fogType != BackgroundRenderer.FogType.FOG_TERRAIN)
            return original.call(start, end, shape, red, green, blue, alpha);

        CameraSubmersionType cameraSubmersionType = camera.getSubmersionType();
        if (cameraSubmersionType != CameraSubmersionType.NONE)
            return original.call(start, end, shape, red, green, blue, alpha);

        Entity entity = camera.getFocusedEntity();
        if (getFogModifier(entity, tickDelta) != null) return original.call(start, end, shape, red, green, blue, alpha);

        return original.call(start, end, shape, 0F, 0F, 0F, 0F);
    }
}
