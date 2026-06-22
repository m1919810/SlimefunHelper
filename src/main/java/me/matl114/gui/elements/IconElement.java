package me.matl114.gui.elements;

import java.awt.*;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matl114.gui.basic.*;
import me.matl114.gui.complex.BoxElement;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

@Accessors(chain = true)
public abstract class IconElement extends BoxElement {
    @Getter
    @Setter
    private ColorProvider highLightColor;

    @Getter
    @Setter
    protected Color shaderColor = Color.WHITE;

    public abstract boolean isActive();

    public abstract IconElement setActive(boolean active);

    @Accessors(chain = true)
    public static class SimpleIconElement extends IconElement {
        private boolean active = true;

        @Getter
        @Setter
        private Predicate<IconElement> activePredicate = null;

        @Getter
        @Setter
        private boolean guiTexture;

        @Getter
        @Setter
        private Identifier inactiveId;

        @Getter
        @Setter
        private Identifier activeId;

        public boolean drawGuiTexture() {
            return guiTexture;
        }

        public SimpleIconElement(Identifier inactiveId, Identifier activeId, boolean gui, ButtonAction action) {
            super(action);
            this.inactiveId = inactiveId;
            this.activeId = activeId;
            this.guiTexture = gui;
        }

        @Override
        public boolean isActive() {
            return activePredicate == null ? active : activePredicate.test(this);
        }

        public IconElement setActive(boolean active) {
            this.active = active;
            return this;
        }

        @Override
        public @Nullable Identifier getTextureId(VDrawContext context, DrawableWidget element, boolean highlight) {
            return isActive() ? activeId : inactiveId;
        }
    }

    //    public static abstract class PredicatedIconElement extends IconElement{
    //        Predicate<IconElement> predicate;
    //        public PredicatedIconElement(ButtonAction action, Predicate<IconElement> element) {
    //            super(action);
    //            this.predicate = element;
    //        }
    //        @Override
    //        public boolean isActive() {
    //            return predicate.test(this);
    //        }
    //        public IconElement setActive(boolean active){
    //            return this;
    //        }
    //    }

    public static IconElement fixed(Identifier identifier, ButtonAction action) {
        return new SimpleIconElement(identifier, identifier, false, action);
    }

    public static IconElement stated(Identifier activeState, Identifier inactiveState, ButtonAction action) {
        return new SimpleIconElement(inactiveState, activeState, false, action);
    }

    public static IconElement statePredicate(
            Identifier activeState, Identifier inactiveState, ButtonAction action, Predicate<IconElement> activation) {
        return ((SimpleIconElement) stated(activeState, inactiveState, action)).setActivePredicate(activation);
    }

    public static IconElement fixedGui(Identifier identifier, ButtonAction action) {
        return new SimpleIconElement(identifier, identifier, true, action);
    }

    public static SimpleIconElement statedGui(Identifier active, Identifier inactive, ButtonAction action) {
        return new SimpleIconElement(inactive, active, true, action);
    }

    public static SimpleIconElement statedGuiPredicate(
            Identifier activeState, Identifier inactiveState, ButtonAction action, Predicate<IconElement> activation) {
        return ((SimpleIconElement) statedGui(activeState, inactiveState, action)).setActivePredicate(activation);
    }

    public IconElement(ButtonAction action) {
        super(action);
    }

    @Nullable
    public abstract Identifier getTextureId(VDrawContext context, DrawableWidget element, boolean highlight);

    public abstract boolean drawGuiTexture();

    public void renderTexture(VDrawContext context, DrawableWidget element, boolean highlight) {
        Identifier id = getTextureId(context, element, highlight);
        if (id != null) {
            if (drawGuiTexture()) {
                context.drawGuiTexture(id, 0, 0, element.getTextureWidth(), element.getTextureHeight());
            } else {
                context.drawTexturedQuad(
                        id, 0, element.getTextureWidth(), 0, element.getTextureHeight(), 0, 0, 1, 0, 1);
            }
        }
        Integer color = (highLightColor == null)
                ? (highlight ? Integer.valueOf(Colors.WHITE) : null)
                : highLightColor.provideTextColor(element, highlight);
        if (color != null) {
            RenderHandler.drawHighlightFrame(
                    context, 0, 0, element.getTextureWidth(), element.getTextureHeight(), color);
        }
    }

    public void renderCentered0(
            DrawableWidget element,
            VDrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            float alpha,
            boolean shouldHighlight) {
        // float[] shaders = RenderSystem.getShaderColor();
        context.setShaderColor(
                this.shaderColor.getRed() / 255.0f,
                this.shaderColor.getGreen() / 255.0f,
                this.shaderColor.getBlue() / 255.0f,
                alpha);
        renderTexture(context, element, shouldHighlight);
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public ElementHandler withActiveActionCondition(Predicate<ElementHandler> handlerPredicate) {
        IconElement ob = this;
        return new ElementHandler() {
            @Override
            public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
                if (handlerPredicate.test(ob)) {
                    ob.setActive(true);
                    return ob.onAction(element, mouseX, mouseY, button, type);
                } else {
                    ob.setActive(false);
                    return false;
                }
            }

            public boolean onScroll(
                    ExecutableWidget widget,
                    double mouseX,
                    double mouseY,
                    double horizontalAmount,
                    double verticalAmount) {
                if (handlerPredicate.test(ob)) {
                    ob.setActive(true);
                    return ob.onScroll(widget, mouseX, mouseY, horizontalAmount, verticalAmount);
                } else {
                    ob.setActive(false);
                    return false;
                }
            }

            public boolean onKey(ExecutableWidget widget, int keyCode, int scanCode, int modifiers, boolean isPress) {
                if (handlerPredicate.test(ob)) {
                    ob.setActive(true);
                    return ob.onKey(widget, keyCode, scanCode, modifiers, isPress);
                } else {
                    ob.setActive(false);
                    return false;
                }
            }

            public boolean onTyped(ExecutableWidget widget, char chr, int modifiers) {
                if (handlerPredicate.test(ob)) {
                    ob.setActive(true);
                    return ob.onTyped(widget, chr, modifiers);
                } else {
                    ob.setActive(false);
                    return false;
                }
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
                // update active condition before render
                if (handlerPredicate.test(ob)) {
                    ob.setActive(true);
                } else ob.setActive(false);
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
                if (handlerPredicate.test(ob)) {
                    ob.setActive(true);
                } else ob.setActive(false);
                ob.renderExtraAbsoluteCoord(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
            }
        };
    }
}
