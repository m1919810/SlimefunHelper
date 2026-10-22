package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.SlowMovement;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(SweetBerryBushBlock.class)
public abstract class SweetBerryBushBlockMixin {
    @WrapOperation(
            method = "onEntityCollision",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/entity/Entity;slowMovement(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Vec3d;)V"))
    private void onSlowMovement(
            Entity instance,
            BlockState state,
            Vec3d multiplier,
            Operation<Void> original,
            @Local(argsOnly = true) BlockPos pos) {
        if (instance == MinecraftClient.getInstance().player) {
            Event<SlowMovement> slowMovement = new Event<>(new SlowMovement(multiplier, pos, state), true, true);
            Listener.getPlayerSlowedByBlockPoint().handleValue(slowMovement);
            if (slowMovement.isCancelled()) {
                return;
            } else {
                original.call(instance, state, slowMovement.context.movementModifier());
            }
        } else {
            original.call(instance, state, multiplier);
        }
    }
}
