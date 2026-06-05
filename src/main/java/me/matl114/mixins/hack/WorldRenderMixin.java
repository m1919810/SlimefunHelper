package me.matl114.mixins.hack;

import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.modules.render.RenderExtra;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public abstract class WorldRenderMixin {

    @Inject(method = "hasBlindnessOrDarkness", at = @At("HEAD"), cancellable = true)
    public void hasBlindnessOrDarkness(CallbackInfoReturnable<Boolean> cir) {
        if (RenderExtra.INSTANCE.noEffect.get()) {
            cir.setReturnValue(false);
        }
    }
}
