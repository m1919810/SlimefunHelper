package me.matl114.gui.basic;

import me.matl114.utils.Debug;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class TooltipHandler implements RenderHandler{
    final TooltipProvider provider;
    public static TooltipHandler of(List<Text> list){
        return new TooltipHandler(list);
    }
    public static TooltipHandler of(TooltipProvider provider){
        return new TooltipHandler(provider);
    }
    public static TooltipHandler of(Supplier<List<Text>> listSupplier){
        return new TooltipHandler(TooltipProvider.of(listSupplier));
    }

    public TooltipHandler(List<Text> provider){
        this(TooltipProvider.of(provider));
    }
    public TooltipHandler(TooltipProvider provider){
        this.provider = provider;
    }
    @Override
    public final void renderAtCentered(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {

    }


    public void renderExtraAbsoluteCoord(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight){

        if(shouldHighlight){
            if(provider != null){
                List<Text> texts = provider.getTooltips(element);
                if(texts != null && !texts.isEmpty()){
                    context.drawTooltip(mc.textRenderer, texts, Optional.empty(), mouseX, mouseY);
                }
            }
        }
    }
    public interface TooltipProvider{
        List<Text> getTooltips(DrawableWidget element);

        static TooltipProvider of(List<Text> a){
            return (e)->a;
        }
        static TooltipProvider of(Supplier<List<Text>> t){
            return (e)->t.get();
        }
    }
}
