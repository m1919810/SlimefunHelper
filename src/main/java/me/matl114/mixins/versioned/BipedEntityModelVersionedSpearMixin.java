package me.matl114.mixins.versioned;

import me.matl114.hacks.modules.combat.SpearEnhance;
import me.matl114.versioned.accessors.PlayerEntityRendererStateAccess;
import me.matl114.versioned.impl.LancingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.util.Arm;
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
    private void positionRightArm(BipedEntityRenderState state, BipedEntityModel.ArmPose armPose, CallbackInfo ci) {
        if (state instanceof PlayerEntityRendererStateAccess acc
                && acc.getSpearingHand() == Arm.RIGHT
                && acc.getSpearingItem() != null
                && SpearEnhance.INSTANCE.fixOldVersionSpear.get()) {
            LancingUtils.positionArmForSpear(rightArm, head, true, acc.getSpearingItem(), state);
        }
    }

    @Inject(method = "positionLeftArm", at = @At("RETURN"))
    private void positionLefgArm(BipedEntityRenderState state, BipedEntityModel.ArmPose armPose, CallbackInfo ci) {
        if (state instanceof PlayerEntityRendererStateAccess acc
                && acc.getSpearingHand() == Arm.RIGHT
                && acc.getSpearingItem() != null
                && SpearEnhance.INSTANCE.fixOldVersionSpear.get()) {
            LancingUtils.positionArmForSpear(leftArm, head, false, acc.getSpearingItem(), state);
        }
    }
}
