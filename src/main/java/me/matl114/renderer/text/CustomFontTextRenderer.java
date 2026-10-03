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
import net.minecraft.text.TextVisitFactory;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.joml.Matrix4f;

public final class CustomFontTextRenderer extends VanillaTextRenderer {
    public static final Identifier UNICODE_FONT_ID = Identifier.of("slimefunhelper", "unicode");
    private static final Style UNICODE_STYLE = Style.EMPTY.withFont(new StyleSpriteSource.Font(UNICODE_FONT_ID));

    public CustomFontTextRenderer() {
        this(MinecraftClient.getInstance().textRenderer);
    }

    public CustomFontTextRenderer(net.minecraft.client.font.TextRenderer delegate) {
        super(delegate);
    }

    @Override
    public int getWidth(String text) {
        if (text == null) {
            return 0;
        }
        return (int) Math.ceil(delegate.getTextHandler().getWidth(formattedString(text, false)));
    }

    @Override
    public int getWidth(StringVisitable text) {
        return (int) Math.ceil(delegate.getTextHandler().getWidth(withUnicodeFont(text)));
    }

    @Override
    public int getWidth(OrderedText text) {
        return (int) Math.ceil(delegate.getTextHandler().getWidth(withUnicodeFont(text)));
    }

    @Override
    public String trimToWidth(String text, int maxWidth, boolean backwards) {
        return backwards
            ? delegate.getTextHandler().trimToWidthBackwards(text, maxWidth, UNICODE_STYLE)
            : delegate.getTextHandler().trimToWidth(text, maxWidth, UNICODE_STYLE);
    }

    @Override
    public String trimToWidth(String text, int maxWidth) {
        return delegate.getTextHandler().trimToWidth(text, maxWidth, UNICODE_STYLE);
    }

    @Override
    public StringVisitable trimToWidth(StringVisitable text, int width) {
        return delegate.getTextHandler().trimToWidth(withUnicodeFont(text), width, Style.EMPTY);
    }

    @Override
    public GlyphDrawable prepare(String text, float x, float y, int color, boolean shadow, int backgroundColor) {
        return delegate.prepare(formattedString(text, true), x, y, color, shadow, false, backgroundColor);
    }

    @Override
    public GlyphDrawable prepare(OrderedText text, float x, float y, int color, boolean shadow, boolean trackEmpty, int backgroundColor) {
        return delegate.prepare(withUnicodeFont(text), x, y, color, shadow, trackEmpty, backgroundColor);
    }

    @Override
    public void draw(String text, float x, float y, int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumers, TextLayerType layerType, int backgroundColor, int light) {
        if (text == null) {
            return;
        }
        delegate.draw(formattedString(text, true), x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light);
    }

    @Override
    public void draw(net.minecraft.text.Text text, float x, float y, int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumers, TextLayerType layerType, int backgroundColor, int light) {
        delegate.draw(withUnicodeFont(text.asOrderedText()), x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light);
    }

    @Override
    public void draw(OrderedText text, float x, float y, int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumers, TextLayerType layerType, int backgroundColor, int light) {
        delegate.draw(withUnicodeFont(text), x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light);
    }

    @Override
    public void drawWithOutline(OrderedText text, float x, float y, int color, int outlineColor, Matrix4f matrix, VertexConsumerProvider vertexConsumers, int light) {
        delegate.drawWithOutline(withUnicodeFont(text), x, y, color, outlineColor, matrix, vertexConsumers, light);
    }

    @Override
    public int getWrappedLinesHeight(StringVisitable text, int maxWidth) {
        return fontHeight() * wrapLines(text, maxWidth).size();
    }

    @Override
    public List<OrderedText> wrapLines(StringVisitable text, int width) {
        return Language.getInstance().reorder(delegate.getTextHandler().wrapLines(withUnicodeFont(text), width, Style.EMPTY));
    }

    @Override
    public List<StringVisitable> wrapLinesWithoutLanguage(StringVisitable text, int width) {
        return delegate.getTextHandler().wrapLines(withUnicodeFont(text), width, Style.EMPTY);
    }

    @Override
    public TextHandler getTextHandler() {
        return new TextHandler((codePoint, style) -> {
            return delegate.getTextHandler().getWidth(OrderedText.styled(codePoint, withUnicodeFont(style)));
        });
    }

    private static OrderedText withUnicodeFont(OrderedText text) {
        return visitor -> text.accept((index, style, codePoint) -> visitor.accept(index, withUnicodeFont(style), codePoint));
    }

    private OrderedText formattedString(String text, boolean mirror) {
        String renderedText = mirror && delegate.isRightToLeft() ? delegate.mirror(text) : text;
        List<FormattedCharacter> characters = new ArrayList<>();
        TextVisitFactory.visitFormatted(renderedText, UNICODE_STYLE, (index, style, codePoint) -> {
            characters.add(new FormattedCharacter(index, withUnicodeFont(style), codePoint));
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

    private static StringVisitable withUnicodeFont(StringVisitable text) {
        List<StringVisitable> parts = new ArrayList<>();
        text.visit((style, string) -> {
            parts.add(StringVisitable.styled(string, withUnicodeFont(style)));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return StringVisitable.concat(parts);
    }

    private static Style withUnicodeFont(Style style) {
        return style.getFont() instanceof StyleSpriteSource.Font
            ? style.withFont(new StyleSpriteSource.Font(UNICODE_FONT_ID))
            : style;
    }

    private record FormattedCharacter(int index, Style style, int codePoint) {
    }
}
