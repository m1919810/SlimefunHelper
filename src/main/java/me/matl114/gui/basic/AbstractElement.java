package me.matl114.gui.basic;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.versioned.api.VDrawContext;

@Accessors(chain = true)
public class AbstractElement implements ElementHandler {
    List<RenderHandler> extraRender = null;
    List<RenderHandler> absoluteRender = null;
    List<InputHandler> mouseHandlers = null;

    @Setter
    @Getter
    boolean showTooltips = true;

    public AbstractElement combineRender(RenderHandler handler) {
        if (extraRender == null) {
            extraRender = new ArrayList<>();
        }
        extraRender.add(handler);
        return this;
    }

    @Override
    public final boolean canBeSelected(DrawableWidget element) {
        return showTooltips;
    }

    public AbstractElement combineAbsoluteRender(RenderHandler handlerAbsolute) {
        if (handlerAbsolute == null) return this;
        if (absoluteRender == null) {
            absoluteRender = new ArrayList<>();
        }
        if (handlerAbsolute instanceof TooltipHandler handler) {
            // can only keep one tooltipHandler at a time
            absoluteRender.removeIf(i -> i instanceof TooltipHandler);
        }
        absoluteRender.add(handlerAbsolute);
        return this;
    }

    public AbstractElement withTooltips(TooltipHandler handler) {
        return combineAbsoluteRender(handler);
    }

    public AbstractElement withInputHandler(InputHandler handler) {
        if (mouseHandlers == null) {
            mouseHandlers = new ArrayList<>();
        }
        mouseHandlers.add(handler);
        return this;
    }

    public final void renderAtCentered(
            DrawableWidget element,
            VDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            float alpha,
            boolean shouldHighlight) {
        renderCentered0(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
        if (extraRender != null) {
            for (var h : extraRender) {
                h.renderAtCentered(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
            }
        }
    }

    public void renderCentered0(
            DrawableWidget element,
            VDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            float alpha,
            boolean shouldHighlight) {}

    public final void renderExtraAbsoluteCoord(
            DrawableWidget element,
            VDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            float alpha,
            boolean shouldHighlight) {
        renderExtra0(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
        if (absoluteRender != null) {
            for (var h : absoluteRender) {

                h.renderExtraAbsoluteCoord(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
            }
        }
    }

    public void renderExtra0(
            DrawableWidget element,
            VDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            float alpha,
            boolean shouldHighlight) {}

    @Override
    public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
        if (mouseHandlers != null) {
            for (var h : mouseHandlers) {
                if (h.onAction(element, mouseX, mouseY, button, type)) {
                    return true;
                }
            }
        }
        return type == Type.MOUSE_CLICK && onClick(element, mouseX, mouseY, button);
    }

    @Override
    public boolean onScroll(
            ExecutableWidget widget, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseHandlers != null) {
            for (var h : mouseHandlers) {
                if (h.onScroll(widget, mouseX, mouseY, horizontalAmount, verticalAmount)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onKey(ExecutableWidget widget, int keyCode, int scanCode, int modifiers, boolean isPress) {
        if (mouseHandlers != null) {
            for (var h : mouseHandlers) {
                if (h.onKey(widget, keyCode, scanCode, modifiers, isPress)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onTyped(ExecutableWidget widget, char chr, int modifiers) {
        if (mouseHandlers != null) {
            for (var h : mouseHandlers) {
                if (h.onTyped(widget, chr, modifiers)) {
                    return true;
                }
            }
        }
        return false;
    }
}
