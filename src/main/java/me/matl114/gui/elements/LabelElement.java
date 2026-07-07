package me.matl114.gui.elements;

import me.matl114.gui.basic.DrawableWidget;
import me.matl114.gui.basic.RenderHandler;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.complex.RawTextElement;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;

public class LabelElement extends RawTextElement {
    protected static final Identifier BACKGROUND_RESOURCE =
            new Identifier("slimefunhelper", "textures/custom/recipecontainer.png");

    public static LabelElement instance(Text text) {
        return new LabelElement(text, Colors.WHITE);
    }

    public LabelElement(int color) {
        this(Text.empty(), color);
    }

    public LabelElement(Text text, int color) {
        this(text, color, 0);
    }

    public LabelElement(Text text, int color, int alignment) {
        this(TextProvider.of(text), color, alignment);
    }

    public LabelElement(TextProvider text, int color, int alignment) {
        super(text, color, alignment);
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
        OrderedText text1 = text.getLabel(element);
        if (text1 != null) {
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
