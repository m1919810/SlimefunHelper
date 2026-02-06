package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.accessors.access.LivingEntityAccess;
import me.matl114.hacks.MovTasks;
import me.matl114.utils.EntityUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin  extends Entity implements LivingEntityAccess {

    @Shadow
    protected int fallFlyingTicks;

    @Accessor("jumpingCooldown")
    public abstract void setJumpingCooldown(int cooldown);

    @Shadow public abstract float getYaw(float tickDelta);



    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }
    @Shadow
    protected abstract float getJumpVelocity(float st);
    @Unique
    @Override
    public float getJumpUpwardSpeed(float strength){
        return getJumpVelocity(1.0f);
    }


//    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isFallFlying()Z", shift = At.Shift.BEFORE))
//    private void onWriteFlyingTicks(CallbackInfo ci){
//        if(elytraUnbreakable.get() && fallFlyingTicks > 18){
//            if(MovTasks.runElytraUnbreakable(this)){
//                fallFlyingTicks = 0;
//            }
//        }
//    }

    @Inject(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;addVelocityInternal(Lnet/minecraft/util/math/Vec3d;)V", shift = At.Shift.BEFORE), cancellable = true)
    private void fixJumpingWhileSprintingBackward(CallbackInfo ci, @Local Vec3d vec3d){
        if(MovTasks.getSprint().directionalSprint.get()){
//            float g = this.getYaw() * 0.017453292F;
            Vec3d rot = EntityUtils.pitchYawToRotation(0.0F, this.getYaw());
            if(rot.x * vec3d.x + rot.z * vec3d.z < 0){
                //inversed
                this.addVelocityInternal(rot.normalize().multiply(-0.2));
                velocityDirty = true;
                ci.cancel();
            }
        }
    }




    @ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getSlipperiness()F"))
    private float onIgnoreSlipperiness(float original){
        if(MovTasks.getNoSlowDown().blockFrac.get()){
            return 0.6F;
        }else{
            return original;
        }
    }

}
