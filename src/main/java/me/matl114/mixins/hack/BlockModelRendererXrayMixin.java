package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.List;
import me.matl114.hacks.modules.render.Xray;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockModelRenderer.class)
public abstract class BlockModelRendererXrayMixin {
    @Unique
    private static final ThreadLocal<Integer> slimefunhelper$xrayAlpha = ThreadLocal.withInitial(() -> -1);

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void slimefunhelper$applyXray(
            BlockRenderView world,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            int overlay,
            CallbackInfo ci) {
        int alpha = Xray.getAlpha(state, pos);
        slimefunhelper$xrayAlpha.set(alpha);
        if (alpha == 0) {
            slimefunhelper$xrayAlpha.remove();
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void slimefunhelper$clearXrayAlpha(CallbackInfo ci) {
        slimefunhelper$xrayAlpha.remove();
    }

    @ModifyArg(
            method = "renderQuad",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;[FFFFF[II)V"),
            index = 6)
    private float slimefunhelper$modifyAlpha(float original) {
        int alpha = slimefunhelper$xrayAlpha.get();
        return alpha == -1 ? original : alpha / 255.0F;
    }

    @ModifyReturnValue(method = "shouldDrawFace", at = @At("RETURN"))
    private static boolean slimefunhelper$modifyDrawSide(
            boolean original,
            BlockRenderView world,
            BlockState state,
            boolean cull,
            Direction facing,
            BlockPos neighborPos) {
        Xray xray = Xray.INSTANCE;
        if (xray == null || !xray.isActive()) return original;

        return xray.modifyDrawSide(state, world, neighborPos.offset(facing.getOpposite()), facing, original);
    }
}
