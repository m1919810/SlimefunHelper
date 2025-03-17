package me.matl114.mixins.RenderMixin;

import me.matl114.managers.Configs;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
}
