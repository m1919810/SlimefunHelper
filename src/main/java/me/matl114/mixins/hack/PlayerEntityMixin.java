package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.GameProfile;
import me.matl114.accessors.access.LivingEntityAccess;
import me.matl114.accessors.hacks.EntityInternalAccess;
import me.matl114.accessors.hacks.PlayerInternalAccess;
import me.matl114.hacks.modules.combat.CombatExtra;
import me.matl114.hacks.modules.mine.MineExtra;
import me.matl114.hacks.utils.entity.Predictor;
import me.matl114.hacks.utils.entity.PredictorImpl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity
        implements LivingEntityAccess<PlayerEntity>, EntityInternalAccess<PlayerEntity>, PlayerInternalAccess {
    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @ModifyExpressionValue(
            method = "getBlockBreakingSpeed",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/player/PlayerEntity;getAttributeValue(Lnet/minecraft/registry/entry/RegistryEntry;)D",
                            ordinal = 1))
    private double onBlockBreakingSpeedAttrWrongValueFix(double original) {

        return original < 1E-5 ? 1.0F : original;
    }

    @Unique
    PredictorImpl predictorImpl;

    @Inject(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/PlayerLikeEntity;<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V",
                            shift = At.Shift.AFTER))
    private void onInit(World world, GameProfile profile, CallbackInfo ci) {
        predictorImpl = new PredictorImpl(this);
    }

    @Override
    public Predictor getPositionPredictor() {
        if (predictorImpl == null) {
            predictorImpl = new PredictorImpl(this);
        }
        return predictorImpl;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void positionRecordTick(CallbackInfo ci) {
        if (predictorImpl == null) {
            predictorImpl = new PredictorImpl(this);
        }
        predictorImpl.tick();
    }

    @Override
    public PredictorImpl getPredictorImpl() {
        if (predictorImpl == null) {
            predictorImpl = new PredictorImpl(this);
        }
        return predictorImpl;
    }

    @Inject(method = "getBlockInteractionRange", at = @At("RETURN"), cancellable = true)
    private void getBlockInteractionRange(CallbackInfoReturnable<Double> cir) {
        if (MineExtra.INSTANCE.reachDistance.get() > 1E-6) {
            cir.setReturnValue(MineExtra.INSTANCE.getReachDistance());
        }
    }

    @Inject(method = "getEntityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void getEntityInteractionRange(CallbackInfoReturnable<Double> cir) {
        if (CombatExtra.INSTANCE.range.get() > 0.1) {
            cir.setReturnValue(CombatExtra.INSTANCE.getAttackRange());
        }
    }
}
