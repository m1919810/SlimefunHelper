package me.matl114.mixins.hack;

import me.matl114.hacks.modules.render.Xray;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.class)
public abstract class AbstractBlockXrayMixin {
    @Inject(method = "getAmbientOcclusionLightLevel", at = @At("HEAD"), cancellable = true)
    private void slimefunhelper$disableAmbientOcclusion(
            BlockState state, BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (Xray.isXrayActive()) cir.setReturnValue(1.0F);
    }
}
