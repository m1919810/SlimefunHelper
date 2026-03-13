package me.matl114.gui.basic;

import java.util.function.Predicate;
import me.matl114.versioned.api.VDrawContext;

public interface ElementHandler extends InputHandler, RenderHandler {
    default ElementHandler withTooltips(TooltipHandler handler) {
        combineAbsoluteRender(handler);
        return this;
    }

    default ElementHandler withPresentCondition(Predicate<ElementHandler> handlerPredicate) {
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

            public boolean onScroll(
                    ExecutableWidget widget,
                    double mouseX,
                    double mouseY,
                    double horizontalAmount,
                    double verticalAmount) {
                return handlerPredicate.test(ob)
                        && ob.onScroll(widget, mouseX, mouseY, horizontalAmount, verticalAmount);
            }

            public boolean onKey(ExecutableWidget widget, int keyCode, int scanCode, int modifiers, boolean isPress) {
                return handlerPredicate.test(ob) && ob.onKey(widget, keyCode, scanCode, modifiers, isPress);
            }

            public boolean onTyped(ExecutableWidget widget, char chr, int modifiers) {
                return handlerPredicate.test(ob) && ob.onTyped(widget, chr, modifiers);
            }

            @Override
            public void renderAtCentered(
                    DrawableWidget element,
                    VDrawContext context,
                    int mouseX,
                    int mouseY,
                    float delta,
                    float alpha,
                    boolean shouldHighlight) {
                if (handlerPredicate.test(ob)) {
                    ob.renderAtCentered(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
                }
            }

            @Override
            public void renderExtraAbsoluteCoord(
                    DrawableWidget element,
                    VDrawContext context,
                    int mouseX,
                    int mouseY,
                    float delta,
                    float alpha,
                    boolean shouldHighlight) {
                if (handlerPredicate.test(ob)) {
                    ob.renderExtraAbsoluteCoord(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
                }
            }

            public boolean canBeSelected(DrawableWidget element) {
                return handlerPredicate.test(ob) && ob.canBeSelected(element);
            }
        };
    }

    default ElementHandler withActiveActionCondition(Predicate<ElementHandler> handlerPredicate) {
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

            public boolean onScroll(
                    ExecutableWidget widget,
                    double mouseX,
                    double mouseY,
                    double horizontalAmount,
                    double verticalAmount) {
                return handlerPredicate.test(ob)
                        && ob.onScroll(widget, mouseX, mouseY, horizontalAmount, verticalAmount);
            }

            public boolean onKey(ExecutableWidget widget, int keyCode, int scanCode, int modifiers, boolean isPress) {
                return handlerPredicate.test(ob) && ob.onKey(widget, keyCode, scanCode, modifiers, isPress);
            }

            public boolean onTyped(ExecutableWidget widget, char chr, int modifiers) {
                return handlerPredicate.test(ob) && ob.onTyped(widget, chr, modifiers);
            }

            @Override
            public void renderAtCentered(
                    DrawableWidget element,
                    VDrawContext context,
                    int mouseX,
                    int mouseY,
                    float delta,
                    float alpha,
                    boolean shouldHighlight) {
                ob.renderAtCentered(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
            }

            @Override
            public void renderExtraAbsoluteCoord(
                    DrawableWidget element,
                    VDrawContext context,
                    int mouseX,
                    int mouseY,
                    float delta,
                    float alpha,
                    boolean shouldHighlight) {
                ob.renderExtraAbsoluteCoord(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
            }
        };
    }
}
