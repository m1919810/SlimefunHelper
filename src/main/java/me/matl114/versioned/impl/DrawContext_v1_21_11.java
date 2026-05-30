package me.matl114.versioned.impl;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.*;
import java.util.List;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.accessors.GuiRendererStateAccess;
import me.matl114.versioned.api.MatrixStack;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
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
        getMatrices().pushMatrix();
        return this.drawContext;
    }

    @Override
    public DrawContext popMatrix() {
        getMatrices().popMatrix();
        return this.drawContext;
    }

    @Override
    public MatrixStack getMatrices() {
        return this.matrixStack;
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

    public static int getCurrentDepthLevel() {
        return depthDeque.isEmpty() ? 0 : depthDeque.peekLast().index();
    }

    private static final ArrayDeque<IndexEntry<LayerSnapshot>> depthDeque = new ArrayDeque<>(4);

    private record LayerSnapshot(GuiRenderState.Layer layer, @Nullable ScreenRect bounds) {}

    public void pushLayer(int depth) {
        int level = getCurrentDepthLevel();
        LayerSnapshot snapshot =
                new LayerSnapshot(drawContext.state.currentLayer, drawContext.state.currentLayerBounds);
        depthDeque.addLast(new IndexEntry<>(depth + level, snapshot));
        GuiRendererStateAccess.of(drawContext.state).setLayerToDepth();
        drawContext.state.currentLayerBounds = null;
    }

    public void popLayer() {
        var idx = depthDeque.removeLast();
        drawContext.state.currentLayer = idx.val().layer();
        drawContext.state.currentLayerBounds = idx.val().bounds();
    }

    @Override
    public void drawGuiTexture(Identifier texture, int x, int y, int z, int width, int height) {
        if (z != 0) {
            pushLayer(z);
        }
        try {
            this.drawContext.drawGuiTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, width, height, getShaderRGB());
        } finally {
            if (z != 0) {
                popLayer();
            }
        }
    }

    @Override
    public void drawGuiTexture(
            Identifier texture, int i, int j, int k, int l, int x, int y, int z, int width, int height) {
        if (z != 0) {
            pushLayer(z);
        }
        try {
            this.drawContext.drawGuiTexture(
                    RenderPipelines.GUI_TEXTURED, texture, i, j, k, l, x, y, width, height, getShaderRGB());
        } finally {
            if (z != 0) {
                popLayer();
            }
        }
    }

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
    public Sprite getGuiSprite(Identifier id) {
        return this.drawContext.spriteAtlasTexture.getSprite(id);
    }

    @Override
    public void drawTexturedQuad(
            Identifier texture, int x1, int x2, int y1, int y2, int z, float u1, float u2, float v1, float v2) {
        if (z != 0) {
            pushLayer(z);
        }
        try {
            this.drawContext.drawTexturedQuad(
                    RenderPipelines.GUI_TEXTURED, texture, x1, x2, y1, y2, u1, u2, v1, v2, getShaderRGB());
        } finally {
            if (z != 0) {
                popLayer();
            }
        }
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
        if (depth != 0) {
            pushLayer(depth);
        }
        try {
            this.drawContext.fillGradient(x1, y1, x2, y2, getShaderRGB(color1), getShaderRGB(color2));
        } finally {
            if (depth != 0) {
                popLayer();
            }
        }
    }

    @Override
    public void fillGuiGradient(
            int x1, int y1, int x2, int y2, int color1, int color2, int color3, int color4, int depth) {
        if (depth != 0) {
            pushLayer(depth);
        }
        try {
            this.drawContext.state.addSimpleElement(new ColoredQuad2DGuiElementRenderState(
                    RenderPipelines.GUI,
                    TextureSetup.empty(),
                    new Matrix3x2f(this.drawContext.getMatrices()),
                    x1,
                    y1,
                    x2,
                    y2,
                    color1,
                    color2,
                    color3,
                    color4,
                    this.drawContext.scissorStack.peekLast()));
        } finally {
            if (depth != 0) {
                popLayer();
            }
        }
    }

    public static record ColoredQuad2DGuiElementRenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2fc pose,
            int x0,
            int y0,
            int x1,
            int y1,
            int col1,
            int col2,
            int col3,
            int col4,
            @Nullable ScreenRect scissorArea,
            @Nullable ScreenRect bounds)
            implements SimpleGuiElementRenderState {
        public ColoredQuad2DGuiElementRenderState(
                RenderPipeline pipeline,
                TextureSetup textureSetup,
                Matrix3x2fc pose,
                int x0,
                int y0,
                int x1,
                int y1,
                int col1,
                int col2,
                int col3,
                int col4,
                @Nullable ScreenRect scissorArea) {
            this(
                    pipeline,
                    textureSetup,
                    pose,
                    x0,
                    y0,
                    x1,
                    y1,
                    col1,
                    col2,
                    col3,
                    col4,
                    scissorArea,
                    createBounds(x0, y0, x1, y1, pose, scissorArea));
        }

        @Override
        public void setupVertices(VertexConsumer vertices) {
            vertices.vertex(this.pose(), (float) this.x0(), (float) this.y0()).color(this.col1());
            vertices.vertex(this.pose(), (float) this.x0(), (float) this.y1()).color(this.col2());
            vertices.vertex(this.pose(), (float) this.x1(), (float) this.y1()).color(this.col3());
            vertices.vertex(this.pose(), (float) this.x1(), (float) this.y0()).color(this.col4());
        }

        private static @Nullable ScreenRect createBounds(
                int x0, int y0, int x1, int y1, Matrix3x2fc pose, @Nullable ScreenRect scissorArea) {
            ScreenRect screenRect = (new ScreenRect(x0, y0, x1 - x0, y1 - y0)).transformEachVertex(pose);
            return scissorArea != null ? scissorArea.intersection(screenRect) : screenRect;
        }
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int z, int color) {
        if (z != 0) {
            pushLayer(z);
        }
        try {
            this.drawContext.fill(x1, y1, x2, y2, getShaderRGB(color));
        } finally {
            if (z != 0) {
                popLayer();
            }
        }
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
        if (z != 0) {
            pushLayer(z);
        }
        try {
            this.drawContext.drawItem(stack, x, y, seed);
        } finally {
            if (z != 0) {
                popLayer();
            }
        }
    }

    @Override
    public void drawItemInSlot(
            TextRenderer textRenderer, ItemStack stack, int x, int y, @Nullable String countOverride) {
        this.drawContext.drawStackOverlay(textRenderer, stack, x, y, countOverride);
    }
}
