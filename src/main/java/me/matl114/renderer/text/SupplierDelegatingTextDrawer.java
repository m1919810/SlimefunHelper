package me.matl114.renderer.text;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer.GlyphDrawable;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

public final class SupplierDelegatingTextDrawer implements TextDrawer {
    private final Supplier<? extends TextDrawer> supplier;

    public SupplierDelegatingTextDrawer(Supplier<? extends TextDrawer> supplier) {
        this.supplier = supplier;
    }

    public TextDrawer getDelegate() {
        return supplier.get();
    }

    @Override
    public net.minecraft.client.font.TextRenderer unwrap() {
        return getDelegate().unwrap();
    }

    @Override
    public int fontHeight() {
        return getDelegate().fontHeight();
    }

    @Override
    public float getTextHeight() {
        return getDelegate().getTextHeight();
    }

    @Override
    public String mirror(String text) {
        return getDelegate().mirror(text);
    }

    @Override
    public boolean isRightToLeft() {
        return getDelegate().isRightToLeft();
    }

    @Override
    public int getWidth(String text) {
        return getDelegate().getWidth(text);
    }

    @Override
    public int getWidth(StringVisitable text) {
        return getDelegate().getWidth(text);
    }

    @Override
    public int getWidth(OrderedText text) {
        return getDelegate().getWidth(text);
    }

    @Override
    public int getWidth(Text text) {
        return getDelegate().getWidth(text);
    }

    @Override
    public String trimToWidth(String text, int maxWidth, boolean backwards) {
        return getDelegate().trimToWidth(text, maxWidth, backwards);
    }

    @Override
    public String trimToWidth(String text, int maxWidth) {
        return getDelegate().trimToWidth(text, maxWidth);
    }

    @Override
    public StringVisitable trimToWidth(StringVisitable text, int width) {
        return getDelegate().trimToWidth(text, width);
    }

    @Override
    public GlyphDrawable prepare(String text, float x, float y, int color, boolean shadow, int backgroundColor) {
        return getDelegate().prepare(text, x, y, color, shadow, backgroundColor);
    }

    @Override
    public GlyphDrawable prepare(
            OrderedText text, float x, float y, int color, boolean shadow, boolean trackEmpty, int backgroundColor) {
        return getDelegate().prepare(text, x, y, color, shadow, trackEmpty, backgroundColor);
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
        getDelegate().draw(text, x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light);
    }

    @Override
    public void draw(
            Text text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f matrix,
            VertexConsumerProvider vertexConsumers,
            TextLayerType layerType,
            int backgroundColor,
            int light) {
        getDelegate().draw(text, x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light);
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
        getDelegate().draw(text, x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light);
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
        getDelegate().drawWithOutline(text, x, y, color, outlineColor, matrix, vertexConsumers, light);
    }

    @Override
    public float getWrappedLinesHeight(StringVisitable text, int maxWidth) {
        return getDelegate().getWrappedLinesHeight(text, maxWidth);
    }

    @Override
    public List<OrderedText> wrapLines(StringVisitable text, int width) {
        return getDelegate().wrapLines(text, width);
    }

    @Override
    public List<StringVisitable> wrapLinesWithoutLanguage(StringVisitable text, int width) {
        return getDelegate().wrapLinesWithoutLanguage(text, width);
    }

    @Override
    public TextHandler getTextHandler() {
        return getDelegate().getTextHandler();
    }
}
