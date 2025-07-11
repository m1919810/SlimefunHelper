package me.matl114.gui.basic;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Predicate;

public abstract class IconElement extends AbstractElement{
    protected final ButtonAction action;

    public abstract boolean isActive();
    public abstract IconElement setActive(boolean active);



    public static abstract class SimpleIconElement extends IconElement {
        private boolean active = true;

        public SimpleIconElement(ButtonAction action) {
            super(action);
        }

        @Override
        public boolean isActive() {
            return active;
        }
        public IconElement setActive(boolean active){
            this.active = active;
            return this;
        }
    }

    public static abstract class PredicatedIconElement extends IconElement{
        Predicate<IconElement> predicate;
        public PredicatedIconElement(ButtonAction action, Predicate<IconElement> element) {
            super(action);
            this.predicate = element;
        }
        @Override
        public boolean isActive() {
            return predicate.test(this);
        }
        public IconElement setActive(boolean active){
            return this;
        }
    }

    public static IconElement fixed(Identifier identifier, ButtonAction action){
        return new SimpleIconElement(action) {
            @Override
            public Identifier getTextureId(DrawContext context, DrawableWidget element, boolean highlight) {
                return identifier;
            }
        };
    }

    public static IconElement stated(Identifier activeState, Identifier inactiveState, ButtonAction action){
        return new SimpleIconElement(action) {
            @Override
            public @Nullable Identifier getTextureId(DrawContext context, DrawableWidget element, boolean highlight) {
                return this.isActive()? activeState: inactiveState;
            }
        };
    }

    public static IconElement statePredicate(Identifier activeState, Identifier inactiveState, ButtonAction action, Predicate<IconElement> activation){
        return new PredicatedIconElement(action, activation) {
            @Override
            public @Nullable Identifier getTextureId(DrawContext context, DrawableWidget element, boolean highlight) {
                return this.isActive()? activeState: inactiveState;
            }
        };
    }
    public static IconElement fixedGui(Identifier identifier, ButtonAction action){
        return new SimpleIconElement(action) {
            @Override
            public @Nullable Identifier getTextureId(DrawContext context, DrawableWidget element, boolean highlight) {
                return identifier;
            }
            public void renderTexture(DrawContext context, DrawableWidget element, boolean highlight){
                Identifier id = getTextureId(context, element, highlight);
                if(id != null){
                    context.drawGuiTexture(id, 0,0, element.getTextureWidth(), element.getTextureHeight());
                }
            }
        };
    }

    public static IconElement statedGui(Identifier active, Identifier inactive, ButtonAction action){
        return new SimpleIconElement(action) {
            @Override
            public @Nullable Identifier getTextureId(DrawContext context, DrawableWidget element, boolean highlight) {
                return this.isActive() ? active: inactive;
            }
            public void renderTexture(DrawContext context, DrawableWidget element, boolean highlight){
                Identifier id = getTextureId(context, element, highlight);
                if(id != null){
                    context.drawGuiTexture(id, 0,0, element.getTextureWidth(), element.getTextureHeight());
                }
            }
        };
    }
    public static IconElement stateGuiPredicate(Identifier activeState, Identifier inactiveState, ButtonAction action, Predicate<IconElement> activation){
        return new PredicatedIconElement(action, activation) {
            @Override
            public @Nullable Identifier getTextureId(DrawContext context, DrawableWidget element, boolean highlight) {
                return this.isActive()? activeState: inactiveState;
            }
            public void renderTexture(DrawContext context, DrawableWidget element, boolean highlight){
                Identifier id = getTextureId(context, element, highlight);
                if(id != null){
                    context.drawGuiTexture(id, 0,0, element.getTextureWidth(), element.getTextureHeight());
                }
            }
        };
    }


    public IconElement(ButtonAction action){
        this.action = action;
    }
    @Nullable
    public abstract Identifier getTextureId(DrawContext context, DrawableWidget element, boolean highlight);

    public void renderTexture(DrawContext context, DrawableWidget element, boolean highlight){
        Identifier id = getTextureId(context, element, highlight);
        if(id != null){
            context.drawTexturedQuad(id, 0,element.getTextureWidth(), 0 , element.getTextureHeight(),0, 0,1,0,1);
        }
    }

    public void renderCentered0(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight){
        context.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        renderTexture(context, element, shouldHighlight);
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
        return action != null && action.onClick(this, element, button);
    }
    @Override
    public ElementHandler withActiveActionCondition(Predicate<ElementHandler> handlerPredicate){
        IconElement ob = this;
        return new ElementHandler() {
            @Override
            public boolean onClick(ExecutableWidget element, double mouseX, double mouseY, int button) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean onAction(ExecutableWidget element, double mouseX, double mouseY, int button, Type type) {
                if(handlerPredicate.test(ob)){
                    ob.setActive(true);
                    return ob.onAction(element, mouseX, mouseY, button, type);
                }else {
                    ob.setActive(false);
                    return false;
                }
            }

            public boolean onScroll(ExecutableWidget widget, double mouseX, double mouseY, double horizontalAmount, double verticalAmount){
                if(handlerPredicate.test(ob)){
                    ob.setActive(true);
                    return ob.onScroll(widget, mouseX, mouseY, horizontalAmount, verticalAmount);
                }else {
                    ob.setActive(false);
                    return false;
                }

            }

            public boolean onKey(ExecutableWidget widget, int keyCode, int scanCode, int modifiers, boolean isPress){
                if(handlerPredicate.test(ob)){
                    ob.setActive(true);
                    return ob.onKey(widget, keyCode, scanCode, modifiers, isPress);
                }else {
                    ob.setActive(false);
                    return false;
                }

            }

            public boolean onTyped(ExecutableWidget widget, char chr, int modifiers){
                if(handlerPredicate.test(ob)){
                    ob.setActive(true);
                    return ob.onTyped(widget, chr, modifiers);
                }else {
                    ob.setActive(false);
                    return false;
                }
            }

            @Override
            public void renderAtCentered(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {
                //update active condition before render
                if(handlerPredicate.test(ob)){
                    ob.setActive(true);
                }else ob.setActive(false);
                ob.renderAtCentered(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);

            }

            @Override
            public void renderExtraAbsoluteCoord(DrawableWidget element, DrawContext context, int mouseX, int mouseY, float delta, float alpha, boolean shouldHighlight) {
                if(handlerPredicate.test(ob)){
                    ob.setActive(true);
                }else ob.setActive(false);
                ob.renderExtraAbsoluteCoord(element, context, mouseX, mouseY, delta, alpha, shouldHighlight);
            }
        };
    }

}
