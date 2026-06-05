package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.matl114.accessors.events.EntityAccess;
import me.matl114.accessors.hacks.EntityInternalAccess;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.utils.entity.Predictor;
import me.matl114.hacks.utils.entity.SimpleEntityPredictor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class EntityMixin<T extends Entity> implements EntityAccess<T>, EntityInternalAccess<T> {
    @Unique
    byte renderTracked = 0;

    @Unique
    public byte renderTrackedLevel() {
        return renderTracked;
    }

    @Unique
    public void markRenderTracked(byte tracked) {
        renderTracked = tracked;
    }

    @Unique
    boolean clientGlowEffect = false;

    @Override
    public void setGlow0(boolean glow) {
        clientGlowEffect = glow;
    }

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    public void onGlowEffect(CallbackInfoReturnable<Boolean> cir) {
        if (clientGlowEffect) {
            cir.setReturnValue(true);
        }
    }

    //    @Inject(
    //            method = "slowMovement",
    //            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;onLanding()V", shift =
    // At.Shift.AFTER),
    //            cancellable = true)
    //    private void onSlowMovementDoNotModifyVelocity(BlockState state, Vec3d multiplier, CallbackInfo ci) {
    //        if (checkClientPlayer() && MovTasks.getNoSlowDown().blockIn.get()) {
    //            ci.cancel();
    //        }
    //    }

    @WrapWithCondition(
            method = "pushAwayFrom",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;addVelocity(DDD)V"))
    public boolean onEntityNoPush(Entity instance, double deltaX, double deltaY, double deltaZ) {
        if (MovTasks.getVelocity().noEntityPush.get()) {
            return false;
        }
        return true;
    }
    @Unique
    Predictor predictorInstance;
@Unique
    public Predictor getPositionPredictor(){
        if(predictorInstance == null) {
            predictorInstance = new SimpleEntityPredictor((Entity)(Object) this);
        }
        return predictorInstance;
    }
}
