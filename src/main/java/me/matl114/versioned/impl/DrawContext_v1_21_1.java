package me.matl114.versioned.impl;

import com.google.common.util.concurrent.Runnables;
import me.matl114.versioned.api.MatrixStack;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;

import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

import java.util.List;
import java.util.Optional;

public class DrawContext_v1_21_1 implements VDrawContext {
    private final DrawContext drawContext;
    private final MatrixStack matrixStack;
    private Runnable delayedDrawing = null;
    public DrawContext_v1_21_1(DrawContext context) {
        this.drawContext = context;
        this.matrixStack = MatrixStack.of(context.getMatrices());
    }

    public DrawContext getDrawContext() {
        return this.drawContext;
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
        this.drawContext.setShaderColor(red, green, blue, alpha);
    }
    @Override
    public void setShaderAlpha(float alpha) {
        this.drawContext.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
    }

    @Override
    public void drawGuiTexture(Identifier texture, int x, int y, int z, int width, int height) {
        this.drawContext.drawGuiTexture(texture, x, y, z, width, height);
    }

    @Override
    public void drawGuiTexture(Identifier texture, int i, int j, int k, int l, int x, int y, int z, int width, int height) {
        this.drawContext.drawGuiTexture(texture, i, j, k, l, x, y, z, width, height);
    }

    @Override
    public void drawTexturedQuad(Identifier texture, int x1, int x2, int y1, int y2, int z, float u1, float u2, float v1, float v2) {
        this.drawContext.drawTexturedQuad(texture, x1, x2, y1, y2, z, u1, u2, v1, v2);
    }

    @Override
    public void drawText(TextRenderer textRenderer, OrderedText text, int x, int y, int color, boolean shadow) {
        drawContext.drawText(textRenderer, text, x, y, color, shadow);
    }

    @Override
    public void drawText(TextRenderer textRenderer, @Nullable String text, int x, int y, int color, boolean shadow) {
        drawContext.drawText(textRenderer, text, x, y, color, shadow);
    }

    @Override
    public void enableScissor(int x, int y, int x2, int y2) {
        var trans = matrixStack.peek3D();
        var point1 = new Vector4f(x, y, 0 ,1).mul(trans);
        var point2 = new Vector4f(x2, y2, 0, 1).mul(trans);
        this.drawContext.enableScissor((int) point1.x, (int) point1.y, (int) point2.x, (int) point2.y);
    }

    @Override
    public void disableScissor() {
        this.drawContext.disableScissor();
    }

    @Override
    public void tryDraw() {
        if(this.delayedDrawing != null) {
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
    public void fill(int x1, int y1, int x2, int y2, int z, int color) {

    }

    private void addInternal(Runnable runnable) {
        if(this.delayedDrawing != null) {
            final Runnable prev = this.delayedDrawing;
            this.delayedDrawing = ()->{
                prev.run();
                runnable.run();
            };
        }else{
            this.delayedDrawing = runnable;
        }
    }

    @Override
    public void drawTooltip(TextRenderer textRenderer, List<Text> text, Optional<TooltipData> data, int x, int y) {
        var trans = matrixStack.peek3D();
        var point1 = new Vector4f(x, y, 0 ,1).mul(trans);
        addInternal(()->this.drawContext.drawTooltip(textRenderer, text, data, (int) point1.x, (int) point1.y));

    }

    @Override
    public void drawItem(ItemStack stack, int x, int y, int seed, int z) {
        this.drawContext.drawItem(stack, x, y, seed);
    }

    @Override
    public void drawItemInSlot(TextRenderer textRenderer, ItemStack stack, int x, int y, @Nullable String countOverride) {
        this.drawContext.drawItemInSlot(textRenderer, stack, x, y, countOverride);
    }

}
