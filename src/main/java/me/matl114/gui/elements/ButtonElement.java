package me.matl114.gui.elements;

import me.matl114.gui.Constants;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.ColorSampler;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.RenderHandler;
import me.matl114.gui.basic.TextProvider;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.util.math.MathHelper;

public class ButtonElement extends IconElement.SimpleIconElement {
    private final TextProvider provider;
    private final ColorSampler textColor;

    public ButtonElement(TextProvider provider, ButtonAction action) {
        this(provider, action, ColorSampler.WHITE);
    }

    public ButtonElement(TextProvider provider, ButtonAction action, ColorSampler textColor) {
        super(Constants.BUTTON_INACTIVE_SPRITE, Constants.BUTTON_SPRITE, true, action);
        this.provider = provider;
        this.textColor = textColor;
    }

    public void renderCentered0(
            DrawableWidget element,
            VDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            float alpha,
            boolean shouldHighlight) {
        super.renderCentered0(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
        int i = textColor.getColorInt();
        OrderedText a = provider.getLabel(element);
        if (a != null) {
            RenderHandler.drawScaledText0(
                    context,
                    context.getTextDrawer(),
                    a,
                    0,
                    0,
                    element.getTextureWidth(),
                    element.getTextureHeight(),
                    i | MathHelper.ceil(alpha * 255.0F) << 24,
                    provider.getAlign(element));
        }
    }

    //    public static interface ColorProvider{
    //        public int provideTextColor(DrawableWidget widget, boolean isFocused);
    //    }

}
