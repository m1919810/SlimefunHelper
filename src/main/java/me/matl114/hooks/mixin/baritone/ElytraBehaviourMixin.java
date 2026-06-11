package me.matl114.hooks.mixin.baritone;

import baritone.api.utils.IPlayerController;
import baritone.behavior.InventoryBehavior;
import baritone.process.elytra.ElytraBehavior;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Predicate;
import me.matl114.hacks.modules.move.BaritoneFix;
import me.matl114.hacks.modules.move.ElytraExtra;
import me.matl114.hacks.modules.move.FloatingUtils;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(value = ElytraBehavior.class, remap = false)
public abstract class ElytraBehaviourMixin {
    @WrapOperation(
            method = {
                "a(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;ZZ)V",
                "tickUseFireworks(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;ZZ)V"
            },
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lbaritone/api/utils/IPlayerController;processRightClick(Lnet/minecraft/client/network/ClientPlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;"),
            require = 0)
    public ActionResult onUseFireworks(
            IPlayerController instance,
            ClientPlayerEntity player,
            World world,
            Hand hand,
            Operation<ActionResult> original) {
        if (BaritoneFix.INSTANCE.enableGhostHandFireworks.get()) {
            ElytraExtra.INSTANCE.sendCustomUseFireworkPacket();
            return ActionResult.SUCCESS;
        }
        return original.call(instance, player, world, hand);
    }

    @WrapOperation(
            method = "a(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;ZZ)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lbaritone/behavior/InventoryBehavior;a(ZLjava/util/function/Predicate;)Z"),
            require = 0)
    private boolean onCancelInventorySwap(
            InventoryBehavior instance,
            boolean b,
            Predicate<? super ItemStack> predicate,
            Operation<Boolean> original) {
        if (BaritoneFix.INSTANCE.enableGhostHandFireworks.get()) {
            return true;
        }
        return original.call(instance, b, predicate);
    }

