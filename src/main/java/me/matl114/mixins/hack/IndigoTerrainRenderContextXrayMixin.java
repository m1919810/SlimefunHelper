package me.matl114.mixins.hack;

import me.matl114.hacks.modules.render.Xray;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
// Indigo 1.7.0 (fabric-api for 1.21.1) has renderQuad(MutableQuadViewImpl) where newer Indigo has bufferQuad
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractBlockRenderContext", remap = false)
public abstract class IndigoTerrainRenderContextXrayMixin {
    @Shadow
    @Final
    protected BlockRenderInfo blockInfo;

    @Inject(method = "renderQuad", at = @At("HEAD"), cancellable = true)
    private void slimefunhelper$prepareXrayQuad(MutableQuadViewImpl quad, CallbackInfo ci) {
        int alpha = Xray.getAlpha(blockInfo.blockState, blockInfo.blockPos);
        if (alpha == 0) {
            ci.cancel();
            return;
        }
    }

    @ModifyArg(
            method = "renderQuad(Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/fabricmc/fabric/impl/client/indigo/renderer/render/AbstractBlockRenderContext;getVertexConsumer(Lnet/minecraft/client/render/RenderLayer;)Lnet/minecraft/client/render/VertexConsumer;"),
            index = 0)
    private RenderLayer slimefunhelper$useTranslucentLayer(RenderLayer original) {
        int alpha = Xray.getAlpha(blockInfo.blockState, blockInfo.blockPos);
        return alpha > 0 && alpha < 255 ? RenderLayer.getTranslucent() : original;
    }

    @Inject(
            method = "renderQuad",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/fabricmc/fabric/impl/client/indigo/renderer/render/AbstractBlockRenderContext;bufferQuad(Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;Lnet/minecraft/client/render/VertexConsumer;)V"))
    private void slimefunhelper$applyXrayAlpha(MutableQuadViewImpl quad, CallbackInfo ci) {
        int alpha = Xray.getAlpha(blockInfo.blockState, blockInfo.blockPos);
        if (alpha == -1) return;

        for (int vertex = 0; vertex < 4; vertex++) {
            int color = quad.color(vertex);
            quad.color(vertex, ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF));
        }
    }
}
