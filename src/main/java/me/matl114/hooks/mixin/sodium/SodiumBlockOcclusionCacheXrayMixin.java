package me.matl114.hooks.mixin.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.matl114.hacks.modules.render.Xray;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Sodium 0.6.x culls block faces through {@code BlockOcclusionCache.shouldDrawSide}. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache", remap = false)
public abstract class SodiumBlockOcclusionCacheXrayMixin {
    @ModifyReturnValue(method = "shouldDrawSide", at = @At("RETURN"), require = 0, expect = 0)
    private boolean slimefunhelper$modifyDrawSide(
            boolean original, BlockState state, BlockView view, BlockPos pos, Direction facing) {
        Xray xray = Xray.INSTANCE;
        if (xray == null || !xray.isActive()) return original;
        return xray.modifyDrawSide(state, view, pos, facing, original);
    }
}
