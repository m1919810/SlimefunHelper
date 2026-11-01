package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
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

    @Unique
    private static final ThreadLocal<Boolean> slimefunhelper$forceXrayFluidSides = ThreadLocal.withInitial(() -> false);

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
        slimefunhelper$forceXrayFluidSides.set(Xray.isXrayActive());
        if (alpha == 0) {
            slimefunhelper$xrayAlpha.remove();
            slimefunhelper$forceXrayFluidSides.remove();
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void slimefunhelper$clearXray(CallbackInfo ci) {
        slimefunhelper$xrayAlpha.remove();
        slimefunhelper$forceXrayFluidSides.remove();
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

    @ModifyReturnValue(method = "shouldSkipRendering", at = @At("RETURN"))
    private static boolean slimefunhelper$showFluidSides(
            boolean original, Direction side, float height, BlockState neighborState) {
        if (!original
                || !slimefunhelper$forceXrayFluidSides.get()
                || side.getAxis().isVertical()) {
            return original;
        }

        Xray xray = Xray.INSTANCE;
        return xray == null || !xray.isBlocked(neighborState.getBlock(), null);
    }
}
