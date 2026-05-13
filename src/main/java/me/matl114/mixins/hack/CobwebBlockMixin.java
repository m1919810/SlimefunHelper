package me.matl114.mixins.hack;

import me.matl114.hacks.MovTasks;
import me.matl114.hacks.modules.move.NoSlowDown;
import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CobwebBlock.class)
public abstract class CobwebBlockMixin {
    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    public void onEntityCollision(
            BlockState state,
            World world,
            BlockPos pos,
            Entity entity,
            EntityCollisionHandler handler,
            boolean bl,
            CallbackInfo ci) {
        NoSlowDown noSlowDown = MovTasks.getNoSlowDown();
        if (noSlowDown.blockIn.get() && entity == MinecraftClient.getInstance().player && noSlowDown.onWeb(pos)) {
            ci.cancel();
        }
    }
}
