package me.matl114.mixins.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.accessors.events.TrackedPositionAccess;
import me.matl114.events.Listener;
import me.matl114.events.impl.PosUpdate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TrackedPosition;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(TrackedPosition.class)
public abstract class TrackedPositionEvents implements TrackedPositionAccess {
    @Shadow
    private Vec3d pos;

    @Unique
    public Entity owner;

    public void setOwner(Entity entity) {
        this.owner = entity;
    }

    @WrapOperation(
            method = "setPos",
            at =
                    @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/entity/TrackedPosition;pos:Lnet/minecraft/util/math/Vec3d;"))
    private void onSetPos(TrackedPosition instance, Vec3d value, Operation<Void> original) {
        Vec3d oldValue = this.pos;
        original.call(instance, value);
        if (this.owner != null) {
            Listener.getEntityTrackedPositionUpdate().broadcast(new PosUpdate(oldValue, this.pos, this.owner));
        }
    }
}
