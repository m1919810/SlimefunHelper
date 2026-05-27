package me.matl114.gui.elements;

import me.matl114.gui.basic.*;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.OrderedText;

public class ColorSplitterElement extends ColorLabelTextElement {

    public ColorSplitterElement(TextProvider text, int color, ColorSampler background) {
        super(text, color, background);
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
        context.fill(0, 0, element.getTextureWidth(), element.getTextureHeight(), 0, backgroundColor.getColorInt());
        OrderedText text1 = text.getLabel(element);
        float width = 0;
        int textColor = color.getColorInt();
        if (text1 != null) {
            width = mc.textRenderer.getTextHandler().getWidth(text1);
            RenderHandler.drawScaledText0(
                    context,
                    mc.textRenderer,
                    text1,
                    0,
                    0,
                    element.getTextureWidth(),
                    element.getTextureHeight(),
                    textColor,
                    alignment);
        }
        float startHeight = (element.getTextureHeight() - 1) / 2.0f;
        // add
        context.pushMatrix();
        context.getMatrices().translate(0, startHeight);
        if (width == 0) {
            context.fill(0, 0, element.getTextureWidth(), 1, 0, textColor);
        } else {
            int textureWidth = element.getTextureWidth();
            int startWidth = (int) ((textureWidth - width - 2) / 2);
            if (startWidth > 0) {
                context.fill(0, 0, startWidth, 1, 0, textColor);
                context.fill(textureWidth - startWidth, 0, textureWidth, 1, 0, textColor);
            }
        }
        context.popMatrix();
    }
}
