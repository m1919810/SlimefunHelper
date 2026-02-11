package me.matl114.mixins.hack;

import me.matl114.hacks.MovTasks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlimeBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(SlimeBlock.class)
public abstract class SlimeBlockMixin {

    @Inject(
            method = "onSteppedOn",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/entity/Entity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V",
                            shift = At.Shift.BEFORE),
            cancellable = true)
    private void onDisableSlimeBlockVelocityModify(
            World world, BlockPos pos, BlockState state, Entity entity, CallbackInfo ci) {
        if (MovTasks.getNoSlowDown().blockSpecial.get()) {
            ci.cancel();
        }
    }
}
