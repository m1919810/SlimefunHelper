package me.matl114.mixins.RenderMixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LightmapTextureManager.class)
public abstract class LightMapTextureMixin {
    @Unique
    private static final Config.FlagRef noEffect = Configs.RENDER_CONFIG.getBoolean(Configs.RENDER_NO_EFFECT);
    @Unique
    private static final Config.FlagRef doNightVision = Configs.RENDER_CONFIG.getBoolean(Configs.RENDER_NIGHTVISION);
    @Inject(method = "getDarknessFactor",at = @At("HEAD"),cancellable = true)
    private void getDarknessFactor(CallbackInfoReturnable<Float> cir) {
        if(noEffect.get()) {
            cir.setReturnValue(0.0F);
        }
    }
    @ModifyExpressionValue(method = "update",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z",ordinal = 0))
    public boolean alwaysNightVision(boolean original) {
        if(doNightVision.get()) {
            return true;
        }
        return original;
    }

}
