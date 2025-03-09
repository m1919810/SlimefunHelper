package me.matl114.SlimefunMixin.RenderMixin;

import me.matl114.ManageUtils.Configs;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public abstract class WorldRenderMixin {
    @Unique
    private static final AtomicBoolean noEffect = Configs.RENDER_CONFIG.getBoolean(Configs.RENDER_NO_EFFECT);
    @Inject(method = "hasBlindnessOrDarkness",at = @At("HEAD"),cancellable = true)
    public void hasBlindnessOrDarkness(CallbackInfoReturnable<Boolean> cir) {
        if(noEffect.get()) {
            cir.setReturnValue(false);
        }
    }
}
