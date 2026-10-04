package me.matl114.gui.elements;

import me.matl114.gui.basic.AbstractElement;
import me.matl114.gui.basic.ColorSampler;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.versioned.api.VDrawContext;

public class ColorLabelElement extends AbstractElement {
    private final ColorSampler backgroundColor;

    public ColorLabelElement(ColorSampler backgroundColor) {
        this.backgroundColor = backgroundColor;
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
    }
}
