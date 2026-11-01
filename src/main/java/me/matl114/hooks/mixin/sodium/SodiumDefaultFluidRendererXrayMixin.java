package me.matl114.hooks.mixin.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.hacks.modules.render.Xray;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer", remap = false)
public abstract class SodiumDefaultFluidRendererXrayMixin {
    @Shadow
    @Final
    private int[] quadColors;

    @Unique
    private int slimefunhelper$xrayAlpha;

    @Unique
    private boolean slimefunhelper$forceXrayFluidSides;

    @Unique
    private Xray slimefunhelper$xray;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0, expect = 0)
    private void slimefunhelper$initializeXray(CallbackInfo ci) {
        slimefunhelper$xray = Xray.INSTANCE;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void slimefunhelper$applyXray(
            @Coerce Object level,
            BlockState blockState,
            FluidState fluidState,
            BlockPos blockPos,
            BlockPos offset,
            @Coerce Object collector,
            @Coerce Object meshBuilder,
            Material material,
            @Coerce Object colorProvider,
            @Coerce Object sprites,
            CallbackInfo ci) {
        slimefunhelper$xrayAlpha = Xray.getFluidAlpha(fluidState, blockPos);
        slimefunhelper$forceXrayFluidSides = slimefunhelper$xray != null && slimefunhelper$xray.isActive();
        if (slimefunhelper$xrayAlpha == 0) {
            slimefunhelper$forceXrayFluidSides = false;
            ci.cancel();
        }
    }

    @ModifyReturnValue(method = "isFullBlockFluidSideVisible", at = @At("RETURN"), require = 0, expect = 0)
    private boolean slimefunhelper$showFullBlockFluidSide(
            boolean original,
            @Coerce Object view,
            BlockPos selfPos,
            Direction facing,
            FluidState fluid,
            @Local(name = "otherState") BlockState otherState) {
        if (original || !slimefunhelper$forceXrayFluidSides || facing.getAxis().isVertical()) return original;
        if (slimefunhelper$xray == null || !slimefunhelper$xray.isBlocked(otherState.getBlock(), null)) return false;
        return !otherState.getFluidState().getFluid().matchesType(fluid.getFluid());
    }

    @ModifyReturnValue(
            method =
                    "isFluidSideExposed(Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Direction;F)Z",
            at = @At("RETURN"),
            require = 0,
            expect = 0,
            remap = true)
    private boolean slimefunhelper$showFluidSide(
            boolean original, BlockState ownBlockState, BlockState neighborBlockState, Direction facing, float height) {
        if (original || !slimefunhelper$forceXrayFluidSides || facing.getAxis().isVertical()) return original;
        if (slimefunhelper$xray == null || !slimefunhelper$xray.isBlocked(neighborBlockState.getBlock(), null))
            return false;
        return !neighborBlockState
                .getFluidState()
                .getFluid()
                .matchesType(ownBlockState.getFluidState().getFluid());
    }

    @Inject(method = "getUpFaceExposureByNeighbors", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void slimefunhelper$showUpFluidFace(
            @Coerce Object level, BlockPos origin, FluidState fluidState, CallbackInfoReturnable<Integer> cir) {
        if (slimefunhelper$forceXrayFluidSides) cir.setReturnValue(3);
    }

    @Inject(method = "updateQuad", at = @At("TAIL"), require = 0, expect = 0)
    private void slimefunhelper$setFluidQuadAlpha(
            @Coerce Object quad,
            @Coerce Object level,
            BlockPos pos,
            @Coerce Object lighter,
            Direction direction,
            @Coerce Object facing,
            float brightness,
            @Coerce Object colorProvider,
            FluidState fluidState,
            CallbackInfo ci) {
        if (slimefunhelper$xrayAlpha != -1) {
            for (int vertex = 0; vertex < 4; vertex++) {
                quadColors[vertex] = (quadColors[vertex] & 0x00FFFFFF) | (slimefunhelper$xrayAlpha << 24);
            }
        }
    }

    @ModifyArg(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/DefaultFluidRenderer;writeQuad(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuilder;Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/TranslucentGeometryCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;Lnet/minecraft/util/math/BlockPos;Lnet/caffeinemc/mods/sodium/client/model/quad/ModelQuadView;Lnet/caffeinemc/mods/sodium/client/model/quad/properties/ModelQuadFacing;Z)V",
                            remap = true),
            index = 2,
            require = 0,
            expect = 0)
    private Material slimefunhelper$useTranslucentMaterial(Material original) {
        return slimefunhelper$xrayAlpha == -1 ? original : DefaultMaterials.TRANSLUCENT;
    }
}
