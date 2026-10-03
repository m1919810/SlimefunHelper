package me.matl114.gui.complex;

import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.gui.basic.*;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;

@Accessors(chain = true)
public class RawTextElement extends AbstractElement {
    protected final TextProvider text;

    protected final ColorSampler color;

    public RawTextElement setAlignment(TextAlign alignment) {
        return new RawTextElement(TextProvider.withAlign(this.text, alignment), this.color);
    }

    public static RawTextElement instance(Text text) {
        return new RawTextElement(text, Colors.WHITE);
    }

    public static RawTextElement instance(TextProvider text) {
        return new RawTextElement(text, Colors.WHITE);
    }

    public RawTextElement(Text text, int color) {
        this(TextProvider.of(text), color);
    }

    public RawTextElement(Text text, int color, int alignment) {
        this(TextProvider.of(text, TextAlign.fromLegacy(alignment)), color);
    }

    public RawTextElement(Text text, int color, TextAlign alignment) {
        this(TextProvider.of(text, alignment), color);
    }

    public RawTextElement(TextProvider provider, int color) {
        this(provider, ColorSampler.of(color));
    }

    public RawTextElement(TextProvider provider, int color, TextAlign alignment) {
        this(TextProvider.withAlign(provider, alignment), color);
    }

    public RawTextElement(TextProvider provider, ColorSampler color) {
        this.text = provider;
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
                    text.getAlign(element));
        }
    }
}
