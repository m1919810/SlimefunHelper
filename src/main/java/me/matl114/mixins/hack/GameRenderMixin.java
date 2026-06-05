package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.modules.render.RenderExtra;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRenderMixin {

    @Inject(method = "getNightVisionStrength", at = @At("HEAD"), cancellable = true)
    private static void getNightVisionStrength(
            LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (RenderExtra.INSTANCE.nightVision.get()) {
            cir.setReturnValue(1.0F);
        }
    }

    @ModifyExpressionValue(
            method = "renderWorld",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z"))
    public boolean noRenderNausea(boolean original) {
        if (RenderTasks.getRenderExtra().noNausea.get()) {
            return false;
        }
        return original;
    }
}
