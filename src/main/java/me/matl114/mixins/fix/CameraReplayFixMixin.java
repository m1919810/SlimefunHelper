package me.matl114.mixins.fix;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.attribute.EnvironmentAttributeInterpolator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public abstract class CameraReplayFixMixin {
    @WrapOperation(
            method = "updateEyeHeight",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/attribute/EnvironmentAttributeInterpolator;update(Lnet/minecraft/world/World;Lnet/minecraft/util/math/Vec3d;)V"))
    public void updateEyeHeight(
            EnvironmentAttributeInterpolator instance, World world, Vec3d pos, Operation<Void> original) {
        try {
            original.call(instance, world, pos);
        } catch (Throwable e) {
        }
    }
}
