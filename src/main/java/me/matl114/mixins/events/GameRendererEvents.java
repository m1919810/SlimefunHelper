package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.events.Event;
import me.matl114.events.RenderListener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
@Environment(EnvType.CLIENT)
public abstract class GameRendererEvents {
    @Shadow public abstract void tick();

    @Shadow public abstract Matrix4f getBasicProjectionMatrix(float fovDegrees);

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void captureBasicProjectionMatrix(RenderTickCounter renderTickCounter, CallbackInfo ci, @Local(ordinal = 2) Matrix4f positionMatrix,
                                              @Local(ordinal = 1) Matrix4f projectionMatrix, @Local(ordinal = 0) Matrix4f basicProjection) {
        RenderListener.setWorldModelViewMatrix(new Matrix4f(positionMatrix));
        RenderListener.setWorldProjectionMatrix(new Matrix4f(projectionMatrix));
        RenderListener.setWorldBasicProjectionMatrix(new Matrix4f(basicProjection));
    }

    @Inject(
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                            shift = At.Shift.AFTER),
            method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V")
    public void renderMore(
            RenderTickCounter tickCounter,
            CallbackInfo ci,
            @Local(ordinal = 2) Matrix4f positionMatrix) {
        MatrixStack matrixStack = new MatrixStack();
        matrixStack.multiplyPositionMatrix(positionMatrix);
        // fixme: Event

        RenderListener.renderWorldTasks(matrixStack, tickCounter.getTickDelta(false));
    }



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
}
