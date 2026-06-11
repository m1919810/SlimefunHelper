package me.matl114.mixins.versioned;

import me.matl114.hacks.modules.combat.SpearEnhance;
import me.matl114.versioned.accessors.PlayerEntityRendererStateAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererVersionedSpearMixin {
    @Unique
    Hand spearHand = null;

    @Inject(
            method =
                    "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("RETURN"))
    private void onSpearUpdate(
            AbstractClientPlayerEntity abstractClientPlayerEntity,
            PlayerEntityRenderState playerEntityRenderState,
            float f,
            CallbackInfo ci) {
        PlayerEntityRendererStateAccess access = (PlayerEntityRendererStateAccess) playerEntityRenderState;
        if (SpearEnhance.INSTANCE.fixOldVersionSpear.get() && SpearEnhance.isUsingSpear(abstractClientPlayerEntity)) {
            Arm mainArm = abstractClientPlayerEntity.getMainArm();

            access.setSpearingHand(
                    abstractClientPlayerEntity.getActiveHand() == Hand.MAIN_HAND ? mainArm : mainArm.getOpposite());
            access.setSpearingItem(abstractClientPlayerEntity.getActiveItem());
        } else {
            access.setSpearingHand(null);
            access.setSpearingItem(null);
        }
    }
}
