package me.matl114.versioned.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import me.matl114.versioned.api.MatrixStack;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

public class DrawContext_v1_21_11 implements VDrawContext {
    private final DrawContext drawContext;
    private final MatrixStack matrixStack;

    public DrawContext_v1_21_11(DrawContext context) {
        this.drawContext = context;
        this.matrixStack = MatrixStack.of(context);
    }

    @Override
    public DrawContext pushMatrix() {
        return this.drawContext;
    }

    @Override
    public DrawContext popMatrix() {
        return this.drawContext;
    }

    @Override
    public MatrixStack getMatrices() {
        return this.matrixStack;
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

    // r g  b a
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

    public static int getShaderRGB(int a) {
        return ColorHelper.mix(getShaderRGB(), a);
    }

    // todo: add cached shader color
    // todo: add color computation

    @Override
    public void drawGuiTexture(Identifier texture, int x, int y, int z, int width, int height) {

        this.drawContext.drawGuiTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, width, height, getShaderRGB());
    }

    @Override
    public void drawGuiTexture(
            Identifier texture, int i, int j, int k, int l, int x, int y, int z, int width, int height) {
        this.drawContext.drawGuiTexture(
                RenderPipelines.GUI_TEXTURED, texture, i, j, k, l, x, y, width, height, getShaderRGB());
    }

    @Override
    public void drawTexturedQuad(
            Identifier texture, int x1, int x2, int y1, int y2, int z, float u1, float u2, float v1, float v2) {
        this.drawContext.drawTexturedQuad(
                RenderPipelines.GUI_TEXTURED, texture, x1, x2, y1, y2, u1, u2, v1, v2, getShaderRGB());
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
    public void enableScissor(int x, int y, int width, int height) {
        this.drawContext.enableScissor(x, y, width, height);
    }

    @Override
    public void disableScissor() {
        this.drawContext.disableScissor();
    }

    @Override
    public void tryDraw() {}

    @Override
    public void fillGuiGradient(int x1, int y1, int x2, int y2, int color1, int color2, int depth) {
        this.drawContext.fillGradient(x1, y1, x2, y2, getShaderRGB(color1), getShaderRGB(color2));
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int z, int color) {
        this.drawContext.fill(x1, y1, x2, y2, getShaderRGB(color));
    }

    private void addInternal(Runnable runnable) {
        if (this.drawContext.tooltipDrawer != null) {
            final Runnable prev = this.drawContext.tooltipDrawer;
            this.drawContext.tooltipDrawer = () -> {
                prev.run();
                runnable.run();
            };
        } else {
            this.drawContext.tooltipDrawer = runnable;
        }
    }
    // todo: color
    @Override
    public void drawTooltip(TextRenderer textRenderer, List<Text> text, Optional<TooltipData> data, int x, int y) {
        var trans = matrixStack.peek3D();
        var point1 = new Vector4f(x, y, 0, 1).mul(trans);
        // Tooltips are draw in delay callback, so transfer before the call
        List<TooltipComponent> list = (List)
                text.stream().map(Text::asOrderedText).map(TooltipComponent::of).collect(Util.toArrayList());
        data.ifPresent((datax) -> {
            list.add(list.isEmpty() ? 0 : 1, TooltipComponent.of(datax));
        });
        if (!list.isEmpty()) {
            addInternal(() -> {
                drawContext.drawTooltipImmediately(
                        textRenderer, list, (int) point1.x, (int) point1.y, HoveredTooltipPositioner.INSTANCE, null);
            });
        }
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
