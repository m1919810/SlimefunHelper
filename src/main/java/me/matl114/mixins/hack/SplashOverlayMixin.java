package me.matl114.mixins.hack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import me.matl114.hacks.RenderTasks;
import me.matl114.utils.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
    @Inject(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/DrawContext;drawTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V",
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
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            context.fillGradient(0, 0, i, j, color, color);
            // nmb
            RenderSystem.blendFuncSeparate(
                    GL11.GL_ONE, // SourceFactor.ONE = 1
                    GL11.GL_ONE_MINUS_SRC_ALPHA, // DestFactor.ONE_MINUS_SRC_ALPHA = 771
                    GL11.GL_ONE, // SourceFactor.ONE (alpha) = 1
                    GL11.GL_ONE_MINUS_SRC_ALPHA // DestFactor.ONE_MINUS_SRC_ALPHA (alpha) = 771
                    );
            context.drawTexturedQuad(
                    RenderLayer::getGuiTextured,
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
