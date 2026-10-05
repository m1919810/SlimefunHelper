package me.matl114.renderer.text;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer.GlyphDrawable;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.joml.Matrix4f;

public class VanillaTextDrawer implements TextDrawer {
    protected final net.minecraft.client.font.TextRenderer delegate;

    public VanillaTextDrawer() {
        this(MinecraftClient.getInstance().textRenderer);
    }

    public VanillaTextDrawer(net.minecraft.client.font.TextRenderer delegate) {
        this.delegate = delegate;
    }

    @Override
    public net.minecraft.client.font.TextRenderer unwrap() {
        return delegate;
    }

    @Override
    public int fontHeight() {
        return delegate.fontHeight;
    }

    @Override
    public String mirror(String text) {
        return delegate.mirror(text);
    }

    @Override
    public boolean isRightToLeft() {
        return delegate.isRightToLeft();
    }

    @Override
    public int getWidth(String text) {
        return text == null ? 0 : delegate.getWidth(text);
    }

    @Override
    public int getWidth(StringVisitable text) {
        return delegate.getWidth(text);
    }

    @Override
    public int getWidth(OrderedText text) {
        return delegate.getWidth(text);
    }

    @Override
    public String trimToWidth(String text, int maxWidth, boolean backwards) {
        return delegate.trimToWidth(text, maxWidth, backwards);
    }

    @Override
    public String trimToWidth(String text, int maxWidth) {
        return delegate.trimToWidth(text, maxWidth);
    }

    @Override
    public StringVisitable trimToWidth(StringVisitable text, int width) {
        return delegate.trimToWidth(text, width);
    }

    @Override
    public GlyphDrawable prepare(String text, float x, float y, int color, boolean shadow, int backgroundColor) {
        return delegate.prepare(text, x, y, color, shadow, backgroundColor);
    }

    @Override
    public GlyphDrawable prepare(
            OrderedText text, float x, float y, int color, boolean shadow, boolean trackEmpty, int backgroundColor) {
        return delegate.prepare(text, x, y, color, shadow, trackEmpty, backgroundColor);
    }

    @Override
    public void draw(
            String text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f matrix,
            VertexConsumerProvider vertexConsumers,
            TextLayerType layerType,
            int backgroundColor,
            int light) {
        delegate.draw(text, x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light);
    }

    @Override
    public void draw(
            net.minecraft.text.Text text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f matrix,
            VertexConsumerProvider vertexConsumers,
            TextLayerType layerType,
            int backgroundColor,
            int light) {
        delegate.draw(text, x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light);
    }

    @Override
    public void draw(
            OrderedText text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f matrix,
            VertexConsumerProvider vertexConsumers,
            TextLayerType layerType,
            int backgroundColor,
            int light) {
        delegate.draw(text, x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light);
    }

    @Override
    public void drawWithOutline(
            OrderedText text,
            float x,
            float y,
            int color,
            int outlineColor,
            Matrix4f matrix,
            VertexConsumerProvider vertexConsumers,
            int light) {
        delegate.drawWithOutline(text, x, y, color, outlineColor, matrix, vertexConsumers, light);
    }

    @Override
    public float getWrappedLinesHeight(StringVisitable text, int maxWidth) {
        return delegate.getWrappedLinesHeight(text, maxWidth);
    }

    @Override
    public List<OrderedText> wrapLines(StringVisitable text, int width) {
        return delegate.wrapLines(text, width);
    }

    @Override
    public List<StringVisitable> wrapLinesWithoutLanguage(StringVisitable text, int width) {
        return delegate.wrapLinesWithoutLanguage(text, width);
    }

    @Override
    public TextHandler getTextHandler() {
        return delegate.getTextHandler();
    }
}
