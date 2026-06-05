package me.matl114.hooks.mixin.baritone;

import baritone.process.elytra.ElytraBehavior;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.hacks.modules.move.BaritoneFix;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(ElytraBehavior.PathManager.class)
public abstract class ElytraBehaviourPathManagerMixin {
    @WrapOperation(
            method = {"b()V", "Lbaritone/process/elytra/ElytraBehavior$PathManager;pathfindAroundObstacles()V"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lbaritone/process/elytra/ElytraBehavior;a(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Z)Z",
                            ordinal = 2),
            require = 0)
    private boolean b(ElytraBehavior instance, Vec3d start, Vec3d to, boolean b, Operation<Boolean> original) {
        if (!b && BaritoneFix.INSTANCE.baritoneExperimental1.get()) {
            if (to.y < BaritoneFix.INSTANCE.baritoneExperimentHeight.get()) {
                return false;
            }
        }
        return original.call(instance, start, to, b);
    }
}
