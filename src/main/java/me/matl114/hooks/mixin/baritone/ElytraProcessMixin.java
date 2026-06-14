package me.matl114.hooks.mixin.baritone;

import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.process.ElytraProcess;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.matl114.hacks.modules.move.BaritoneFix;
import me.matl114.hacks.modules.move.FloatingUtils;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
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
            require = 0,
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
                            ordinal = 4),
            require = 0)
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
            require = 0)
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
            cancellable = true,
            require = 0)
    private void hookAllowEmergencyLand(boolean par1, boolean par2, CallbackInfoReturnable<PathingCommand> cir) {
        if (BaritoneFix.INSTANCE.handleLog("Emergency Landing")) {
            cir.setReturnValue(new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL));
            return;
        }
        if (BaritoneFix.INSTANCE.handleFreeze("Path Complete")) {
            cir.setReturnValue(new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL));
            return;
        }
    }

    @Inject(
            method = "onTick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lbaritone/process/ElytraProcess;logDirect(Ljava/lang/String;)V",
                            ordinal = 5),
            cancellable = true,
            require = 0)
    private void hookLogDirect(boolean par1, boolean par2, CallbackInfoReturnable<PathingCommand> cir) {
        if (BaritoneFix.INSTANCE.handleLog("Path Complete")) {
            cir.setReturnValue(new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL));
            return;
        }
        if (BaritoneFix.INSTANCE.handleFreeze("Path Complete")) {
            cir.setReturnValue(new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL));
            return;
        }
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void hookPauseElytraProcess(boolean par1, boolean par2, CallbackInfoReturnable<PathingCommand> cir) {
        if (BaritoneFix.INSTANCE.shouldPauseBaritoneElytra()) {
            cir.setReturnValue(new PathingCommand(null, PathingCommandType.REQUEST_PAUSE));
        }
    }
    //    @Unique
    //    Box cachedBox;

    //    @Inject(method = "onTick", at = @At(value = "INVOKE", target =
    // "Lbaritone/process/elytra/ElytraBehavior$SolverContext;<init>(Lbaritone/process/elytra/ElytraBehavior;Z)V", shift
    // = At.Shift.BEFORE), require = 0)
    //    private void hookElytraBehaviorSolverBox(boolean par1, boolean par2, CallbackInfoReturnable<PathingCommand>
    // cir) {
    //        if(BaritoneFix.INSTANCE.fixErrorFly.get()){
    //            Box box1 = BaritoneFix.INSTANCE.processBoxOfElytraFlight();
    //            if(box1 != null){
    //                var pl =  MinecraftClient.getInstance().player;
    //                cachedBox = pl.getBoundingBox();
    //                pl.setBoundingBox(box1);
    //            }
    //        }
    //    }
    //    @Inject(method = "onTick", at = @At(value = "INVOKE", target =
    // "Lbaritone/process/elytra/ElytraBehavior$SolverContext;<init>(Lbaritone/process/elytra/ElytraBehavior;Z)V", shift
    // = At.Shift.AFTER), require =  0)
    //    private void hookElytraBehaviorSolverBox2(boolean par1, boolean par2, CallbackInfoReturnable<PathingCommand>
    // cir){
    //        if(cachedBox != null){
    //            MinecraftClient.getInstance().player.setBoundingBox(cachedBox);
    //            cachedBox = null;
    //        }
    //    }

    @Inject(
            method = "onTick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lbaritone/process/elytra/ElytraBehavior;a(Ljava/lang/String;)V",
                            ordinal = 2),
            require = 0)
    private void onNoSolution1(boolean par1, boolean par2, CallbackInfoReturnable<PathingCommand> cir) {
        if (BaritoneFix.INSTANCE.freezeWhenFailCalculate.get()) {
            Debug.chat(ChatUtils.stringToText(
                    "&c[BaritoneFix] &fFreeze because of Baritone Elytra Computing Failure (All)"));
            FloatingUtils.INSTANCE.setGrimFloatingTick(true);
        }
    }

    @Inject(
            method = "onTick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lbaritone/process/elytra/ElytraBehavior;a(Ljava/lang/String;)V",
                            ordinal = 3),
            require = 0)
    private void onNoSolution2(boolean par1, boolean par2, CallbackInfoReturnable<PathingCommand> cir) {
        if (BaritoneFix.INSTANCE.freezeWhenFailCalculate.get()) {
            Debug.chat(ChatUtils.stringToText(
                    "&c[BaritoneFix] &fFreeze because of Baritone Elytra Computing Failure (Pitch)"));
            FloatingUtils.INSTANCE.setGrimFloatingTick(true);
        }
    }
}