    @WrapOperation(
            method = "tickUseFireworks(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;ZZ)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lbaritone/behavior/InventoryBehavior;throwaway(ZLjava/util/function/Predicate;)Z"),
            require = 0)
    private boolean onCancelInventorySwap2(
            InventoryBehavior instance,
            boolean b,
            Predicate<? super ItemStack> predicate,
            Operation<Boolean> original) {
        if (BaritoneFix.INSTANCE.enableGhostHandFireworks.get()) {
            return true;
        }
        return original.call(instance, b, predicate);
    }

    @WrapOperation(
            method = {
                "a(Lbaritone/process/elytra/ElytraBehavior$SolverContext;Lnet/minecraft/util/math/Vec3d;ILit/unimi/dsi/fastutil/floats/FloatArrayList;III)Lbaritone/process/elytra/ElytraBehavior$PitchResult;",
                "Lbaritone/process/elytra/ElytraBehavior;simulate(Lbaritone/process/elytra/ElytraBehavior$SolverContext;Lnet/minecraft/world/phys/Vec3;FIII)Ljava/util/List;"
            },
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/util/math/Box;expand(DDD)Lnet/minecraft/util/math/Box;"),
            require = 0)
    private Box fixHitBoxCalculation(Box instance, double x, double y, double z, Operation<Box> original) {
        if (BaritoneFix.INSTANCE.fixSimulateError.get()) {
            return instance.offset(x, y, z);
        }
        return original.call(instance, x, y, z);
    }
    //    @Unique
    //    @Final
    //    private static final VarHandle boxHandle = initialize();
    //    @Unique
    //    private static VarHandle initialize(){
    //        try{
    //            Class<?> clazz = Class.forName(
    //                "baritone.process.elytra.ElytraBehavior$SolverContext");
    //            Field field = Arrays.stream(clazz.getFields()).filter(s -> s.getType() == Box.class).filter(s ->
    // !Modifier.isStatic(s.getModifiers())).findAny().orElseThrow();
    //            field.setAccessible(true);
    //            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
    //            return lookup.unreflectVarHandle(field);
    //        }catch (Throwable e){
    //            throw new RuntimeException(e);
    //        }
    ////    }
    //    @Unique
    //    Box cachedBox;

    //    @Inject(method = {"a(Lbaritone/api/event/events/TickEvent;)V",
    // "Lbaritone/process/elytra/ElytraBehavior;onPostTick(Lbaritone/api/event/events/TickEvent;)V"}, at = @At(value =
    // "INVOKE", target =
    // "Lbaritone/process/elytra/ElytraBehavior$SolverContext;<init>(Lbaritone/process/elytra/ElytraBehavior;Z)V", shift
    // = At.Shift.BEFORE), require = 0)
    //    private void onInitialize(TickEvent par1, CallbackInfo ci){
    //        if(BaritoneFix.INSTANCE.fixErrorFly.get()){
    //            Box box1 = BaritoneFix.INSTANCE.processBoxOfElytraFlight();
    //            if(box1 != null){
    //                var pl =  MinecraftClient.getInstance().player;
    //                cachedBox = pl.getBoundingBox();
    //                pl.setBoundingBox(box1);
    //            }
    //        }
    //    }
    //    @Inject(method = {"a(Lbaritone/api/event/events/TickEvent;)V",
    // "Lbaritone/process/elytra/ElytraBehavior;onPostTick(Lbaritone/api/event/events/TickEvent;)V"}, at = @At(value =
    // "INVOKE", target =
    // "Lbaritone/process/elytra/ElytraBehavior$SolverContext;<init>(Lbaritone/process/elytra/ElytraBehavior;Z)V", shift
    // = At.Shift.AFTER), require = 0)
    //    private void onInitialize2(TickEvent par1, CallbackInfo ci){
    //        if(cachedBox != null){
    //            MinecraftClient.getInstance().player.setBoundingBox(cachedBox);
    //            cachedBox = null;
    //        }
    //    }
    //
    //    @Inject(method = "Lbaritone/process/elytra/ElytraBehavior;tick()V", at = @At(value = "INVOKE", target =
    // "Lbaritone/process/elytra/ElytraBehavior$SolverContext;<init>(Lbaritone/process/elytra/ElytraBehavior;Z)V", shift
    // = At.Shift.BEFORE), require = 0)
    //    private void onInitialize3(CallbackInfo ci){
    //        if(BaritoneFix.INSTANCE.fixErrorFly.get()){
    //            Box box1 = BaritoneFix.INSTANCE.processBoxOfElytraFlight();
    //            if(box1 != null){
    //                var pl =  MinecraftClient.getInstance().player;
    //                cachedBox = pl.getBoundingBox();
    //                pl.setBoundingBox(box1);
    //            }
    //        }
    //    }
    //
    //    @Inject(method = "Lbaritone/process/elytra/ElytraBehavior;tick()V", at = @At(value = "INVOKE", target =
    // "Lbaritone/process/elytra/ElytraBehavior$SolverContext;<init>(Lbaritone/process/elytra/ElytraBehavior;Z)V", shift
    // = At.Shift.AFTER), require = 0)
    //    private void onInitialize4(CallbackInfo ci){
    //        if(cachedBox != null){
    //            MinecraftClient.getInstance().player.setBoundingBox(cachedBox);
    //            cachedBox = null;
    //        }
    //    }

    @Inject(
            method = "Lbaritone/process/elytra/ElytraBehavior;tick()V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lbaritone/process/elytra/ElytraBehavior;logVerbose(Ljava/lang/String;)V",
                            ordinal = 2),
            require = 0)
    private void onNoSolution3(CallbackInfo ci) {
        if (BaritoneFix.INSTANCE.freezeWhenFailCalculate.get()) {
            Debug.chat(ChatUtils.stringToText(
                    "&c[BaritoneFix] &fFreeze because of Baritone Elytra Computing Failure (All)"));
            FloatingUtils.INSTANCE.setGrimFloatingTick(true);
        }
    }

    @Inject(
            method = "Lbaritone/process/elytra/ElytraBehavior;tick()V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lbaritone/process/elytra/ElytraBehavior;logVerbose(Ljava/lang/String;)V",
                            ordinal = 3),
            require = 0)
    private void onNoSolution4(CallbackInfo ci) {
        if (BaritoneFix.INSTANCE.freezeWhenFailCalculate.get()) {
            Debug.chat(ChatUtils.stringToText(
                    "&c[BaritoneFix] &fFreeze because of Baritone Elytra Computing Failure (Pitch)"));
            FloatingUtils.INSTANCE.setGrimFloatingTick(true);
        }
    }
}
