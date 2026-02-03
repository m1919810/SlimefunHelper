package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.GameProfile;
import me.matl114.accessors.hacks.EntityInternalAccess;
import me.matl114.accessors.access.LivingEntityAccess;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

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
    @Unique
    boolean stopJumpThisTickInternal = false;
    @Override
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
    @Unique
    private static final int HISTORY_LEN = 16;
    @Unique
    private Vec3d[] historyPositionQueue = new Vec3d[HISTORY_LEN];

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V", shift = At.Shift.AFTER))
    private void onInit(World world, BlockPos pos, float yaw, GameProfile gameProfile, CallbackInfo ci){
        historyPositionQueue = new Vec3d[HISTORY_LEN];
        currentCursor = 0;
    }
    @Unique
    private int currentCursor = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void positionRecordTick(CallbackInfo ci){
        if(historyPositionQueue != null){
            synchronized (historyPositionQueue) {
                historyPositionQueue[currentCursor] = getPos();
                currentCursor = (currentCursor + 1) % HISTORY_LEN;
            }
        }

    }
    @Unique
    private int getCurrentCursor(){
        int current;
        synchronized (historyPositionQueue){
            current = currentCursor;
        }
        return current;
    }

    @Unique
    public Vec3d predictPosition(int ticksLater, int interpolateMethod){
        if(ticksLater <= 0){
            return getEntityPos();
        }
        Vec3d[] vec3ds;
        int current;
        synchronized (historyPositionQueue) {
            vec3ds = Arrays.copyOf(historyPositionQueue, HISTORY_LEN);
            current = currentCursor;
        }
        if(vec3ds[current] == null){
            return ;
        }
        Vec3d[] vec3ds1 = new Vec3d[HISTORY_LEN];
        for(var i = 0 ; i < HISTORY_LEN ; i++){
            vec3ds1[i] = vec3ds[(current + i) % HISTORY_LEN];
        }
        //todo: predict
        return switch (interpolateMethod) {
            case 1 ->
                MathUtils.linearPrediction(vec3ds1, ticksLater);

            case 2 ->
                MathUtils.quadraticPrediction(vec3ds1, interpolateMethod);
            case 3 ->
                new MathUtils.NVPredictor(vec3ds, ()-> current).compute(ticksLater);
            default -> vec3ds1[vec3ds1.length - 1];
        };
    }
}
