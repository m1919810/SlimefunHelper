package me.matl114.gui.basic;

import net.minecraft.client.gui.DrawContext;

import java.util.function.Predicate;

public interface ElementHandler extends MouseHandler, RenderHandler{
    default ElementHandler withTooltips(TooltipHandler handler){
        combineAbsoluteRender(handler);
        return this;
    }

    default ElementHandler withPresentCondition(Predicate<ElementHandler> handlerPredicate){
        ElementHandler ob = this;
        return new ElementHandler() {
            @Override
            public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
               return handlerPredicate.test(ob) && ob.onAction(element, mouseX, mouseY, button, type);
            }

            @Override
            public void renderAtCentered(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {
                if(handlerPredicate.test(ob)){
                    ob.renderAtCentered(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
                }
            }

            @Override
            public void renderExtraAbsoluteCoord(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {
                if(handlerPredicate.test(ob)){
                    ob.renderExtraAbsoluteCoord(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
                }
            }
        };
    }

    default ElementHandler withActiveActionCondition(Predicate<ElementHandler> handlerPredicate){
        ElementHandler ob = this;
        return new ElementHandler() {
            @Override
            public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
                return handlerPredicate.test(ob) && ob.onAction(element, mouseX, mouseY, button, type);
            }

            @Override
            public void renderAtCentered(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {
                ob.renderAtCentered(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);

            }

            @Override
            public void renderExtraAbsoluteCoord(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {
                ob.renderExtraAbsoluteCoord(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
            }
        };
    }
}
