package me.matl114.gui.basic;

import java.util.function.Predicate;
import java.util.function.Supplier;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

public interface RenderHandler {
    /**
     * the matrix stack of context is changed into element's coord, you can draw TEXTURE with coord 0,0 , they will be scaled and translated to the element's position
     * @param element
     * @param context
     * @param mouseX
     * @param mouseY
     * @param delta
     * @param alpha
     * @param shouldHighlight
     */
    public void renderAtCentered(
            DrawableWidget element,
            VDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            float alpha,
            boolean shouldHighlight);

    default boolean canBeSelected(DrawableWidget element) {
        return true;
    }

    /**
     * the matrix stack of context is poped here , you can draw tooltips or something without scaling by element
     * @param element
     * @param context
     * @param mouseX
     * @param mouseY
     * @param delta
     * @param alpha
     * @param shouldHighlight
     */
    default void renderExtraAbsoluteCoord(
            DrawableWidget element,
            VDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            float alpha,
            boolean shouldHighlight) {}

    default RenderHandler combineRender(RenderHandler handler) {
        return (element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
            RenderHandler.this.renderAtCentered(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
            handler.renderAtCentered(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
        };
    }

    default RenderHandler combineAbsoluteRender(RenderHandler handlerAbsolute) {
        return new RenderHandler() {
            @Override
            public void renderAtCentered(
                    DrawableWidget element,
                    VDrawContext context,
                    int mouseX,
                    int mouseY,
                    float delta,
                    float alpha,
                    boolean shouldHighlight) {
                RenderHandler.this.renderAtCentered(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
            }

            @Override
            public void renderExtraAbsoluteCoord(
                    DrawableWidget element,
                    VDrawContext context,
                    int mouseX,
                    int mouseY,
                    float delta,
                    float alpha,
                    boolean shouldHighlight) {
                handlerAbsolute.renderExtraAbsoluteCoord(
                        element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
            }
        };
    }

    default RenderHandler withRenderCondition(Predicate<RenderHandler> renderPredicate) {
        RenderHandler de = this;
        return new RenderHandler() {
            @Override
            public void renderAtCentered(
                    DrawableWidget element,
                    VDrawContext context,
                    int mouseX,
                    int mouseY,
                    float delta,
                    float alpha,
                    boolean shouldHighlight) {
                if (renderPredicate.test(de)) {
                    de.renderAtCentered(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
                }
            }

            @Override
            public void renderExtraAbsoluteCoord(
                    DrawableWidget element,
                    VDrawContext context,
                    int mouseX,
                    int mouseY,
                    float delta,
                    float alpha,
                    boolean shouldHighlight) {
                if (renderPredicate.test(de)) {
                    de.renderExtraAbsoluteCoord(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
                }
            }
        };
    }

    /**
     * auto shape TEXTURE uv part to element TEXTURE size
     * @param identifier
     * @param u0
     * @param v0
     * @param uheight
     * @param vheight
     * @return
     */
    public static RenderHandler ofMatchingElement(Identifier identifier, int u0, int v0, int uheight, int vheight) {
        float u1 = (float) u0 / 256f;
        float v1 = (float) v0 / 256f;
        float u2 = (float) (u0 + uheight) / 256f;
        float v2 = (float) (v0 + vheight) / 256f;
        return new RenderHandler() {
            @Override
            public void renderAtCentered(
                    DrawableWidget element,
                    VDrawContext context,
                    int mouseX,
                    int mouseY,
                    float delta,
                    float alpha,
                    boolean shouldHighlight) {
                context.setShaderAlpha(alpha);
                context.drawTexturedQuad(
                        identifier, 0, element.getTextureWidth(), 0, element.getTextureHeight(), 0, u1, u2, v1, v2);
                context.setShaderAlpha(1.0F);
            }
        };
    }

    public static RenderHandler ofMatchingElement(Identifier identifier) {
        return ofMatchingElement(identifier, 0, 0, 256, 256);
    }

    public static RenderHandler ofElementTextureSize(Identifier identifier) {
        return ofElementTextureSize(identifier, 1.0f);
    }

    /**
     * scale the picture with scaler( which multiply pict wxh->scaler *w x scaler *h and cut from the element size after scale
     * @param identifier
     * @param scaler
     * @return
     */
    public static RenderHandler ofElementTextureSize(Identifier identifier, float scaler) {
        final float scaler256 = scaler * 256;
        return new RenderHandler() {
            @Override
            public void renderAtCentered(
                    DrawableWidget element,
                    VDrawContext context,
                    int mouseX,
                    int mouseY,
                    float delta,
                    float alpha,
                    boolean shouldHighlight) {
                context.setShaderAlpha(alpha);
                context.drawTexturedQuad(
                        identifier,
                        0,
                        element.getTextureWidth(),
                        0,
                        element.getTextureHeight(),
                        0,
                        0,
                        ((float) element.getTextureWidth()) / scaler256,
                        0,
                        ((float) element.getTextureHeight()) / scaler256);
                context.setShaderAlpha(1.0F);
            }
        };
    }

    public static RenderHandler ofResource(Identifier identifier) {
        return ofResource(identifier, 1.0f);
    }

    public static RenderHandler ofResource(Identifier identifier, int u0, int v0, int uheight, int vheight) {
        return ofResource(identifier, u0, v0, uheight, vheight, 1.0f);
    }

    public static RenderHandler ofResource(Identifier identifier, float scaler) {
        return ofResource(identifier, 0, 0, 256, 256, scaler);
    }

    public static RenderHandler ofResource(
            Identifier identifier, int u0, int v0, int uheight, int vheight, float scaler) {
        float u1 = (float) u0 / 256f;
        float v1 = (float) v0 / 256f;
        float u2 = (float) (u0 + uheight) / 256f;
        float v2 = (float) (v0 + vheight) / 256f;
        int dx = (int) ((float) uheight * scaler);
        int dy = (int) ((float) vheight * scaler);
        return new RenderHandler() {
            @Override
            public void renderAtCentered(
                    DrawableWidget element,
                    VDrawContext context,
                    int mouseX,
                    int mouseY,
                    float delta,
                    float alpha,
                    boolean shouldHighlight) {
                context.setShaderAlpha(alpha);
                context.drawTexturedQuad(identifier, 0, dx, 0, dy, 0, u1, u2, v1, v2);
                context.setShaderAlpha(1.0F);
            }
        };
    }

    public static RenderHandler ofPositionResource(Identifier identifier, int x, int y, int xheight, int yheight) {
        return ofPositionResource(identifier, x, y, xheight, yheight, 0, 0, 256, 256);
    }

    public static RenderHandler ofPositionResource(
            Identifier identifier, int x, int y, int xheight, int yheight, int u0, int v0, int uheight, int vheight) {
        float u1 = (float) u0 / 256f;
        float v1 = (float) v0 / 256f;
        float u2 = (float) (u0 + uheight) / 256f;
        float v2 = (float) (v0 + vheight) / 256f;
        int x2 = x + xheight;
        int y2 = y + yheight;
        return new RenderHandler() {
            @Override
            public void renderAtCentered(
                    DrawableWidget element,
                    VDrawContext context,
                    int mouseX,
                    int mouseY,
                    float delta,
                    float alpha,
                    boolean shouldHighlight) {
                context.setShaderAlpha(alpha);
                context.drawTexturedQuad(identifier, x, x2, y, y2, 0, u1, u2, v1, v2);
                context.setShaderAlpha(1.0F);
            }
        };
    }

    public static RenderHandler ofGuiTextures(Identifier guiTexture, int startX, int startY, int sizeX, int sizeY) {
        return (element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
            context.drawGuiTexture(guiTexture, startX, startY, sizeX, sizeY);
        };
    }

    public static RenderHandler ofColorQuad(int color) {
        return ((element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
            context.fillGuiGradient(0, 0, element.getTextureWidth(), element.getTextureHeight(), color, color, 0);
        });
    }

    public static MinecraftClient mc = MinecraftClient.getInstance();

    public static RenderHandler ofScrollableText(Text text, int color) {
        final int color1 = color;
        return (element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
            drawScrollableText(
                    context,
                    mc.textRenderer,
                    text,
                    0,
                    0,
                    element.getTextureWidth(),
                    element.getTextureHeight(),
                    color1);
        };
    }

    public static RenderHandler ofAutoScaleText(Text text, int color) {
        return (element, context, mouseX, mouseY, delta, alpha, shouldHighlight) -> {
            RenderHandler.drawScaledText0(
                    context,
                    mc.textRenderer,
                    text,
                    0,
                    0,
                    element.getTextureWidth(),
                    element.getTextureHeight(),
                    color,
                    0);
        };
    }

    public static void drawScrollableText(
            VDrawContext context,
            TextRenderer textRenderer,
            Text text,
            int startX,
            int startY,
            int endX,
            int endY,
            int color) {
        drawScrollableText0(context, textRenderer, text, (startX + endX) / 2, startX, startY, endX, endY, color);
    }

    public static void drawScrollableText0(
            VDrawContext context,
            TextRenderer textRenderer,
            Text text,
            int centerX,
            int startX,
            int startY,
            int endX,
            int endY,
            int color) {
        drawScrollableText0(context, textRenderer, text.asOrderedText(), centerX, startX, startY, endX, endY, color);
    }

    public static void drawScrollableText0(
            VDrawContext context,
            TextRenderer textRenderer,
            OrderedText text,
            int centerX,
            int startX,
            int startY,
            int endX,
            int endY,
            int color) {
        int i = textRenderer.getWidth(text);
        int var10000 = startY + endY;
        int j = (var10000 - 9) / 2 + 1;
        int k = endX - startX;
        int l;
        if (i > k) {
            l = i - k;
            double d = (double) Util.getMeasuringTimeMs() / 1000.0;
            double e = Math.max((double) l * 0.5, 3.0);
            double f = Math.sin(1.5707963267948966 * Math.cos(6.283185307179586 * d / e)) / 2.0 + 0.5;
            double g = MathHelper.lerp(f, 0.0, (double) l);
            context.enableScissor(startX, startY, endX, endY);
            context.drawText(textRenderer, text, startX - (int) g, j, color, true);
            context.disableScissor();
        } else {
            l = MathHelper.clamp(centerX, startX + i / 2, endX - i / 2);
            context.drawCenteredTextWithShadow(textRenderer, text, l, j, color);
        }
    }

    public static void drawScaledText0(
            VDrawContext context,
            TextRenderer textRenderer,
            Text text,
            int startX,
            int startY,
            int endX,
            int endY,
            int color,
            int alignment) {
        drawScaledText0(context, textRenderer, text.asOrderedText(), startX, startY, endX, endY, color, alignment);
    }

    public static void drawScaledText0(
            VDrawContext context,
            TextRenderer textRenderer,
            OrderedText text,
            int startX,
            int startY,
            int endX,
            int endY,
            int color,
            int alignment) {
        float i = textRenderer.getTextHandler().getWidth(text);
        int availableWidth = endX - startX;
        // 居中位置
        float j = (startY + endY - 9) / 2.0F;
        if (availableWidth > i) {
            float l =
                    switch (alignment) {
                        case -1 -> startX;

                        case 1 -> endX - i;
                            // magic, I dont know
                        default -> (float) (startX + endX - i + 1) / 2.0F;
                    };
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(l, j);
            context.drawText(textRenderer, text, 0, 0, color, true);
            context.getMatrices().popMatrix();
        } else {
            float scale = ((float) availableWidth) / (float) i;
            float scaledHeight = 9.0F * scale;
            float newJ = (startY + endY - scaledHeight) / 2.0F;
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(startX, newJ);
            context.getMatrices().scale(scale, scale);
            context.drawText(textRenderer, text, 0, 0, color, true);
            context.getMatrices().popMatrix();
        }
    }

    public static RenderHandler ofSingleItem(Supplier<ItemStack> item, int x, int y, boolean inSlot) {
        return new RenderHandler() {
            @Override
            public void renderAtCentered(
                    DrawableWidget element,
                    VDrawContext context,
                    int mouseX,
                    int mouseY,
                    float delta,
                    float alpha,
                    boolean shouldHighlight) {
                drawSingleItem(context, item.get(), x, y, inSlot);
            }
        };
    }

    public static void drawSingleItem(VDrawContext context, ItemStack stack, int x, int y, boolean inSlot) {
        if (!stack.isEmpty()) {
            context.getMatrices().pushMatrix();
            context.drawItem(stack, x, y, 114514, 0);
            if (inSlot) {
                context.drawItemInSlot(mc.textRenderer, stack, x, y, null);
            }
            context.getMatrices().popMatrix();
        }
    }

    public static void drawHighlightFrame(VDrawContext context, int x, int y, int dx, int dy, int color) {
        context.fillGuiGradient(x, y, x + dx, y + 1, color, color, 0);
        context.fillGuiGradient(x, y, x + 1, y + dy, color, color, 0);
        context.fillGuiGradient(x + dx - 1, y + 1, x + dx, y + dy, color, color, 0);
        context.fillGuiGradient(x + 1, y + dy - 1, x + dx, y + dy, color, color, 0);
    }

    public static void drawHighLightBox(VDrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
    }
}
