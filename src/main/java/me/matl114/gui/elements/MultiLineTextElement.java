package me.matl114.gui.elements;

import lombok.experimental.Accessors;
import me.matl114.gui.basic.ColorSampler;
import me.matl114.gui.basic.TextAlign;
import me.matl114.gui.basic.TextProvider;
import me.matl114.gui.complex.RawTextElement;
import net.minecraft.text.Text;

@Accessors(chain = true)
public class MultiLineTextElement extends RawTextElement {
    public MultiLineTextElement(Text text, int color) {
        this(TextProvider.of(text), color);
    }

    public MultiLineTextElement(Text text, int color, TextAlign alignment) {
        this(TextProvider.of(text, alignment), color);
    }

    public MultiLineTextElement(TextProvider provider, int color) {
        super(provider, ColorSampler.of(color), true);
    }

    public MultiLineTextElement(TextProvider provider, int color, TextAlign alignment) {
        super(TextProvider.withAlign(provider, alignment), ColorSampler.of(color), true);
    }
}
