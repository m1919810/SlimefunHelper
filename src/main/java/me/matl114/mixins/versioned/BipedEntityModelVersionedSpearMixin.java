package me.matl114.mixins.versioned;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.hacks.modules.combat.SpearEnhance;
import me.matl114.hacks.modules.move.ElytraExtra;
import me.matl114.versioned.impl.LancingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelVersionedSpearMixin {
    @Shadow
    @Final
    public ModelPart leftArm;

    @Shadow
    @Final
    public ModelPart head;

    @Shadow
    @Final
    public ModelPart rightArm;

    @Inject(method = "positionRightArm", at = @At("RETURN"))
    private void positionRightArm(LivingEntity entity, CallbackInfo ci) {
        if (entity instanceof PlayerEntity pl
                && SpearEnhance.INSTANCE.fixOldVersionSpear.get()
                && SpearEnhance.isUsingSpear(pl)) {
            Hand hand = pl.getActiveHand();
            Arm arm = hand == Hand.MAIN_HAND ? pl.getMainArm() : pl.getMainArm().getOpposite();
            if (arm == Arm.RIGHT) {
                LancingUtils.positionArmForSpear(rightArm, head, true, pl.getActiveItem(), pl);
            }
        }
    }

    @Inject(method = "positionLeftArm", at = @At("RETURN"))
    private void positionLefgArm(LivingEntity entity, CallbackInfo ci) {
        if (entity instanceof PlayerEntity pl
                && SpearEnhance.INSTANCE.fixOldVersionSpear.get()
                && SpearEnhance.isUsingSpear(pl)) {
            Hand hand = pl.getActiveHand();
            Arm arm = hand == Hand.MAIN_HAND ? pl.getMainArm() : pl.getMainArm().getOpposite();
            if (arm == Arm.LEFT) {
                LancingUtils.positionArmForSpear(leftArm, head, false, pl.getActiveItem(), pl);
            }
        }
    }

    @ModifyExpressionValue(
            method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getFallFlyingTicks()I"))
    private int setAnglesFallFlyingTicks(int original, @Local(argsOnly = true) LivingEntity p) {
        if (original > 0 && p == MinecraftClient.getInstance().player) {
            if (ElytraExtra.INSTANCE.isCurrentArmorGliding() && ElytraExtra.INSTANCE.renderFix.get()) {
                return 0;
            }
        }
        return original;
    }
}
