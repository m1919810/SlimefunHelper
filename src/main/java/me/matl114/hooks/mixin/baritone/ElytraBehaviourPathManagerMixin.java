package me.matl114.hooks.mixin.baritone;

import baritone.api.utils.BetterBlockPos;
import baritone.process.elytra.ElytraBehavior;
import baritone.process.elytra.UnpackedSegment;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import me.matl114.hacks.modules.move.BaritoneFix;
import me.matl114.hooks.BaritoneHooks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(ElytraBehavior.PathManager.class)
public abstract class ElytraBehaviourPathManagerMixin {

    @Shadow(aliases = {"a", "setPath"})
    protected abstract void a(UnpackedSegment unpackedSegment);

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

    @Inject(
            method = {
                "Lbaritone/process/elytra/ElytraBehavior$PathManager;a(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;Ljava/util/function/UnaryOperator;)Ljava/util/concurrent/CompletableFuture;",
                "Lbaritone/process/elytra/ElytraBehavior$PathManager;path0(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Ljava/util/function/UnaryOperator;)Ljava/util/concurrent/CompletableFuture;"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void c(
            BlockPos var1,
            BlockPos var2,
            UnaryOperator<UnpackedSegment> var3,
            CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        if (BaritoneHooks.Impl.netherPathSupplier != null) {
            var lst = BaritoneHooks.Impl.netherPathSupplier.get();
            if (lst != null) {
                UnpackedSegment segment = new UnpackedSegment(lst.stream().map(BetterBlockPos::from), true);
                var segment2 = var3.apply(segment);

                cir.setReturnValue(CompletableFuture.supplyAsync(
                        () -> {
                            this.a(segment2);
                            return null;
                        },
                        MinecraftClient.getInstance()));
            }
        }
    }
}
