package me.matl114.gui.elements;

import me.matl114.gui.basic.*;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Colors;

public class ColorBoxElement extends BoxElement {
    private final TextProvider text;
    private final ColorSampler colorSampler;
    private final ColorSampler textSampler;
    private final ColorProvider highLightProvider;

    public ColorBoxElement(
            ButtonAction action, TextProvider provider, ColorSampler colorSampler, ColorProvider highLightProvider) {
        this(action, provider, colorSampler, ColorSampler.WHITE, highLightProvider);
    }

    public ColorBoxElement(
            ButtonAction action,
            TextProvider provider,
            ColorSampler colorSampler,
            ColorSampler textSampler,
            ColorProvider highLightProvider) {
        super(action);
        this.text = provider;
        this.colorSampler = colorSampler;
        this.textSampler = textSampler;
        this.highLightProvider = highLightProvider;
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
        OrderedText text1 = text.getLabel(element);
        context.fill(0, 0, element.getTextureWidth(), element.getTextureHeight(), 0, colorSampler.getColorInt());
        if (text1 != null) {
            RenderHandler.drawScaledText0(
                    context,
                    mc.textRenderer,
                    text1,
                    0,
                    0,
                    element.getTextureWidth(),
                    element.getTextureHeight(),
                    textSampler.getColorInt(),
                    0);
        }
        Integer color = (highLightProvider == null)
                ? (shouldHighlight ? Integer.valueOf(Colors.WHITE) : null)
                : highLightProvider.provideTextColor(element, shouldHighlight);
        if (color != null) {
            RenderHandler.drawHighlightFrame(
                    context, 0, 0, element.getTextureWidth(), element.getTextureHeight(), color);
        }
    }
}
