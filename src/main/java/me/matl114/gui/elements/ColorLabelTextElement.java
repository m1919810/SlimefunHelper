package me.matl114.gui.elements;

import me.matl114.gui.basic.*;
import me.matl114.gui.complex.RawTextElement;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.OrderedText;

public class ColorLabelTextElement extends RawTextElement {
    ColorSampler backgroundColor;

    public ColorLabelTextElement(TextProvider text, int color, ColorSampler background) {
        super(text, color, 0);
        backgroundColor = background;
    }

    public ColorLabelTextElement(TextProvider text, ColorSampler color, ColorSampler background) {
        super(text, color, 0);
        backgroundColor = background;
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
        if (text1 != null) {

            RenderHandler.drawScaledText0(
                    context,
                    mc.textRenderer,
                    text1,
                    0,
                    0,
                    element.getTextureWidth(),
                    element.getTextureHeight(),
                    color.getColorInt(),
                    alignment);
        }
    }
}
