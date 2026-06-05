package me.matl114.mixins.hack;

import me.matl114.hacks.modules.render.RenderExtra;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
    private static void onNoRender0(Sprite sprite, MatrixStack matrices, CallbackInfo ci) {
        if (RenderExtra.INSTANCE.noOverlay.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
    private static void onNoRender1(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        if (RenderExtra.INSTANCE.noOverlay.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void onNoRender2(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        if (RenderExtra.INSTANCE.noFireOverlay.get()) {
            ci.cancel();
        }
    }
}
