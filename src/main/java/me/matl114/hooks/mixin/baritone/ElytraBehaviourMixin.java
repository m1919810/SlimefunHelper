package me.matl114.hooks.mixin.baritone;

import baritone.api.utils.IPlayerController;
import baritone.behavior.InventoryBehavior;
import baritone.process.elytra.ElytraBehavior;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Predicate;
import me.matl114.hacks.modules.move.BaritoneFix;
import me.matl114.hacks.modules.move.ElytraExtra;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

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
        expect = 1,
        require = 1)
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

//    @WrapOperation(
//        method = "tickUseFireworks(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;ZZ)V",
//        at =
//        @At(
//            value = "INVOKE",
//            target =
//                "Lbaritone/behavior/InventoryBehavior;throwaway(ZLjava/util/function/Predicate;)Z"),
//        require = 0)
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
}
