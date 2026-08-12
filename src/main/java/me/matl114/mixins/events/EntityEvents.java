package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import me.matl114.accessors.events.EntityAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class EntityEvents<T extends Entity> implements EntityAccess<T> {
    @ModifyExpressionValue(
            method = "updateVelocity",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/Entity;movementInputToVelocity(Lnet/minecraft/util/math/Vec3d;FF)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d onModifyVelocity(Vec3d original) {
        if (!checkClientPlayer()) return original;
        Event<Vec3d> vec3d = new Event<>(original, true, true);
        Listener.getPlayerVelocityTick().handleValue(vec3d);
        if (vec3d.isCancelled()) {
            return Vec3d.ZERO;
        } else {
            return vec3d.context();
        }
    }

    @Shadow
    protected abstract void setFlag(int index, boolean value);

    @Shadow
    protected abstract boolean getFlag(int index);

    @Shadow
    protected abstract void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition);

    @Shadow
    public abstract ActionResult interact(PlayerEntity player, Hand hand);

    @Unique
    public void setDataFlag(int index, boolean val) {
        this.setFlag(index, val);
    }

    @Unique
    public boolean getDataFlag(int index) {
        return getFlag(index);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void onEntityTickUpdate(CallbackInfo ci) {
        Entity entity = (Entity) (Object) (this);
        Listener.getEntityMidTickListener().broadcast(entity);
    }

    @Inject(method = "onDataTrackerUpdate", at = @At("HEAD"))
    public void onEntityDataUpdate(List<DataTracker.SerializedEntry<?>> dataEntries, CallbackInfo ci) {
        Entity entity = (Entity) (Object) (this);
        Listener.getEntityDataListener().broadcast(entity, dataEntries);
    }

    @Inject(method = "setRemoved", at = @At("RETURN"))
    public void onEntityRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        Listener.getEntityRemoveListener().broadcast((Entity) (Object) this, reason);
    }

    @WrapOperation(
            method = "updateMovementInFluid",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/util/math/Vec3d;add(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
                            ordinal = 1))
    public Vec3d onEntityUpdateVelocity(
            Vec3d instance, Vec3d vec, Operation<Vec3d> original, @Local(argsOnly = true) TagKey<Fluid> tagKey) {
        if (checkClientPlayer()) {
            Event<Vec3d> eventVec3d = new Event<>(vec, true, true, tagKey);
            Listener.getPlayerFluidVelocityPoint().handleValue(eventVec3d);
            if (eventVec3d.isCancelled()) {
                return instance;
            }
            return original.call(instance, eventVec3d.context);
        } else {
            return original.call(instance, vec);
        }
    }
}
