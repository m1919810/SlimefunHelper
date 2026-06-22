package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
@Environment(EnvType.CLIENT)
public abstract class GameRendererEvents {

    @ModifyExpressionValue(
            method = "renderWorld",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;",
                            ordinal = 0))
    private Object onRenderWorld(Object original, @Local MatrixStack matrixStack) {
        if (original instanceof Boolean bl) {
            Event<MatrixStack> event = new Event<>(matrixStack, true, false);
            if (!bl) {
                event.cancel();
            }
            RenderListener.getApplyWorldBobView().handleValue(event);
            return !event.isCancelled();
        }
        return null;
    }

    @ModifyArg(
            method = "renderWorld",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/WorldRenderer;setupFrustum(Lnet/minecraft/util/math/Vec3d;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"),
            index = 2)
    private Matrix4f captureFrustum(Matrix4f matrix4f) {
        RenderListener.setWorldProjectionMatrix(new Matrix4f(matrix4f));
        return matrix4f;
    }

    @Inject(
            method = "renderWorld",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void captureBasicProjectionMatrix(
            RenderTickCounter renderTickCounter,
            CallbackInfo ci,
            @Local(ordinal = 1) Matrix4f positionMatrix,
            @Local(ordinal = 0) Matrix4f basicProjection) {
        RenderListener.setWorldModelViewMatrix(new Matrix4f(positionMatrix));
        RenderListener.setWorldBasicProjectionMatrix(new Matrix4f(basicProjection));
    }

    @Inject(
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                            shift = At.Shift.AFTER),
            method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V")
    public void renderMore(
            RenderTickCounter tickCounter,
            CallbackInfo ci,
            @Local(ordinal = 1) Matrix4f matrix4f2,
            @Local(ordinal = 1) float tickDelta) {
        MatrixStack matrixStack = new MatrixStack();
        matrixStack.multiplyPositionMatrix(matrix4f2);
        // fixme: Event

        RenderListener.renderWorldTasks(matrixStack, tickDelta);
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    public void onGetFov(CallbackInfoReturnable<Double> cir) {
        float fov = (float) cir.getReturnValueD();
        Event<Float> fovEvent = new Event<>(fov, false, true);
        RenderListener.getFovGetListener().handleValue(fovEvent);
        double fov2 = fovEvent.context;
        if (fov2 != fov) {
            cir.setReturnValue(fov2);
            return;
        }
    }
}
