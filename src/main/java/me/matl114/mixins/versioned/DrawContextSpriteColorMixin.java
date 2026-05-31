package me.matl114.mixins.versioned;

import me.matl114.versioned.impl.DrawContext_v1_21_1;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrawContext.class)
public abstract class DrawContextSpriteColorMixin {
    @Shadow
    public abstract void drawTexturedQuad(
            Identifier texture,
            int x1,
            int x2,
            int y1,
            int y2,
            int z,
            float u1,
            float u2,
            float v1,
            float v2,
            float red,
            float green,
            float blue,
            float alpha);

    @Inject(
            method = "drawTexturedQuad(Lnet/minecraft/util/Identifier;IIIIIFFFF)V",
            at = @At("HEAD"),
            cancellable = true)
    private void addColorArguments(
            Identifier texture,
            int x1,
            int x2,
            int y1,
            int y2,
            int z,
            float u1,
            float u2,
            float v1,
            float v2,
            CallbackInfo ci) {
        if (DrawContext_v1_21_1.colorOverride.get()) {
            ci.cancel();
            drawTexturedQuad(
                    texture,
                    x1,
                    x2,
                    y1,
                    y2,
                    z,
                    u1,
                    u2,
                    v1,
                    v2,
                    DrawContext_v1_21_1.r(),
                    DrawContext_v1_21_1.g(),
                    DrawContext_v1_21_1.b(),
                    DrawContext_v1_21_1.a());
        }
    }
}
