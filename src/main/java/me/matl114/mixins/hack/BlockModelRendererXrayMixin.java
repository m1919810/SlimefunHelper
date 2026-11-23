package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.hacks.modules.render.Xray;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.BlockView;
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

    @Inject(
            method =
                    "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",
            at = @At("HEAD"),
            cancellable = true)
    private void slimefunhelper$applyXray(
            BlockRenderView world,
            BakedModel model,
            BlockState state,
            BlockPos pos,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            Random random,
            long seed,
            int overlay,
            CallbackInfo ci) {
        int alpha = Xray.getAlpha(state, pos);
        slimefunhelper$xrayAlpha.set(alpha);
        if (alpha == 0) {
            slimefunhelper$xrayAlpha.remove();
            ci.cancel();
        }
    }

    @Inject(
            method =
                    "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",
            at = @At("RETURN"))
    private void slimefunhelper$clearXrayAlpha(CallbackInfo ci) {
        slimefunhelper$xrayAlpha.remove();
    }

    @ModifyArg(
            method = "renderQuad",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;[FFFFF[IIZ)V"),
            index = 6)
    private float slimefunhelper$modifyAlpha(float original) {
        int alpha = slimefunhelper$xrayAlpha.get();
        return alpha == -1 ? original : alpha / 255.0F;
    }

    @WrapOperation(
            method = {"renderSmooth", "renderFlat"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/block/Block;shouldDrawSide(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;Lnet/minecraft/util/math/BlockPos;)Z"))
    private boolean slimefunhelper$modifyDrawSide(
            BlockState state,
            BlockView blockView,
            BlockPos blockPos,
            Direction facing,
            BlockPos otherPos,
            Operation<Boolean> original,
            @Local(argsOnly = true) BlockRenderView world,
            @Local(argsOnly = true) BlockPos pos) {
        boolean draw = original.call(state, blockView, blockPos, facing, otherPos);
        Xray xray = Xray.INSTANCE;
        if (xray == null || !xray.isActive()) return draw;

        return xray.modifyDrawSide(state, world, pos, facing, draw);
    }
}
