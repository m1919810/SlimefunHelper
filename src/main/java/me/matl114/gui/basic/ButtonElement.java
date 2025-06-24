package me.matl114.gui.basic;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;


public class ButtonElement extends IconElement.SimpleIconElement {
    private final TextProvider provider;
    @Getter
    private ColorProvider color;

    public static final Identifier BUTTON=new Identifier("minecraft","widget/button");
    public static final Identifier BUTTON_HIGHLIGHT= new Identifier("minecraft","widget/button_highlighted");
    public static final Identifier BUTTON_INACTIVE =new Identifier("minecraft","widget/button_disabled");

    public ButtonElement setColorProvider(ColorProvider provider){
        this.color = provider;
        return this;
    }


    public ButtonElement(TextProvider provider, ButtonAction action) {
        super(action);
        this.provider = provider;
    }




    public Identifier getTextureId(DrawContext context, DrawableWidget element, boolean highlight){
        return this.isActive()? (highlight ? BUTTON_HIGHLIGHT: BUTTON):BUTTON_INACTIVE;
    }
    public void renderTexture(DrawContext context, DrawableWidget element, boolean highlight){
        Identifier id = getTextureId(context, element, highlight);
        if(id != null){
            context.drawGuiTexture(id, 0,0, element.getTextureWidth(), element.getTextureHeight());
        }
    }

    public void renderCentered0(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight){
        super.renderCentered0(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
        int i = 16777215 ;
        Text a = provider.getLabel(element);
        if(a != null){
            RenderHandler.drawScaledText0(context, mc.textRenderer, a, 0,0,element.getTextureWidth(), element.getTextureHeight(), i | MathHelper.ceil(alpha * 255.0F) << 24, 0);
        }
    }


//    public static interface ColorProvider{
//        public int provideTextColor(DrawableWidget widget, boolean isFocused);
//    }



}
