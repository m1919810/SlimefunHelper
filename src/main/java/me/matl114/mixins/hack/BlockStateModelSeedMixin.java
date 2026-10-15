package me.matl114.mixins.hack;

import me.matl114.hacks.modules.render.NoRender;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class BlockStateModelSeedMixin {
    @Shadow
    public abstract Block getBlock();

    @Inject(method = "getRenderingSeed(Lnet/minecraft/util/math/BlockPos;)J", at = @At("HEAD"), cancellable = true)
    private void noTextureOffset(BlockPos pos, CallbackInfoReturnable<Long> cir) {
        if (NoRender.INSTANCE.noTextureOffset()
                && Registries.BLOCK.getId(this.getBlock()).getNamespace().equals("minecraft")) {
            cir.setReturnValue(0L);
        }
    }
}
