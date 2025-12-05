package me.matl114.mixins.HackMixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.matl114.access.EntityInternalAccess;
import me.matl114.access.LivingEntityAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity implements LivingEntityAccess<PlayerEntity> , EntityInternalAccess<PlayerEntity> {
    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setPosition(DDD)V"))
    private void removePositionXZLimit(PlayerEntity instance, double v, double v2, double v3){
        //do not set
    }

    boolean stopJumpThisTickInternal = false;

    public void stopJumpThisTick(){
        stopJumpThisTickInternal = true;
    }

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void onStopJumpThisTick(CallbackInfo ci){
        if(stopJumpThisTickInternal){
            stopJumpThisTickInternal = false;
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method =  "getBlockBreakingSpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getAttributeValue(Lnet/minecraft/registry/entry/RegistryEntry;)D", ordinal = 1))
    private double onBlockBreakingSpeedAttrWrongValueFix(double original){

        return original < 1E-5? 1.0F:  original;
    }




}
