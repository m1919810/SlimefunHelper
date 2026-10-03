package me.matl114.gui.elements;

import me.matl114.gui.basic.AbstractElement;
import me.matl114.gui.basic.ColorSampler;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.TextProvider;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.OrderedText;

public class SplitterTextElement extends AbstractElement {
    private final TextProvider text;
    private final ColorSampler color;

    public SplitterTextElement(TextProvider text, ColorSampler color) {
        this.text = text;
        this.color = color;
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
        OrderedText label = text.getLabel(element);
        int textWidth = label == null ? 0 : (int) mc.textRenderer.getTextHandler().getWidth(label);
        int width = element.getTextureWidth();
        int sideWidth = Math.max(0, (width - textWidth - 2) / 2);
        int lineColor = color.getColorInt();
        float y = (element.getTextureHeight() - 1) / 2.0f;
        context.pushMatrix();
        context.getMatrices().translate(0, y);
        if (textWidth == 0) {
            context.fill(0, 0, width, 1, 0, lineColor);
        } else if (sideWidth > 0) {
            context.fill(0, 0, sideWidth, 1, 0, lineColor);
            context.fill(width - sideWidth, 0, width, 1, 0, lineColor);
        }
        context.popMatrix();
    }
}
