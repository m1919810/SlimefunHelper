package me.matl114.versioned.api;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import me.matl114.versioned.impl.DrawContext_v1_21_1;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public interface VDrawContext {
    // MatrixStack will be deprecated in the future in GUI drawing, to compat old code, we have to use these wrappers
    @Nonnull
    public static VDrawContext of(DrawContext drawContext) {
        return new DrawContext_v1_21_1(drawContext);
    }
    // These method push and pop DrawContext's matrixStack to ensure that it is same as getMatrices()
    public DrawContext pushMatrix();

    public DrawContext popMatrix();

    public void pushLayer(int depth);

    public void popLayer();

    public MatrixStack getMatrices();

    public void setShaderColor(int rgba);

    public void setShaderColor(final float red, final float green, final float blue, final float alpha);

    public void setShaderAlpha(float alpha);

    default void drawTexture(Identifier texture, int x, int y, int u, int v, int width, int height) {
        this.drawTexture(texture, x, y, 0, (float) u, (float) v, width, height, 256, 256);
    }

    /**
     * Draws a textured rectangle from a region in a texture.
     *
     * <p>The width and height of the region are the same as
     * the dimensions of the rectangle.
     */
    default void drawTexture(
            Identifier texture,
            int x,
            int y,
            int z,
            float u,
            float v,
            int width,
            int height,
            int textureWidth,
            int textureHeight) {
        this.drawTexture(texture, x, x + width, y, y + height, z, width, height, u, v, textureWidth, textureHeight);
    }

    /**
     * Draws a textured rectangle from a region in a texture.
     */
    default void drawTexture(
            Identifier texture,
            int x,
            int y,
            int width,
            int height,
            float u,
            float v,
            int regionWidth,
            int regionHeight,
            int textureWidth,
            int textureHeight) {
        this.drawTexture(
                texture, x, x + width, y, y + height, 0, regionWidth, regionHeight, u, v, textureWidth, textureHeight);
    }

    /**
     * Draws a textured rectangle from a region in a texture.
     *
     * <p>The width and height of the region are the same as
     * the dimensions of the rectangle.
     */
    default void drawTexture(
            Identifier texture,
            int x,
            int y,
            float u,
            float v,
            int width,
            int height,
            int textureWidth,
            int textureHeight) {
        this.drawTexture(texture, x, y, width, height, u, v, width, height, textureWidth, textureHeight);
    }

    default void drawTexture(
            Identifier texture,
            int x1,
            int x2,
            int y1,
            int y2,
            int z,
            int regionWidth,
            int regionHeight,
            float u,
            float v,
            int textureWidth,
            int textureHeight) {
        this.drawTexturedQuad(
                texture,
                x1,
                x2,
                y1,
                y2,
                z,
                (u + 0.0F) / (float) textureWidth,
                (u + (float) regionWidth) / (float) textureWidth,
                (v + 0.0F) / (float) textureHeight,
                (v + (float) regionHeight) / (float) textureHeight);
    }

    default void drawGuiTexture(Identifier texture, int x, int y, int width, int height) {
        this.drawGuiTexture(texture, x, y, 0, width, height);
    }

    public void drawGuiTexture(Identifier texture, int x, int y, int z, int width, int height);
    //
    public void drawGuiTexture(
            Identifier texture,
            int textureWidth,
            int textureHeight,
            int u,
            int v,
            int x,
            int y,
            int z,
            int width,
            int height);

    default void drawSprite(Sprite sprite, int x, int y, int z, int width, int height) {
        if (width != 0 && height != 0) {
            this.drawTexturedQuad(
                    sprite.getAtlasId(),
                    x,
                    x + width,
                    y,
                    y + height,
                    z,
                    sprite.getMinU(),
                    sprite.getMaxU(),
                    sprite.getMinV(),
                    sprite.getMaxV());
        }
    }

    public void drawTexturedQuad(
            Identifier texture, int x1, int x2, int y1, int y2, int z, float u1, float u2, float v1, float v2);

    public void drawGuiTextureQuad(
            Identifier texture, int x1, int x2, int y1, int y2, int z, float u1, float u2, float v1, float v2);
    //    {
    //        float baseSpriteWidth = 256f;
    //        int ui1 = (int) (u1 * baseSpriteWidth);
    //        int vi1 = (int) (v1 * baseSpriteWidth);
    //        int width = x2 - x1;
    //        int height = y2 - y1;
    //        // 要求 u1  + (width / textureWidth) = u2
    //        // u1 * base + (width * base  / textureWidth ) = u2 * base
    //        //
    //        float textureWidth = (width / (u2 - u1)) * baseSpriteWidth;
    //        float textureHeight = (height / (v2 - v1)) * baseSpriteWidth;
    //        drawGuiTexture(texture, (int) textureWidth, (int) textureHeight, ui1, vi1, x1, y1, z, width, height);
    //    }

    public Sprite getGuiSprite(Identifier i);

    default void drawCenteredTextWithShadow(
            TextRenderer textRenderer, OrderedText text, int centerX, int y, int color) {
        this.drawText(textRenderer, text, centerX - textRenderer.getWidth(text) / 2, y, color, true);
    }

    default void drawTextWithShadow(TextRenderer textRenderer, @Nullable String text, int x, int y, int color) {
        this.drawText(textRenderer, text, x, y, color, true);
    }

    public void drawText(TextRenderer textRenderer, OrderedText text, int x, int y, int color, boolean shadow);

    public void drawText(TextRenderer textRenderer, @Nullable String text, int x, int y, int color, boolean shadow);
    // just pass the relative coord
    public void enableScissor(int x, int y, int x2, int y2);

    public void disableScissor();

    /**
     * this method must be called if a VDrawContext is about to be released
     */
    public void tryDraw();

    public void fillGuiGradient(int x1, int y1, int x2, int y2, int color1, int color2, int depth);

    public void fillGuiGradient(
            int x1, int y1, int x2, int y2, int color1, int color2, int color3, int color4, int depth);

    default void fill(int x1, int y1, int x2, int y2, int color) {
        this.fill(x1, y1, x2, y2, 0, color);
    }

    public void fill(int x1, int y1, int x2, int y2, int z, int color);

    default void lineGui(int x1, int y1, int x2, int y2, int color2, int depth) {
        lineGuiGradient(x1, y1, x2, y2, color2, color2, depth);
    }

    public void lineGuiGradient(int x1, int y1, int x2, int y2, int color1, int color2, int depth);

    public void drawTooltip(TextRenderer textRenderer, List<Text> text, Optional<TooltipData> data, int x, int y);

    public void drawItem(ItemStack stack, int x, int y, int seed, int z);

    public void drawItemInSlot(
            TextRenderer textRenderer, ItemStack stack, int x, int y, @Nullable String countOverride);
}
