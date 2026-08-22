package me.matl114.hooks.mixin.baritone;

import baritone.api.utils.BetterBlockPos;
import baritone.process.elytra.ElytraBehavior;
import baritone.process.elytra.UnpackedSegment;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import me.matl114.hacks.modules.survival.BaritoneFix;
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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(ElytraBehavior.PathManager.class)
public abstract class ElytraBehaviourPathManagerMixin {

    @Shadow(
            aliases = {"a", "setPath"},
            remap = false)
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
            method =
                    "Lbaritone/process/elytra/ElytraBehavior$PathManager;path0(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Ljava/util/function/UnaryOperator;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void c(
            BlockPos var1,
            BlockPos var2,
            UnaryOperator<UnpackedSegment> var3,
            CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        if (BaritoneHooks.MeteorBaritoneImpl.netherPathSupplier != null) {
            var lst = BaritoneHooks.MeteorBaritoneImpl.netherPathSupplier.get();
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

    @Inject(
            method =
                    "Lbaritone/process/elytra/ElytraBehavior$PathManager;a(Lbaritone/api/utils/BetterBlockPos;Lbaritone/api/utils/BetterBlockPos;Ljava/util/function/UnaryOperator;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void c2(
            BetterBlockPos par1,
            BetterBlockPos par2,
            UnaryOperator<UnpackedSegment> par3,
            CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        if (BaritoneHooks.MeteorBaritoneImpl.netherPathSupplier != null) {
            var lst = BaritoneHooks.MeteorBaritoneImpl.netherPathSupplier.get();
            if (lst != null) {
                UnpackedSegment segment = new UnpackedSegment(lst.stream().map(BetterBlockPos::from), true);
                var segment2 = par3.apply(segment);

                cir.setReturnValue(CompletableFuture.supplyAsync(
                        () -> {
                            this.a(segment2);
                            return null;
                        },
                        MinecraftClient.getInstance()));
            }
        }
    }

    @ModifyArg(
            method = {
                "a(Lbaritone/process/elytra/UnpackedSegment;)V",
                "Lbaritone/process/elytra/ElytraBehavior$PathManager;setPath(Lbaritone/process/elytra/UnpackedSegment;)V"
            },
            at = @At(value = "INVOKE", target = "Lbaritone/process/elytra/NetherPath;<init>(Ljava/util/List;)V"),
            require = 0,
            expect = 0, remap = false)
    private List<BetterBlockPos> captureNetherPathArgumentUpdate(List<BetterBlockPos> list) {
        BaritoneHooks.currentNetherElytraPath = (List) list;
        return list;
    }

    @Inject(
            method = {"a()V", "Lbaritone/process/elytra/ElytraBehavior$PathManager;clear()V"},
            at = @At("HEAD"), remap = false)
    private void captureNetherPathArgumentClear(CallbackInfo ci) {
        BaritoneHooks.currentNetherElytraPath = List.of();
    }
}
