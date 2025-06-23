package me.matl114.gui.basic;

import me.matl114.utils.Debug;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class AbstractElement implements ElementHandler {
    List<RenderHandler> extraRender = null;
    List<RenderHandler> absoluteRender = null;
    List<MouseHandler> mouseHandlers = null;
    public AbstractElement combineRender(RenderHandler handler){
        if(extraRender == null){
            extraRender = new ArrayList<>();
        }
        extraRender.add(handler);
        return this;
    }
    public AbstractElement combineAbsoluteRender(RenderHandler handlerAbsolute){
        if(absoluteRender == null){
            absoluteRender = new ArrayList<>();
        }
        absoluteRender.add(handlerAbsolute);
        return this;
    }

    public AbstractElement withTooltips(TooltipHandler handler){
        return combineAbsoluteRender(handler);
    }

    public AbstractElement withMouseHandler(MouseHandler handler){
        if(mouseHandlers == null){
            mouseHandlers = new ArrayList<>();
        }
        mouseHandlers.add(handler);
        return this;
    }


    public final void renderAtCentered(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {
        renderCentered0(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
        if(extraRender != null){
            for (var h : extraRender){
                h.renderAtCentered(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
            }
        }
    }
    public void renderCentered0(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight){

    }


    public final void renderExtraAbsoluteCoord(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {
        renderExtra0(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
        if(absoluteRender != null){
            for (var h: absoluteRender){
                h.renderExtraAbsoluteCoord(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
            }
        }
    }
    public void renderExtra0(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight){

    }

    @Override
    public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button,Type type) {
        if(mouseHandlers != null){
            for ( var h : mouseHandlers){
                if(h.onAction(element, mouseX, mouseY, button, type)){
                    return true;
                }
            }
        }
        return type == Type.MOUSE_CLICK && onClick(element, mouseX, mouseY, button);
    }
}
