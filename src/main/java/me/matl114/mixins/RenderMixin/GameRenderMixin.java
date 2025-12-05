package me.matl114.mixins.RenderMixin;

import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.managers.Config;
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
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRenderMixin {

    @Unique
    private static final Config.FlagRef doNightVision = Configs.RENDER_CONFIG.getBoolean(Configs.RENDER_NIGHTVISION);
    @Inject(method = "getNightVisionStrength",at = @At("HEAD"),cancellable = true)
    private static void getNightVisionStrength(LivingEntity entity, float tickDelta,CallbackInfoReturnable<Float> cir) {
        if(doNightVision.get()) {
            cir.setReturnValue(1.0F);
        }
    }


    @Inject(
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/render/GameRenderer;renderHand:Z",
            opcode = Opcodes.GETFIELD,
            ordinal = 0),
        method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V")
    public void renderMore(RenderTickCounter tickCounter, CallbackInfo ci, @Local(ordinal = 1) Matrix4f matrix4f2, @Local(ordinal = 1) float tickDelta){
        MatrixStack matrixStack = new MatrixStack();
        matrixStack.multiplyPositionMatrix(matrix4f2);
        //fixme: Event

        RenderMain.renderMoreTasks(matrixStack, tickDelta);
    }


}
