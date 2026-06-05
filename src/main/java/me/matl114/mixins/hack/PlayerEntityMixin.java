package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.GameProfile;
import java.util.Arrays;
import me.matl114.accessors.access.LivingEntityAccess;
import me.matl114.accessors.hacks.EntityInternalAccess;
import me.matl114.accessors.hacks.PlayerInternalAccess;
import me.matl114.hacks.utils.entity.Predictor;
import me.matl114.hacks.utils.entity.PredictorImpl;
import me.matl114.utils.MathUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
                                    "Lnet/minecraft/entity/LivingEntity;<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V",
                            shift = At.Shift.AFTER))
    private void onInit(World world, BlockPos pos, float yaw, GameProfile gameProfile, CallbackInfo ci) {
        predictorImpl = new PredictorImpl(this);
    }

    @Override
    public Predictor getPositionPredictor(){
        if(predictorImpl == null){
            predictorImpl = new PredictorImpl(this);
        }
        return predictorImpl;
    }


    @Inject(method = "tick", at = @At("HEAD"))
    private void positionRecordTick(CallbackInfo ci) {
        if(predictorImpl == null) {
            predictorImpl = new PredictorImpl(this);
        }
        predictorImpl.tick();
    }

    @Override
    public PredictorImpl getPredictorImpl(){
        if(predictorImpl == null){
            predictorImpl = new PredictorImpl(this);
        }
        return predictorImpl;
    }
}
