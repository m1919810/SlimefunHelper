package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.matl114.hacks.modules.render.NoRender;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @ModifyExpressionValue(
            method = "getFovMultiplier",
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerAbilities;flying:Z"))
    private boolean onNoFlyFov(boolean original) {
        if (NoRender.INSTANCE.noFlyFov()) {
            return false;
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "getFovMultiplier",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getAttributeValue(Lnet/minecraft/registry/entry/RegistryEntry;)D"))
    private double onNoSpeedFov(double original) {
        if (NoRender.INSTANCE.noSlowDownFov()) {
            original = Math.max(original, 0.1F);
        }
        if (NoRender.INSTANCE.noSpeedFov()) {
            original = Math.min(original, 0.17F);
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "getFovMultiplier",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isUsingItem()Z"))
    private boolean onNoUseItemFov(boolean original) {
        if (NoRender.INSTANCE.noUseItemFov()) {
            return false;
        }
        return original;
    }
}
