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
    protected TextProvider text;

    protected ColorSampler color;
    private boolean multiLine;
    private boolean autoRescale = true;

    public RawTextElement setAlignment(TextAlign alignment) {
        this.text = TextProvider.withAlign(this.text, alignment);
        return this;
    }

    public RawTextElement setAutoRescale(boolean autoRescale) {
        this.autoRescale = autoRescale;
        return this;
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
        float fontHeight = context.getTextDrawer().getTextHeight();
        float scale = fontHeight > 0 ? Math.max(1, fontHeight) / fontHeight : 1;
        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().scale(scale, scale);
            renderText(
                    element,
                    context,
                    rawText,
                    Math.round(element.getTextureWidth() / scale),
                    Math.round(element.getTextureHeight() / scale));
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    private void renderText(DrawableWidget element, VDrawContext context, Text rawText, int width, int height) {
        if (multiLine || rawText.getString().indexOf('\n') >= 0) {
            renderMultiLine(element, context, rawText, width, height);
            return;
        }
        OrderedText text1 = rawText.asOrderedText();
        if (!autoRescale) {
            RenderHandler.drawScrollingText(
                    context,
                    context.getTextDrawer(),
                    text1,
                    0,
                    0,
                    width,
                    height,
                    color.getColorInt(),
                    text.getAlign(element));
            return;
        } else {
            RenderHandler.drawScaledText0(
                    context,
                    context.getTextDrawer(),
                    text1,
                    0,
                    0,
                    width,
                    height,
                    color.getColorInt(),
                    text.getAlign(element));
        }
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

    private void renderMultiLine(DrawableWidget element, VDrawContext context, Text rawText, int width, int height) {
        var textDrawer = context.getTextDrawer();
        List<OrderedText> lines = textDrawer.wrapLines(rawText, width);
        int size = lines.size();
        if (size == 0) {
            return;
        }
        float lineHeight = textDrawer.getTextHeight();
        float estimated = lineHeight * size;
        float startY = 0;
        if (estimated < height) {
            startY = (height - estimated) / 2;
        } else {
            lineHeight = Math.min(lineHeight, height / size);
        }
        for (int index = 0; index < size; index++) {
            RenderHandler.drawScaledText0(
                    context,
                    textDrawer,
                    lines.get(index),
                    0,
                    index * lineHeight + startY,
                    width,
                    (index + 1) * lineHeight + startY,
                    color.getColorInt(),
                    text.getAlign(element));
        }
    }
}
