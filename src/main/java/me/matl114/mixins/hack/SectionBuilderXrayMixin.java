package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.hacks.modules.render.Xray;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SectionBuilder.class)
public abstract class SectionBuilderXrayMixin {
    @WrapOperation(
            method = "build",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/chunk/ChunkOcclusionDataBuilder;markClosed(Lnet/minecraft/util/math/BlockPos;)V"))
    private void slimefunhelper$disableChunkOcclusion(
            ChunkOcclusionDataBuilder instance, BlockPos pos, Operation<Void> original) {
        if (!Xray.isXrayActive()) original.call(instance, pos);
    }

    @WrapOperation(
            method = "build",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/RenderLayers;getBlockLayer(Lnet/minecraft/block/BlockState;)Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer slimefunhelper$makeBlockLayerTranslucent(
            BlockState state, Operation<RenderLayer> original, @Local(ordinal = 2) BlockPos pos) {
        int alpha = Xray.getAlpha(state, pos);
        RenderLayer layer = original.call(state);
        return alpha > 0 && alpha < 255 ? RenderLayer.getTranslucent() : layer;
    }

    @WrapOperation(
            method = "build",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/RenderLayers;getFluidLayer(Lnet/minecraft/fluid/FluidState;)Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer slimefunhelper$makeFluidLayerTranslucent(
            FluidState state, Operation<RenderLayer> original, @Local(ordinal = 2) BlockPos pos) {
        int alpha = Xray.getFluidAlpha(state, pos);
        RenderLayer layer = original.call(state);
        return alpha > 0 && alpha < 255 ? RenderLayer.getTranslucent() : layer;
    }
}
