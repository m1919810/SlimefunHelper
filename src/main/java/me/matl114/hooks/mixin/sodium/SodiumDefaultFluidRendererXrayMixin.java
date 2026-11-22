package me.matl114.hooks.mixin.sodium;

import me.matl114.hacks.modules.render.Xray;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.minecraft.block.BlockState;
import net.minecraft.client.texture.Sprite;
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

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer", remap = false)
public abstract class SodiumDefaultFluidRendererXrayMixin {
    @Shadow
    @Final
    private int[] quadColors;

    @Unique
    private int slimefunhelper$xrayAlpha;

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
            @Coerce Object material,
            @Coerce Object colorProvider,
            Sprite[] sprites,
            CallbackInfo ci) {
        slimefunhelper$xrayAlpha = Xray.getFluidAlpha(fluidState, blockPos);
        if (slimefunhelper$xrayAlpha == 0) ci.cancel();
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
