package me.matl114.gui.basic;

import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class ButtonElement extends IconElement.SimpleIconElement {
    private final TextProvider provider;

    public static final Identifier BUTTON = new Identifier("minecraft", "widget/button");
    public static final Identifier BUTTON_HIGHLIGHT = new Identifier("minecraft", "widget/button_highlighted");
    public static final Identifier BUTTON_INACTIVE = new Identifier("minecraft", "widget/button_disabled");

    public ButtonElement(TextProvider provider, ButtonAction action) {
        super(BUTTON_INACTIVE, BUTTON, true, action);
        this.provider = provider;
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
        int i = 16777215;
        Text a = provider.getLabel(element);
        if (a != null) {
            RenderHandler.drawScaledText0(
                    context,
                    mc.textRenderer,
                    a,
                    0,
                    0,
                    element.getTextureWidth(),
                    element.getTextureHeight(),
                    i | MathHelper.ceil(alpha * 255.0F) << 24,
                    0);
        }
    }

    //    public static interface ColorProvider{
    //        public int provideTextColor(DrawableWidget widget, boolean isFocused);
    //    }

}
