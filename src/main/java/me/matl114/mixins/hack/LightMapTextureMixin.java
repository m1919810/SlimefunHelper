package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.matl114.hacks.modules.render.NoRender;
import me.matl114.hacks.modules.render.RenderExtra;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LightmapTextureManager.class)
public abstract class LightMapTextureMixin {
    @Inject(method = "getDarknessFactor", at = @At("HEAD"), cancellable = true)
    private void getDarknessFactor(CallbackInfoReturnable<Float> cir) {
        if (NoRender.INSTANCE.noDarkNess()) {
            cir.setReturnValue(0.0F);
        }
    }

    @ModifyExpressionValue(
            method = "update",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/ClientPlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z",
                            ordinal = 0))
    public boolean alwaysNightVision(boolean original) {
        if (RenderExtra.INSTANCE.nightVision.get()) {
            return true;
        }
        return original;
    }
}
