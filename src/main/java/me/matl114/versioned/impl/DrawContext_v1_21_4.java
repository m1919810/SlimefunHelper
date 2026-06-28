package me.matl114.versioned.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import me.matl114.versioned.api.MatrixStack;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Scaling;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class DrawContext_v1_21_4 implements VDrawContext {
    private final DrawContext drawContext;
    private final MatrixStack matrixStack;
    private Runnable delayedDrawing = null;

    public DrawContext_v1_21_4(DrawContext context) {
        this.drawContext = context;
        this.matrixStack = MatrixStack.of(context.getMatrices());
    }

    public DrawContext getDrawContext() {
        return this.drawContext;
    }

    @Override
    public DrawContext pushMatrix() {
        this.matrixStack.pushMatrix();
        return this.drawContext;
    }

    @Override
    public DrawContext popMatrix() {
        this.matrixStack.popMatrix();
        return this.drawContext;
    }

    @Override
    public void pushLayer(int depth) {
        this.matrixStack.pushMatrix();
        this.drawContext.getMatrices().translate(0, 0, depth);
    }

    @Override
    public void popLayer() {
        this.matrixStack.popMatrix();
    }

    @Override
    public MatrixStack getMatrices() {
        return this.matrixStack;
    }

    private static final int[] cachedShaderColor = new int[4];

    static {
        Arrays.fill(cachedShaderColor, 255);
    }

    public static int getShaderRGB() {
        return (cachedShaderColor[3] << 24)
                | (cachedShaderColor[0] << 16)
                | (cachedShaderColor[1] << 8)
                | cachedShaderColor[2];
    }

    public void setShaderColor(int rgba) {
        cachedShaderColor[0] = ColorHelper.getRed(rgba);
        cachedShaderColor[1] = ColorHelper.getGreen(rgba);
        cachedShaderColor[2] = ColorHelper.getBlue(rgba);
        cachedShaderColor[3] = ColorHelper.getAlpha(rgba);
    }

    @Override
    public void setShaderColor(float red, float green, float blue, float alpha) {
        cachedShaderColor[0] = ColorHelper.channelFromFloat(red);
        cachedShaderColor[1] = ColorHelper.channelFromFloat(green);
        cachedShaderColor[2] = ColorHelper.channelFromFloat(blue);
        cachedShaderColor[3] = ColorHelper.channelFromFloat(alpha);
    }

    public void setShaderAlpha(float alpha) {
        cachedShaderColor[3] = ColorHelper.channelFromFloat(alpha);
    }

    @Override
    public void drawGuiTexture(Identifier texture, int x, int y, int z, int width, int height) {
        if (z != 0) {
            pushLayer(z);
        }
        try {
            this.drawContext.drawGuiTexture(RenderLayer::getGuiTextured, texture, x, y, width, height, getShaderRGB());
        } finally {
            if (z != 0) {
                popLayer();
            }
        }
    }

    public static int getShaderRGB(int a) {
        return ColorHelper.mix(getShaderRGB(), a);
    }

    @Override
    public void drawGuiTexture(
            Identifier texture, int i, int j, int k, int l, int x, int y, int z, int width, int height) {
        if (z != 0) {
            pushLayer(z);
        }
        try {

            drawGuiTextureWithColorArgument(
                    RenderLayer::getGuiTextured, texture, i, j, k, l, x, y, width, height, getShaderRGB());
        } finally {
            if (z != 0) {
                popLayer();
            }
        }
    }

    private void drawGuiTextureWithColorArgument(
            Function<Identifier, RenderLayer> renderLayers,
            Identifier sprite,
            int textureWidth,
            int textureHeight,
            int u,
            int v,
            int x,
            int y,
            int width,
            int height,
            int color) {
        Sprite sprite2 = this.drawContext.guiAtlasManager.getSprite(sprite);
        Scaling scaling = this.drawContext.guiAtlasManager.getScaling(sprite2);
        if (scaling instanceof Scaling.Stretch) {
            this.drawContext.drawSpriteRegion(
                    renderLayers, sprite2, textureWidth, textureHeight, u, v, x, y, width, height, color);
        } else {
            this.drawContext.enableScissor(x, y, x + width, y + height);
            this.drawContext.drawGuiTexture(renderLayers, sprite, x - u, y - v, textureWidth, textureHeight, color);
            this.drawContext.disableScissor();
        }
    }

    @Override
    public void drawTexturedQuad(
            Identifier texture, int x1, int x2, int y1, int y2, int z, float u1, float u2, float v1, float v2) {
        if (z != 0) {
            pushLayer(z);
        }
        try {
            this.drawContext.drawTexturedQuad(
                    RenderLayer::getGuiTextured, texture, x1, x2, y1, y2, u1, u2, v1, v2, getShaderRGB());
        } finally {
            if (z != 0) {
                popLayer();
            }
        }
    }

    @Override
    public void drawGuiTextureQuad(
            Identifier texture, int x1, int x2, int y1, int y2, int z, float u1, float u2, float v1, float v2) {
        Sprite sprite = getGuiSprite(texture);
        float sMinU = sprite.getMinU();
        float sMaxU = sprite.getMaxU();
        float sMinV = sprite.getMinV();
        float sMaxV = sprite.getMaxV();
        // 映射：u 从 [0,1] 映射到 [sMinU, sMaxU]，v 同理
        float finalU1 = sMinU + u1 * (sMaxU - sMinU);
        float finalU2 = sMinU + u2 * (sMaxU - sMinU);
        float finalV1 = sMinV + v1 * (sMaxV - sMinV);
        float finalV2 = sMinV + v2 * (sMaxV - sMinV);
        this.drawTexturedQuad(sprite.getAtlasId(), x1, x2, y1, y2, z, finalU1, finalU2, finalV1, finalV2);
    }

    @Override
    public Sprite getGuiSprite(Identifier i) {
        return this.drawContext.guiAtlasManager.getSprite(i);
    }

    @Override
    public void drawText(TextRenderer textRenderer, OrderedText text, int x, int y, int color, boolean shadow) {
        drawContext.drawText(textRenderer, text, x, y, getShaderRGB(color), shadow);
    }

    @Override
    public void drawText(TextRenderer textRenderer, @Nullable String text, int x, int y, int color, boolean shadow) {
        drawContext.drawText(textRenderer, text, x, y, getShaderRGB(color), shadow);
    }

    @Override
    public void enableScissor(int x, int y, int x2, int y2) {
        this.drawContext.enableScissor(x, y, x2, y2);
    }

    @Override
    public void disableScissor() {
        this.drawContext.disableScissor();
    }

    @Override
    public void tryDraw() {
        if (this.delayedDrawing != null) {
            this.delayedDrawing.run();
            this.delayedDrawing = null;
        }
        this.drawContext.draw();
    }

    @Override
    public void fillGuiGradient(int x1, int y1, int x2, int y2, int color1, int color2, int depth) {
        this.drawContext.fillGradient(RenderLayer.getGui(), x1, y1, x2, y2, color1, color2, depth);
    }

    @Override
    public void fillGuiGradient(
            int x1, int y1, int x2, int y2, int color1, int color2, int color3, int color4, int depth) {
        VertexConsumer vertexConsumer = this.drawContext.vertexConsumers.getBuffer(RenderLayer.getGui());
        Matrix4f matrix4f = this.drawContext.getMatrices().peek().getPositionMatrix();
        vertexConsumer.vertex(matrix4f, (float) x1, (float) y1, (float) depth).color(color1);
        vertexConsumer.vertex(matrix4f, (float) x1, (float) y2, (float) depth).color(color2);
        vertexConsumer.vertex(matrix4f, (float) x2, (float) y2, (float) depth).color(color3);
        vertexConsumer.vertex(matrix4f, (float) x2, (float) y1, (float) depth).color(color4);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int z, int color) {
        this.drawContext.fill(x1, y1, x2, y2, z, color);
    }

    @Override
    public void lineGuiGradient(int x1, int y1, int x2, int y2, int color1, int color2, int depth) {
        VertexConsumer vertexConsumer = this.drawContext.vertexConsumers.getBuffer(Render_v1_21_4.LINES);
        var matrix4f = this.drawContext.getMatrices().peek();
        Vector3f normal = new Vector3f(x2 - x1, y2 - y1, 0).normalize();
        vertexConsumer.vertex(matrix4f, (float) x1, (float) y1, (float) depth).color(color1).normal(matrix4f, normal.x, normal.y, normal.z);
        vertexConsumer.vertex(matrix4f, (float) x2, (float) y2, (float) depth).color(color2).normal(matrix4f, normal.x, normal.y, normal.z);
        this.drawContext.vertexConsumers.draw(Render_v1_21_4.LINES);
    }

    private void addInternal(Runnable runnable) {
        if (this.delayedDrawing != null) {
            final Runnable prev = this.delayedDrawing;
            this.delayedDrawing = () -> {
                prev.run();
                runnable.run();
            };
        } else {
            this.delayedDrawing = runnable;
        }
    }

    @Override
    public void drawTooltip(TextRenderer textRenderer, List<Text> text, Optional<TooltipData> data, int x, int y) {
        var trans = matrixStack.peek3D();
        var point1 = new Vector4f(x, y, 0, 1).mul(trans);
        addInternal(() -> this.drawContext.drawTooltip(textRenderer, text, data, (int) point1.x, (int) point1.y));
    }

    @Override
    public void drawItem(ItemStack stack, int x, int y, int seed, int z) {
        this.drawContext.drawItem(stack, x, y, seed);
    }

    @Override
    public void drawItemInSlot(
            TextRenderer textRenderer, ItemStack stack, int x, int y, @Nullable String countOverride) {
        this.drawContext.drawStackOverlay(textRenderer, stack, x, y, countOverride);
    }
}
