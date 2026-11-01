package me.matl114.hooks.mixin.sodium;

import me.matl114.hacks.modules.render.Xray;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumBlockRendererXrayMixin {
    @Unique
    private int slimefunhelper$xrayAlpha;

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void slimefunhelper$applyXray(
            @Coerce Object model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        slimefunhelper$xrayAlpha = Xray.getAlpha(state, pos);
        if (slimefunhelper$xrayAlpha == 0) ci.cancel();
    }

    @Inject(method = "bufferQuad", at = @At("HEAD"), require = 0, expect = 0)
    private void slimefunhelper$setQuadAlpha(
            MutableQuadViewImpl quad, float[] brightnesses, Material material, CallbackInfo ci) {
        if (slimefunhelper$xrayAlpha != -1) {
            for (int vertex = 0; vertex < 4; vertex++) {
                int color = quad.baseColor(vertex);
                quad.setColor(vertex, (slimefunhelper$xrayAlpha << 24) | (color & 0x00FFFFFF));
            }
        }
    }

    @ModifyArg(
            method = "processQuad",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;bufferQuad(Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;[FLnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V",
                            remap = false),
            index = 2,
            require = 0,
            expect = 0)
    private Material slimefunhelper$useTranslucentMaterial(Material original) {
        return slimefunhelper$xrayAlpha == -1 ? original : DefaultMaterials.TRANSLUCENT;
    }
}
