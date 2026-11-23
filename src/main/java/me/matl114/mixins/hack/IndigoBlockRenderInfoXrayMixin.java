package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.matl114.hacks.modules.render.Xray;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo", remap = false)
public abstract class IndigoBlockRenderInfoXrayMixin {
    @Shadow
    public BlockRenderView blockView;

    @Shadow
    public BlockPos blockPos;

    @Shadow
    public BlockState blockState;

    @ModifyReturnValue(method = "shouldCullSide", at = @At("RETURN"))
    private boolean slimefunhelper$modifyDrawSide(boolean original, Direction side) {
        Xray xray = Xray.INSTANCE;
        if (side == null || xray == null || !xray.isActive()) return original;

        return !xray.modifyDrawSide(blockState, blockView, blockPos, side, !original);
    }
}
