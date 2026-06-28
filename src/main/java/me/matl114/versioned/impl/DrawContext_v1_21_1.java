package me.matl114.versioned.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import me.matl114.versioned.api.MatrixStack;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
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

public class DrawContext_v1_21_1 implements VDrawContext {
    private final DrawContext drawContext;
    private final MatrixStack matrixStack;
    private Runnable delayedDrawing = null;

    public DrawContext_v1_21_1(DrawContext context) {
        this.drawContext = context;
        this.matrixStack = MatrixStack.of(context.getMatrices());
    }

    private static final int[] cachedShaderColor = new int[4];

    static {
        Arrays.fill(cachedShaderColor, 255);
    }

    public DrawContext getDrawContext() {
        return this.drawContext;
    }

    @Override
    public DrawContext pushMatrix() {
        this.drawContext.getMatrices().push();
        return this.drawContext;
    }

    @Override
    public DrawContext popMatrix() {
        this.drawContext.getMatrices().pop();
        return this.drawContext;
    }

    @Override
    public void pushLayer(int depth) {
        this.drawContext.getMatrices().push();
        this.drawContext.getMatrices().translate(0, 0, depth);
    }

    @Override
    public void popLayer() {
        this.drawContext.getMatrices().pop();
    }

    @Override
    public MatrixStack getMatrices() {
        return this.matrixStack;
    }

    public static int getShaderRGB() {
        return (cachedShaderColor[3] << 24)
                | (cachedShaderColor[0] << 16)
                | (cachedShaderColor[1] << 8)
                | cachedShaderColor[2];
    }

    public static int getAlpha(int argb) {
        return argb >>> 24;
    }

    public static int getRed(int argb) {
        return argb >> 16 & 255;
    }

    public static int getGreen(int argb) {
        return argb >> 8 & 255;
    }

    public static int getBlue(int argb) {
        return argb & 255;
    }

    public static int getArgb(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    public static int getShaderRGB(int a) {
        int first = getShaderRGB();
        int second = a;
        if (first == -1) {
            return second;
        } else {
            return second == -1
                    ? first
                    : getArgb(
                            getAlpha(first) * getAlpha(second) / 255,
                            getRed(first) * getRed(second) / 255,
                            getGreen(first) * getGreen(second) / 255,
                            getBlue(first) * getBlue(second) / 255);
        }
    }

    public static float r() {
        return cachedShaderColor[0] / 255.0f;
    }

    public static float g() {
        return cachedShaderColor[1] / 255.0f;
    }

    public static float b() {
        return cachedShaderColor[2] / 255.0f;
    }

    public static float a() {
        return cachedShaderColor[3] / 255.0f;
    }

    public void setShaderColor(int rgba) {
        cachedShaderColor[0] = rgba >> 16 & 255;
        cachedShaderColor[1] = rgba >> 8 & 255;
        cachedShaderColor[2] = rgba & 255;
        cachedShaderColor[3] = rgba >>> 24;
    }

    @Override
    public void setShaderColor(float red, float green, float blue, float alpha) {
        cachedShaderColor[0] = ColorHelper.channelFromFloat(red);
        cachedShaderColor[1] = ColorHelper.channelFromFloat(green);
        cachedShaderColor[2] = ColorHelper.channelFromFloat(blue);
        cachedShaderColor[3] = ColorHelper.channelFromFloat(alpha);
    }

    @Override
    public void setShaderAlpha(float alpha) {
        cachedShaderColor[3] = ColorHelper.channelFromFloat(alpha);
    }

    public static ThreadLocal<Boolean> colorOverride = ThreadLocal.withInitial(() -> false);

    @Override
    public void drawGuiTexture(Identifier texture, int x, int y, int z, int width, int height) {
        colorOverride.set(true);
        try {
            this.drawContext.drawGuiTexture(texture, x, y, z, width, height);
        } finally {
            colorOverride.set(false);
        }
    }

    @Override
    public void drawGuiTexture(
            Identifier texture, int i, int j, int k, int l, int x, int y, int z, int width, int height) {
        colorOverride.set(true);
        try {
            this.drawContext.drawGuiTexture(texture, i, j, k, l, x, y, z, width, height);
        } finally {
            colorOverride.set(false);
        }
    }

    @Override
    public void drawTexturedQuad(
            Identifier texture, int x1, int x2, int y1, int y2, int z, float u1, float u2, float v1, float v2) {
        this.drawContext.drawTexturedQuad(texture, x1, x2, y1, y2, z, u1, u2, v1, v2, r(), g(), b(), a());
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
        var trans = matrixStack.peek3D();
        var point1 = new Vector4f(x, y, 0, 1).mul(trans);
        var point2 = new Vector4f(x2, y2, 0, 1).mul(trans);
        this.drawContext.enableScissor((int) point1.x, (int) point1.y, (int) point2.x, (int) point2.y);
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
        this.drawContext.tryDraw();
    }

    @Override
    public void fillGuiGradient(int x1, int y1, int x2, int y2, int color1, int color2, int depth) {
        this.drawContext.fillGradient(RenderLayer.getGuiOverlay(), x1, y1, x2, y2, color1, color2, depth);
    }

    @Override
    public void fillGuiGradient(
            int x1, int y1, int x2, int y2, int color1, int color2, int color3, int color4, int depth) {
        VertexConsumer vertexConsumer = this.drawContext.getVertexConsumers().getBuffer(RenderLayer.getGui());
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
        this.drawContext.drawItemInSlot(textRenderer, stack, x, y, countOverride);
    }
}
