package me.matl114.hooks.mixin.baritone;

import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.process.ElytraProcess;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.lang.reflect.Field;
import java.util.Arrays;
import me.matl114.events.Event;
import me.matl114.hacks.modules.move.BaritoneFix;
import me.matl114.hacks.modules.move.FloatingUtils;
import me.matl114.hooks.BaritoneHooks;
import me.matl114.hooks.impl.BaritoneFuture;
import me.matl114.hooks.impl.BaritoneLanding;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Unique
    private BaritoneFuture landingFuture;

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
        BaritoneFuture future = new BaritoneFuture();
        Event<BaritoneFuture> event = new Event<>(future, true, false, BaritoneLanding.EMERGENCY);
        BaritoneHooks.getLandingEvent().handleValue(event);
        if (event.isCancelled()) {
            future.onCancel();
            cir.setReturnValue(new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL));
            return;
        }
        landingFuture = future;
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
        BaritoneFuture future = new BaritoneFuture();
        Event<BaritoneFuture> event = new Event<>(future, true, false, BaritoneLanding.PATH_COMPLETE);
        BaritoneHooks.getLandingEvent().handleValue(event);
        if (event.isCancelled()) {
            future.onCancel();
            cir.setReturnValue(new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL));
            return;
        }
        landingFuture = future;
    }

    @WrapWithCondition(
            method = "onTick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lbaritone/process/ElytraProcess;logDirect(Ljava/lang/String;)V",
                            ordinal = 9),
            require = 0)
    private boolean hookLanding(ElytraProcess instance, String s) {
        if (landingFuture != null) {
            landingFuture.onComplete();
            landingFuture = null;
        }
        return true;
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

    @Unique
    private static final Field field = Arrays.stream(ElytraProcess.class.getFields())
            .filter(s -> s.getType() == ElytraProcess.State.class)
            .findAny()
            .orElseThrow();

    @WrapOperation(
            method = "onTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isOnGround()Z"),
            require = 0)
    private boolean onAutoJumpRewrite(ClientPlayerEntity instance, Operation<Boolean> original) {
        if (BaritoneFix.INSTANCE.handleAutoJump()) {
            return false;
        }
        return original.call(instance);
    }

    @Inject(method = "pathTo(Lnet/minecraft/util/math/BlockPos;)V", at = @At("RETURN"))
    private void pathTo(BlockPos par1, CallbackInfo ci) {
        BaritoneHooks.getElytraPathingEvent().broadcast(par1);
    }
}
