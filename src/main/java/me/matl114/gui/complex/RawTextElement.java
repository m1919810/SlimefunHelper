package me.matl114.gui.complex;

import java.util.List;
import lombok.experimental.Accessors;
import me.matl114.gui.basic.*;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;

@Accessors(chain = true)
public class RawTextElement extends AbstractElement {
    protected final TextProvider text;

    protected final ColorSampler color;
    private final boolean multiLine;

    public RawTextElement setAlignment(TextAlign alignment) {
        return new RawTextElement(TextProvider.withAlign(this.text, alignment), this.color, this.multiLine);
    }

    public static RawTextElement instance(Text text) {
        return new RawTextElement(text, Colors.WHITE);
    }

    public static RawTextElement instance(TextProvider text) {
        return new RawTextElement(text, Colors.WHITE);
    }

    public RawTextElement(Text text, int color) {
        this(TextProvider.of(text), color);
    }

    public RawTextElement(Text text, int color, int alignment) {
        this(TextProvider.of(text, TextAlign.fromLegacy(alignment)), color);
    }

    public RawTextElement(Text text, int color, TextAlign alignment) {
        this(TextProvider.of(text, alignment), color);
    }

    public RawTextElement(TextProvider provider, int color) {
        this(provider, ColorSampler.of(color));
    }

    public RawTextElement(TextProvider provider, int color, TextAlign alignment) {
        this(TextProvider.withAlign(provider, alignment), color);
    }

    public RawTextElement(TextProvider provider, ColorSampler color) {
        this(provider, color, false);
    }

    protected RawTextElement(TextProvider provider, ColorSampler color, boolean multiLine) {
        this.text = provider;
        this.color = color;
        this.multiLine = multiLine;
    }

    public Text getText(DrawableWidget element) {
        return text.getText(element);
    }

    public boolean hasText(DrawableWidget element) {
        return getText(element) != null;
    }

    public void renderText(DrawableWidget element, VDrawContext context, Text rawText) {
        if (rawText == null) {
            return;
        }
        if (multiLine) {
            renderMultiLine(element, context, rawText);
            return;
        }
        OrderedText text1 = rawText.asOrderedText();
        RenderHandler.drawScaledText0(
                context,
                mc.textRenderer,
                text1,
                0,
                0,
                element.getTextureWidth(),
                element.getTextureHeight(),
                color.getColorInt(),
                text.getAlign(element));
    }

    @Override
    public void renderCentered0(
            DrawableWidget element,
            VDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            float alpha,
            boolean shouldHighlight) {
        renderText(element, context, getText(element));
    }

    private void renderMultiLine(DrawableWidget element, VDrawContext context, Text rawText) {
        int width = element.getTextureWidth();
        List<OrderedText> lines = mc.textRenderer.wrapLines(rawText, width);
        int size = lines.size();
        if (size == 0) {
            return;
        }
        int lineHeight = 9;
        int estimated = lineHeight * size;
        int startY = 0;
        if (estimated < element.getTextureHeight()) {
            startY = (element.getTextureHeight() - estimated) / 2;
        } else {
            lineHeight = Math.min(lineHeight, element.getTextureHeight() / size);
        }
        for (int index = 0; index < size; index++) {
            RenderHandler.drawScaledText0(
                    context,
                    mc.textRenderer,
                    lines.get(index),
                    0,
                    index * lineHeight + startY,
                    element.getTextureWidth(),
                    (index + 1) * lineHeight + startY,
                    color.getColorInt(),
                    text.getAlign(element));
        }
    }
}
