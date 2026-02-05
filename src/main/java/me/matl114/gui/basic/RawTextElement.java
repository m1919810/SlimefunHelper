package me.matl114.gui.basic;

import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;

@Accessors(chain = true)
public class RawTextElement extends AbstractElement {
    final TextProvider text;
    @Setter
    int color;
    @Setter
    int alignment;
    public static RawTextElement instance(Text text){
        return new RawTextElement(text, Colors.WHITE);
    }

    public static RawTextElement instance(TextProvider text){
        return new RawTextElement(text, Colors.WHITE, 0);
    }

    public RawTextElement(Text text, int color){
        this(text, color,  0);
    }
    public RawTextElement(Text text, int color,  int alignment){
        this(TextProvider.of(text), color,  alignment);
    }
    public RawTextElement(TextProvider provider, int color, int alignment){
        this.text = provider;
        this.color = color;

        this.alignment = alignment;
    }
    @Override
    public void renderCentered0(DrawableWidget element, VDrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {
        Text text1 = text.getLabel(element);
        if(text1 != null){
            RenderHandler.drawScaledText0(context, mc.textRenderer, text1, 0,0,element.getTextureWidth(), element.getTextureHeight(), color, alignment);
        }
    }

    public boolean canBeSelected(DrawableWidget element){
        return false;
    }
}
