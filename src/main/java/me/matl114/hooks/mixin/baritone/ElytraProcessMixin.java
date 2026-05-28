package me.matl114.hooks.mixin.baritone;

import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.process.ElytraProcess;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.matl114.hacks.modules.move.BaritoneFix;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(value = ElytraProcess.class, remap = false)
public abstract class ElytraProcessMixin {

    @Inject(
            method = {"a()Z", "shouldLandForSafety()Z"},
            at = @At("HEAD"),
            expect = 1,
            cancellable = true)
    private void hookShouldLandForSafety(CallbackInfoReturnable<Boolean> ci) {
        if (BaritoneFix.INSTANCE.disableInventoryCheck.get()) {
            ci.setReturnValue(!BaritoneFix.INSTANCE.checkCanContinueFlyingCustom());
        }
    }

    @WrapWithCondition(
            method = "onTick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lbaritone/process/ElytraProcess;logDirect(Ljava/lang/String;)V",
                            ordinal = 4))
    private boolean hookLogDirect(ElytraProcess instance, String string) {
        if (BaritoneFix.INSTANCE.enableEmergencyLandingFix.get()) {
            return false;
        }
        return true;
    }

    @ModifyExpressionValue(
            method = {"a(Lnet/minecraft/util/math/BlockPos;Z)V", "pathTo0(Lnet/minecraft/util/math/BlockPos;Z)V"},
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/World;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"),
            expect = 1,
            require = 1)
    private RegistryKey<World> hookGetRegistryKey(RegistryKey<World> original) {
        if (BaritoneFix.INSTANCE.enableDimensionFix.get() && original != World.NETHER) {
            return World.NETHER;
        }
        return original;
    }

    @Inject(
            method = "onTick",
            at =
                    @At(
                            value = "FIELD",
                            target = "Lbaritone/api/Settings;elytraAllowEmergencyLand:Lbaritone/api/Settings$Setting;",
                            shift = At.Shift.BEFORE),
            cancellable = true)
    private void hookAllowEmergencyLand(boolean par1, boolean par2, CallbackInfoReturnable<PathingCommand> cir) {
        if (BaritoneFix.INSTANCE.handleLog()) {
            cir.setReturnValue(new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL));
        }
    }

    @Inject(
            method = "onTick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lbaritone/process/ElytraProcess;logDirect(Ljava/lang/String;)V",
                            ordinal = 5),
            cancellable = true)
    private void hookLogDirect(boolean par1, boolean par2, CallbackInfoReturnable<PathingCommand> cir) {
        if (BaritoneFix.INSTANCE.handleLog()) {
            cir.setReturnValue(new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL));
        }
    }
}
