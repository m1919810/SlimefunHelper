package me.matl114.gui.elements;

import java.util.List;
import lombok.experimental.Accessors;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.RenderHandler;
import me.matl114.gui.basic.TextAlign;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.complex.RawTextElement;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

@Accessors(chain = true)
public class MultiLineTextElement extends RawTextElement {
    public MultiLineTextElement(Text text, int color) {
        super(TextProvider.of(text), color);
    }

    public MultiLineTextElement(Text text, int color, TextAlign alignment) {
        this(TextProvider.of(text, alignment), color);
    }

    public MultiLineTextElement(TextProvider provider, int color) {
        super(provider, color);
    }

    public MultiLineTextElement(TextProvider provider, int color, TextAlign alignment) {
        super(provider, color, alignment);
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
        Text multiLine = text.getText(element);
        if (multiLine != null) {
            int width = element.getTextureWidth();
            List<OrderedText> lines = mc.textRenderer.wrapLines(multiLine, width);
            int size = lines.size();
            if (size > 0) {
                int lineHeight = 9;
                int estimated = lineHeight * size;
                int startY = 0;
                if (estimated < element.getTextureHeight()) {
                    startY = (element.getTextureHeight() - estimated) / 2;
                } else {
                    lineHeight = Math.min(lineHeight, element.getTextureHeight() / size);
                }
                for (int i = 0; i < size; i++) {
                    RenderHandler.drawScaledText0(
                            context,
                            mc.textRenderer,
                            lines.get(i),
                            0,
                            i * lineHeight + startY,
                            element.getTextureWidth(),
                            (i + 1) * lineHeight + startY,
                            color.getColorInt(),
                            text.getAlign(element));
                }
            }
        }
    }
}
