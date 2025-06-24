package me.matl114.mixins.RenderMixin;

import me.matl114.managers.Configs;
import me.matl114.renders.RenderMain;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRenderMixin {
    //@Inject(method = "updateTargetedEntity",)
    //here update crosshairTarget
    @Unique
    private static final AtomicBoolean doNightVision = Configs.RENDER_CONFIG.getBoolean(Configs.RENDER_NIGHTVISION);
    @Inject(method = "getNightVisionStrength",at = @At("HEAD"),cancellable = true)
    private static void getNightVisionStrength(LivingEntity entity, float tickDelta,CallbackInfoReturnable<Float> cir) {
        if(doNightVision.get()) {
            cir.setReturnValue(1.0F);
        }
    }
    @Unique
    MatrixStack currentMatrixStack;
    @ModifyArg(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V"), order = 0)
    public MatrixStack captureMatrixStack(MatrixStack stack){
        currentMatrixStack = stack;
        return stack;
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", ordinal = 1, shift = At.Shift.AFTER))
    public void renderMore(RenderTickCounter tickCounter, CallbackInfo ci){
        if(currentMatrixStack != null)
            RenderMain.renderMoreTasks(currentMatrixStack);
    }
}
