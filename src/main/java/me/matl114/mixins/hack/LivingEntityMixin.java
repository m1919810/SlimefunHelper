package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.accessors.access.LivingEntityAccess;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.modules.move.ElytraExtra;
import me.matl114.utils.EntityUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements LivingEntityAccess {

    @Shadow
    protected int fallFlyingTicks;

    @Accessor("jumpingCooldown")
    public abstract void setJumpingCooldown(int cooldown);

    @Shadow
    public abstract float getYaw(float tickDelta);

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Shadow
    protected abstract float getJumpVelocity(float st);

    @Shadow
    public abstract boolean isFallFlying();

    @Shadow
    public abstract void remove(RemovalReason reason);

    @Shadow
    public abstract void equipStack(EquipmentSlot slot, ItemStack stack);

    @Shadow
    public abstract void updateLimbs(boolean flutter);

    @Unique
    @Override
    public float getJumpUpwardSpeed(float strength) {
        return getJumpVelocity(1.0f);
    }

    //    @Inject(method = "tick", at = @At(value = "INVOKE", target =
    // "Lnet/minecraft/entity/LivingEntity;isFallFlying()Z", shift = At.Shift.BEFORE))
    //    private void onWriteFlyingTicks(CallbackInfo ci){
    //        if(elytraUnbreakable.get() && fallFlyingTicks > 18){
    //            if(MovTasks.runElytraUnbreakable(this)){
    //                fallFlyingTicks = 0;
    //            }
    //        }
    //    }

    @Inject(
            method = "jump",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/LivingEntity;addVelocityInternal(Lnet/minecraft/util/math/Vec3d;)V",
                            shift = At.Shift.BEFORE),
            cancellable = true)
    private void fixJumpingWhileSprintingBackward(CallbackInfo ci, @Local Vec3d vec3d) {
        if (MovTasks.getSprint().directionalSprint.get()) {
            //            float g = this.getYaw() * 0.017453292F;
            Vec3d rot = EntityUtils.pitchYawToRotation(0.0F, this.getYaw());
            if (rot.x * vec3d.x + rot.z * vec3d.z < 0) {
                // inversed
                this.addVelocityInternal(rot.normalize().multiply(-0.2));
                velocityDirty = true;
                ci.cancel();
            }
        }
    }

    @ModifyExpressionValue(
            method = "travel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getSlipperiness()F"))
    private float onIgnoreSlipperiness(float original) {
        if (MovTasks.getNoSlowDown().blockFrac.get()) {
            return 0.6F;
        } else {
            return original;
        }
    }

    @Inject(
            method = "travel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/LivingEntity;travelInFluid(Lnet/minecraft/util/math/Vec3d;)V",
                            shift = At.Shift.BEFORE),
            cancellable = true)
    private void onWaterGlide(Vec3d movementInput, CallbackInfo ci) {
        if (checkClientPlayer()) {
            if (isFallFlying()
                    && ((ElytraExtra.INSTANCE.canFireworkControlMotion()))
                    && (ElytraExtra.INSTANCE.fireworksLiquidFly.get()
                            || ElytraExtra.INSTANCE.hasFireworkVelocityOverrides())) {
                ci.cancel();
                Vec3d overriding = ElytraExtra.INSTANCE.requestNextOverrideVelocity();
                Vec3d vec3d = this.getVelocity();
                if (overriding != null) {
                    this.setVelocity(overriding);
                } else {
                    this.setVelocity(EntityUtils.calculateGlidingVelocity(
                            (ClientPlayerEntity) (Entity) this, vec3d, this.getRotationVector(), !this.hasNoGravity()));
                }
                ;
                this.move(MovementType.SELF, this.getVelocity());
                this.updateLimbs(this instanceof Flutterer);
            }
        }
    }

    @WrapOperation(
            method = "travel",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/entity/LivingEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V",
                            ordinal = 6))
    private void travelGliding(LivingEntity instance, Vec3d oldVelocity, Operation<Vec3d> original) {
        if (checkClientPlayer()) {
            Vec3d velocity = ElytraExtra.INSTANCE.requestNextOverrideVelocity();
            if (velocity == null) {
                velocity = oldVelocity;
            }
            velocity = ElytraExtra.INSTANCE.clampFireworkSpeedInWeb(velocity);
            original.call(instance, velocity);
            return;
        }
        original.call(instance, oldVelocity);
    }
}
