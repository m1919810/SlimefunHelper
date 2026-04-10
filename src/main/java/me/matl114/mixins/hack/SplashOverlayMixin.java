package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.matl114.hacks.RenderTasks;
import me.matl114.utils.ColorUtils;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
    @Inject(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V",
                            ordinal = 1,
                            shift = At.Shift.AFTER))
    private void onRenderOverlay(
            DrawContext context,
            int mouseX,
            int mouseY,
            float deltaTicks,
            CallbackInfo ci,
            @Local(ordinal = 3) float alpha) {
        if (RenderTasks.getCustomOverlay().enable.get()) {
            Identifier identifier = Identifier.tryParse(
                    RenderTasks.getCustomOverlay().texturePath.get());
            int i = context.getScaledWindowWidth();
            int j = context.getScaledWindowHeight();
            int color = RenderTasks.getCustomOverlay().color.get();
            context.fillGradient(0, 0, i, j, color, color);
            context.drawTexturedQuad(
                    RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                    identifier,
                    0,
                    i,
                    0,
                    j,
                    0,
                    1,
                    0,
                    1,
                    ColorUtils.withAlphaInt(-1, alpha));
        }
    }

    @ModifyExpressionValue(
            method = "renderProgressBar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/ColorHelper;getArgb(IIII)I"))
    private int onOverrideProgressbar(int original, @Local(ordinal = 5) int j) {
        return RenderTasks.getCustomOverlay().colorProgressbar.get().withAlpha(j);
    }
}
