package me.matl114.gui.elements;

import me.matl114.gui.basic.AbstractElement;
import me.matl114.gui.basic.ColorProvider;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.RenderHandler;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.util.Colors;

/** Renders a one-pixel frame around the element when its color provider returns a color. */
public class FrameElement extends AbstractElement {
    private final ColorProvider colorProvider;

    public FrameElement() {
        this((element, shouldHighlight) -> shouldHighlight ? Colors.WHITE : null);
    }

    public FrameElement(ColorProvider colorProvider) {
        this.colorProvider = colorProvider;
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
        if (colorProvider == null) {
            return;
        }
        Integer color = colorProvider.provideTextColor(element, shouldHighlight);
        if (color != null) {
            RenderHandler.drawHighlightFrame(
                    context, 0, 0, element.getTextureWidth(), element.getTextureHeight(), color);
        }
    }
}
