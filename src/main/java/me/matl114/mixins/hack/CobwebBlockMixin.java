package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.modules.move.NoSlowDown;
import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CobwebBlock.class)
public abstract class CobwebBlockMixin {
    @Inject(method = "onEntityCollision", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;slowMovement(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Vec3d;)V", shift = At.Shift.BEFORE), cancellable = true)
    public void onEntityCollision(
        BlockState state,
        World world,
        BlockPos pos,
        Entity entity,
        EntityCollisionHandler handler,
        boolean bl,
        CallbackInfo ci, @Local Vec3d vec3d, @Local LocalRef<Vec3d> vec3dLocalRef) {
        if (entity == MinecraftClient.getInstance().player ) {
            Event<Vec3d> slowMovement = new Event<>(vec3d, true, true, pos);
            Listener.getPlayerWebSlowPoint().handleValue(slowMovement);
            if(slowMovement.isCancelled()) {
                ci.cancel();
            }else {
                if(slowMovement.context != vec3d) {
                    vec3dLocalRef.set(slowMovement.context);
                }
            }
        }
    }
}
