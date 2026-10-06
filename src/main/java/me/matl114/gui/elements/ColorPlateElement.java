package me.matl114.gui.elements;

import me.matl114.gui.basic.AbstractElement;
import me.matl114.gui.basic.ColorSampler;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.ExecutableWidget;
import me.matl114.versioned.api.VDrawContext;

public class ColorPlateElement extends AbstractElement {
    private static final int CORNER_RADIUS = 8;

    private final ColorSampler color;
    private final boolean catchInteract;

    public ColorPlateElement() {
        this(false, null);
    }

    public ColorPlateElement(ColorSampler color) {
        this(false, color);
    }

    public ColorPlateElement(boolean catchInteract) {
        this(catchInteract, null);
    }

    public ColorPlateElement(boolean catchInteract, ColorSampler color) {
        this.catchInteract = catchInteract;
        this.color = color == null ? ColorSampler.of(-1) : color;
        showTooltips = false;
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
        int width = element.getTextureWidth();
        int height = element.getTextureHeight();
        int radius = Math.min(CORNER_RADIUS, Math.min(width, height) / 2);
        int colorInt = color.getColorInt();

        for (int y = 0; y < height; y++) {
            int cornerY = Math.min(y, height - 1 - y);
            int inset = 0;
            if (cornerY < radius) {
                double distanceFromCenter = radius - cornerY - 0.5;
                inset = (int)
                        Math.ceil(radius - Math.sqrt(radius * radius - distanceFromCenter * distanceFromCenter) - 0.5);
            }
            if (inset < width - inset) {
                context.fill(inset, y, width - inset, y + 1, colorInt);
            }
        }
    }

    @Override
    public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
        return catchInteract;
    }
}
