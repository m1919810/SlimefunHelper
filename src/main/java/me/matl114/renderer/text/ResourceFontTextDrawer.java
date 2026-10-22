package me.matl114.renderer.text;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer.GlyphDrawable;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.TextVisitFactory;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.joml.Matrix4f;

public class ResourceFontTextDrawer extends VanillaTextDrawer {
    private final Identifier fontId;
    private final Style fontStyle;

    public ResourceFontTextDrawer(Identifier fontId) {
        this(MinecraftClient.getInstance().textRenderer, fontId);
    }

    public ResourceFontTextDrawer(net.minecraft.client.font.TextRenderer delegate, Identifier fontId) {
        super(delegate);
        this.fontId = fontId;
        this.fontStyle = Style.EMPTY.withFont(new StyleSpriteSource.Font(fontId));
    }

    public Identifier fontId() {
        return fontId;
    }

    @Override
    public float getTextHeight() {
        return fontHeight();
    }

    public float getTextYOffset() {
        return getTextHeight() - fontHeight();
    }

    @Override
    public int getWidth(String text) {
        return text == null ? 0 : (int) Math.ceil(delegate.getTextHandler().getWidth(formattedString(text, false)));
    }

    @Override
    public int getWidth(StringVisitable text) {
        return (int) Math.ceil(delegate.getTextHandler().getWidth(withFont(text)));
    }

    @Override
    public int getWidth(OrderedText text) {
        return (int) Math.ceil(delegate.getTextHandler().getWidth(withFont(text)));
    }

    @Override
    public String trimToWidth(String text, int maxWidth, boolean backwards) {
        return backwards
                ? delegate.getTextHandler().trimToWidthBackwards(text, maxWidth, fontStyle)
                : delegate.getTextHandler().trimToWidth(text, maxWidth, fontStyle);
    }

    @Override
    public String trimToWidth(String text, int maxWidth) {
        return delegate.getTextHandler().trimToWidth(text, maxWidth, fontStyle);
    }

    @Override
    public StringVisitable trimToWidth(StringVisitable text, int width) {
        return delegate.getTextHandler().trimToWidth(withFont(text), width, fontStyle);
    }

    @Override
    public GlyphDrawable prepare(String text, float x, float y, int color, boolean shadow, int backgroundColor) {
        if (text == null) {
            return null;
        }
        return delegate.prepare(formattedString(text, true), x, y, color, shadow, false, backgroundColor);
    }

    @Override
    public GlyphDrawable prepare(
            OrderedText text, float x, float y, int color, boolean shadow, boolean trackEmpty, int backgroundColor) {
        return delegate.prepare(withFont(text), x, y, color, shadow, trackEmpty, backgroundColor);
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
        if (text != null) {
            delegate.draw(
                    formattedString(text, true),
                    x,
                    y,
                    color,
                    shadow,
                    matrix,
                    vertexConsumers,
                    layerType,
                    backgroundColor,
                    light);
        }
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
        delegate.draw(
                withFont(text.copy().setStyle(fontStyle).asOrderedText()),
                x,
                y,
                color,
                shadow,
                matrix,
                vertexConsumers,
                layerType,
                backgroundColor,
                light);
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
        delegate.draw(withFont(text), x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light);
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
        delegate.drawWithOutline(withFont(text), x, y, color, outlineColor, matrix, vertexConsumers, light);
    }

    @Override
    public float getWrappedLinesHeight(StringVisitable text, int maxWidth) {
        return getTextHeight() * wrapLines(text, maxWidth).size();
    }

    @Override
    public List<OrderedText> wrapLines(StringVisitable text, int width) {
        return Language.getInstance().reorder(delegate.getTextHandler().wrapLines(withFont(text), width, fontStyle));
    }

    @Override
    public TextHandler getTextHandler() {
        return new TextHandler((codePoint, style) ->
                delegate.getTextHandler().getWidth(OrderedText.styled(codePoint, withFont(style))));
    }

    public OrderedText withFont(OrderedText text) {
        return visitor -> text.accept((index, style, codePoint) -> visitor.accept(index, withFont(style), codePoint));
    }

    public OrderedText formattedString(String text, boolean mirror) {
        String renderedText = mirror && delegate.isRightToLeft() ? delegate.mirror(text) : text;
        List<FormattedCharacter> characters = new ArrayList<>();
        TextVisitFactory.visitFormatted(renderedText, fontStyle, (index, style, codePoint) -> {
            characters.add(new FormattedCharacter(index, withFont(style), codePoint));
            return true;
        });
        return visitor -> {
            for (FormattedCharacter character : characters) {
                if (!visitor.accept(character.index(), character.style(), character.codePoint())) {
                    return false;
                }
            }
            return true;
        };
    }

    private StringVisitable withFont(StringVisitable text) {
        List<StringVisitable> parts = new ArrayList<>();
        text.visit(
                (style, string) -> {
                    parts.add(StringVisitable.styled(string, withFont(style)));
                    return java.util.Optional.empty();
                },
                fontStyle);
        return StringVisitable.concat(parts);
    }

    private Style withFont(Style style) {
        return style.withFont(new StyleSpriteSource.Font(fontId));
    }

    private record FormattedCharacter(int index, Style style, int codePoint) {}
}
