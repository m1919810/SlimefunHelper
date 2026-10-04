package me.matl114.gui.elements;

import me.matl114.gui.basic.AbstractElement;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.TextAlign;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.complex.RawTextElement;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;

public class LabelElement extends AbstractElement {
    private final RawTextElement textElement;

    protected static final Identifier BACKGROUND_RESOURCE =
            new Identifier("slimefunhelper", "textures/custom/recipecontainer.png");

    public static LabelElement instance(Text text) {
        return new LabelElement(text, Colors.WHITE);
    }

    public LabelElement(int color) {
        this(Text.empty(), color);
    }

    public LabelElement(Text text, int color) {
        this(text, color, TextAlign.MIDDLE);
    }

    public LabelElement(Text text, int color, TextAlign alignment) {
        this(TextProvider.of(text, alignment), color);
    }

    public LabelElement(TextProvider text, int color) {
        this(new RawTextElement(text, color));
    }

    public LabelElement(TextProvider text, int color, TextAlign alignment) {
        this(new RawTextElement(text, color, alignment));
    }

    public LabelElement(RawTextElement textElement) {
        this.textElement = textElement;
    }

    protected static final float u0 = (110f / 256);
    protected static final float v0 = (60f / 256);
    protected static final float u1 = (126f / 256);
    protected static final float v1 = (76f / 256);

    @Override
    public void renderCentered0(
            DrawableWidget element,
            VDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            float alpha,
            boolean shouldHighlight) {
        Text text = textElement.getText(element);
        if (text != null) {
            context.drawTexturedQuad(
                    BACKGROUND_RESOURCE,
                    0,
                    element.getTextureWidth(),
                    0,
                    element.getTextureHeight(),
                    0,
                    u0,
                    u1,
                    v0,
                    v1);
            textElement.renderText(element, context, text);
        }
    }
}
