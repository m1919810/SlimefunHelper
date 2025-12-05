package me.matl114.mixins.HackMixin;

import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.access.EntityInternalAccess;
import me.matl114.access.LivingEntityAccess;
import me.matl114.hackUtils.MovTasks;
import me.matl114.listenerUtils.Listener;
import me.matl114.managers.Config;
import me.matl114.managers.Configs;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.UtilClass.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
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
public abstract class LivingEntityMixin  extends Entity implements LivingEntityAccess, EntityInternalAccess<LivingEntity> {

    @Shadow
    protected int fallFlyingTicks;

    @Accessor("jumpingCooldown")
    public abstract void setJumpingCooldown(int cooldown);

    @Shadow public abstract float getYaw(float tickDelta);

    @Shadow private int jumpingCooldown;

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

    @Unique
    private static final Config.FlagRef elytraUnbreakable = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_UNBREAKABLE_ELYTRA);
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isFallFlying()Z", shift = At.Shift.BEFORE))
    private void onWriteFlyingTicks(CallbackInfo ci){
        if(elytraUnbreakable.get() && fallFlyingTicks > 18){
            if(MovTasks.runElytraUnbreakable(this)){
                fallFlyingTicks = 0;
            }
        }
    }
    @Unique
    private static final Config.FlagRef legalDirectional = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_ALL_DIRECTION_SPRINT);
    @Inject(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;addVelocityInternal(Lnet/minecraft/util/math/Vec3d;)V", shift = At.Shift.BEFORE), cancellable = true)
    private void fixJumpingWhileSprintingBackward(CallbackInfo ci, @Local Vec3d vec3d){
        if(legalDirectional.get()){
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
    @Unique
    Integer nextJumpCooldown;

    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;jump()V", shift = At.Shift.BEFORE))
    private void onJump(CallbackInfo ci){
        if((Entity)this == ((Entity) MinecraftClient.getInstance().player)){
            //10 sec
            Event<Integer> jumpEvent = new Event<>(10, true, true );
            Listener.getPlayerNotFlyJumpPoint().handleValue(jumpEvent);
            nextJumpCooldown = jumpEvent.context();
            if(jumpEvent.isCancelled()){
                this.stopJumpThisTick();
            }
        }
    }
    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getWorld()Lnet/minecraft/world/World;", ordinal = 5, shift = At.Shift.BEFORE))
    private void overrideJumpCooldown(CallbackInfo ci){
        if(nextJumpCooldown != null){
            jumpingCooldown = nextJumpCooldown;
            nextJumpCooldown = null;
        }
    }

    @Unique
    private static final Config.FlagRef noOnBlockSlow = Configs.MOV_CONFIG.getBoolean(Configs.MOVE_SPEED_NO_SLOW_DOWN_BLOCK_FRAC);
    @Redirect(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getSlipperiness()F"))
    private float onIgnoreSlipperiness(Block instance){
        if(noOnBlockSlow.get()){
            return 0.6F;
        }else{
            return instance.getSlipperiness();
        }
    }

}
