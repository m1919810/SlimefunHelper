package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.hacks.modules.render.Xray;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public abstract class FluidRendererXrayMixin {
    @Unique
    private static final ThreadLocal<Integer> slimefunhelper$xrayAlpha = ThreadLocal.withInitial(() -> -1);

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void slimefunhelper$applyXray(
            BlockRenderView world,
            BlockPos pos,
            VertexConsumer vertexConsumer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci) {
        int alpha = Xray.getFluidAlpha(fluidState, pos);
        slimefunhelper$xrayAlpha.set(alpha);
        if (alpha == 0) {
            slimefunhelper$xrayAlpha.remove();
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void slimefunhelper$clearXray(CallbackInfo ci) {
        slimefunhelper$xrayAlpha.remove();
    }

    @ModifyArg(
            method = "vertex",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/VertexConsumer;color(FFFF)Lnet/minecraft/client/render/VertexConsumer;"),
            index = 3)
    private float slimefunhelper$modifyAlpha(float original) {
        int alpha = slimefunhelper$xrayAlpha.get();
        return alpha == -1 ? original : alpha / 255.0F;
    }

    @WrapOperation(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/block/FluidRenderer;shouldRenderSide(Lnet/minecraft/fluid/FluidState;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Direction;Lnet/minecraft/fluid/FluidState;)Z"))
    private boolean slimefunhelper$showFluidSides(
            FluidState state,
            BlockState blockState,
            Direction side,
            FluidState neighborState,
            Operation<Boolean> original,
            @Local(argsOnly = true) BlockRenderView world,
            @Local(argsOnly = true) BlockPos pos) {
        boolean draw = original.call(state, blockState, side, neighborState);
        if (draw || !Xray.isXrayActive() || side.getAxis().isVertical()) return draw;
        Xray xray = Xray.INSTANCE;
        BlockPos neighborPos = pos.offset(side);
        return xray.isBlocked(world.getBlockState(neighborPos).getBlock(), neighborPos)
                && !neighborState.getFluid().matchesType(state.getFluid());
    }
}
