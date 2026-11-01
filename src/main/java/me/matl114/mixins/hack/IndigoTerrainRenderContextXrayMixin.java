package me.matl114.mixins.hack;

import me.matl114.hacks.modules.render.Xray;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import net.minecraft.client.render.BlockRenderLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractTerrainRenderContext", remap = false)
public abstract class IndigoTerrainRenderContextXrayMixin {
    @Shadow
    @Final
    protected BlockRenderInfo blockInfo;

    @Inject(method = "bufferQuad", at = @At("HEAD"), cancellable = true)
    private void slimefunhelper$prepareXrayQuad(MutableQuadViewImpl quad, CallbackInfo ci) {
        int alpha = Xray.getAlpha(blockInfo.blockState, blockInfo.blockPos);
        if (alpha == 0) {
            ci.cancel();
            return;
        }

        if (alpha > 0 && alpha < 255) {
            quad.renderLayer(BlockRenderLayer.TRANSLUCENT);
        }
    }

    @Inject(
            method = "bufferQuad",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/fabricmc/fabric/impl/client/indigo/renderer/render/AbstractTerrainRenderContext;bufferQuad(Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;Lnet/minecraft/client/render/VertexConsumer;)V"))
    private void slimefunhelper$applyXrayAlpha(MutableQuadViewImpl quad, CallbackInfo ci) {
        int alpha = Xray.getAlpha(blockInfo.blockState, blockInfo.blockPos);
        if (alpha == -1) return;

        for (int vertex = 0; vertex < 4; vertex++) {
            int color = quad.color(vertex);
            quad.color(vertex, ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF));
        }
    }
}
