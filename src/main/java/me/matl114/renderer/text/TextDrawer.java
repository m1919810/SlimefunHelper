package me.matl114.renderer.text;

import java.util.List;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

public interface TextDrawer {
    int fontHeight();

    default net.minecraft.client.font.TextRenderer unwrap() {
        return null;
    }

    default float getTextHeight() {
        return fontHeight();
    }

    String mirror(String text);

    boolean isRightToLeft();

    int getWidth(String text);

    int getWidth(StringVisitable text);

    int getWidth(OrderedText text);

    default int getWidth(Text text) {
        return getWidth(text.asOrderedText());
    }

    String trimToWidth(String text, int maxWidth, boolean backwards);

    String trimToWidth(String text, int maxWidth);

    StringVisitable trimToWidth(StringVisitable text, int width);

    void draw(
            String text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f matrix,
            VertexConsumerProvider vertexConsumers,
            TextLayerType layerType,
            int backgroundColor,
            int light);

    void draw(
            Text text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f matrix,
            VertexConsumerProvider vertexConsumers,
            TextLayerType layerType,
            int backgroundColor,
            int light);

    void draw(
            OrderedText text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f matrix,
            VertexConsumerProvider vertexConsumers,
            TextLayerType layerType,
            int backgroundColor,
            int light);

    void drawWithOutline(
            OrderedText text,
            float x,
            float y,
            int color,
            int outlineColor,
            Matrix4f matrix,
            VertexConsumerProvider vertexConsumers,
            int light);

    default void draw(
            OrderedText text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f matrix,
            TextLayerType layerType,
            int backgroundColor,
            int light) {
        try (BufferAllocator allocator = new BufferAllocator(4096)) {
            VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
            draw(text, x, y, color, shadow, matrix, immediate, layerType, backgroundColor, light);
            immediate.draw();
        }
    }

    default void draw(
            String text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f matrix,
            TextLayerType layerType,
            int backgroundColor,
            int light) {
        if (text == null) return;
        try (BufferAllocator allocator = new BufferAllocator(4096)) {
            VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
            draw(text, x, y, color, shadow, matrix, immediate, layerType, backgroundColor, light);
            immediate.draw();
        }
    }

    default void draw(
            Text text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f matrix,
            TextLayerType layerType,
            int backgroundColor,
            int light) {
        draw(text.asOrderedText(), x, y, color, shadow, matrix, layerType, backgroundColor, light);
    }

    float getWrappedLinesHeight(StringVisitable text, int maxWidth);

    List<OrderedText> wrapLines(StringVisitable text, int width);

    List<StringVisitable> wrapLinesWithoutLanguage(StringVisitable text, int width);

    TextHandler getTextHandler();
}
