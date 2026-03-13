package me.matl114.gui.basic;

import lombok.Getter;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;

/**
 * this class handles the delegate content's render and click behaviour, but the position and transformation is applied before the handle's
 */
public class ContentDelegateWidget<W extends Element & Drawable & Selectable> extends DrawableWidget
        implements Draggable {

    public ContentDelegateWidget(int x, int y, int dx, int dy) {
        super(x, y, dx, dy);
    }
    // you can put nms widget or sth here, not only DrawableWidget
    @Getter
    protected W delegate;

    public ContentDelegateWidget<W> setContentDelegate(W delegate) {
        this.delegate = delegate;
        return this;
    }

    public int getHeight() {
        return this.delegate instanceof Widget widget ? widget.getHeight() : this.dy;
    }

    public int getWidth() {
        return this.delegate instanceof Widget widget ? widget.getWidth() : this.dx;
    }

    @Override
    public boolean canSelect() {
        return this.delegate != null && ((!(this.delegate instanceof DrawableWidget draw)) || draw.canSelect());
    }

    public void renderInDefaultMatrix(
            VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        super.renderInDefaultMatrix(context, mouseX, mouseY, delta, disableSelect);
        if (this.delegate != null) {
            int translatedMouseX = (mouseX - this.getX());

            int translatedMouseY = mouseY - this.getY();
            if (this.textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / this.textureScale);
                translatedMouseY = (int) (translatedMouseY / this.textureScale);
            }
            if (this.delegate instanceof DrawableWidget draw) {

                draw.render0(context, translatedMouseX, translatedMouseY, delta, disableSelect);
            } else {
                this.delegate.render(context.pushMatrix(), translatedMouseX, translatedMouseY, delta);
                context.popMatrix();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if (this.delegate != null) {
            int translatedMouseX = (int) (mouseX - this.getX());

            int translatedMouseY = (int) (mouseY - this.getY());
            if (this.textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / this.textureScale);
                translatedMouseY = (int) (translatedMouseY / this.textureScale);
            }
            if (this.delegate instanceof DrawableWidget widget) {
                if (widget.mouseClicked(translatedMouseX, translatedMouseY, button)) {
                    return true;
                }
            } else {
                if (this.delegate.mouseClicked(
                        new Click(
                                translatedMouseX,
                                translatedMouseY,
                                new MouseInput(button, DrawableWidget.THREAD_SAFE_MODIFIER_CACHE)),
                        DrawableWidget.THREAD_SAFE_DOUBLE_CLICK)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.delegate != null) {
            int translatedMouseX = (int) (mouseX - this.getX());

            int translatedMouseY = (int) (mouseY - this.getY());
            if (this.textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / this.textureScale);
                translatedMouseY = (int) (translatedMouseY / this.textureScale);
            }
            if (this.delegate instanceof DrawableWidget widget) {
                if (widget.mouseReleased(translatedMouseX, translatedMouseY, button)) {
                    return true;
                }
            } else {
                if (this.delegate.mouseReleased(new Click(
                        translatedMouseX,
                        translatedMouseY,
                        new MouseInput(button, DrawableWidget.THREAD_SAFE_MODIFIER_CACHE)))) {
                    return true;
                }
            }
        }

        return false;
    }

    public void mouseMoved(double mouseX, double mouseY) {
        // should not move
        if (this.delegate != null) {
            int translatedMouseX = (int) (mouseX - this.getX());

            int translatedMouseY = (int) (mouseY - this.getY());
            if (this.textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / this.textureScale);
                translatedMouseY = (int) (translatedMouseY / this.textureScale);
            }
            this.delegate.mouseMoved(translatedMouseX, translatedMouseY);
        }
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // should not drag
        if (this.delegate != null) {
            int translatedMouseX = (int) (mouseX - this.getX());

            int translatedMouseY = (int) (mouseY - this.getY());
            if (this.textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / this.textureScale);
                translatedMouseY = (int) (translatedMouseY / this.textureScale);
            }
            if (this.delegate instanceof DrawableWidget widget) {
                if (widget.mouseDragged(
                        translatedMouseX,
                        translatedMouseY,
                        button,
                        deltaX * this.textureScale,
                        deltaY * this.textureScale)) {
                    return true;
                }
            } else {
                if (this.delegate.mouseDragged(
                        new Click(
                                translatedMouseX,
                                translatedMouseY,
                                new MouseInput(button, DrawableWidget.THREAD_SAFE_MODIFIER_CACHE)),
                        deltaX * this.textureScale,
                        deltaY * this.textureScale)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // should not scrolled
        if (this.delegate != null) {
            int translatedMouseX = (int) (mouseX - this.getX());

            int translatedMouseY = (int) (mouseY - this.getY());
            if (this.textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / this.textureScale);
                translatedMouseY = (int) (translatedMouseY / this.textureScale);
            }
            if (this.delegate.mouseScrolled(translatedMouseX, translatedMouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.delegate instanceof DrawableWidget drawable) {
            return drawable.keyPressed(keyCode, scanCode, modifiers);
        } else return this.delegate != null && this.delegate.keyPressed(new KeyInput(keyCode, scanCode, modifiers));
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (this.delegate instanceof DrawableWidget drawable) {
            return drawable.keyReleased(keyCode, scanCode, modifiers);
        } else return this.delegate != null && this.delegate.keyReleased(new KeyInput(keyCode, scanCode, modifiers));
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.delegate instanceof DrawableWidget drawable) {
            return drawable.charTyped(chr, modifiers);
        } else return this.delegate != null && this.delegate.charTyped(new CharInput(chr, modifiers));
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.delegate != null
                && this.delegate.isMouseOver(
                        (mouseX - this.getX()) / this.textureScale, (mouseY - this.getY()) / this.textureScale);
    }

    @Override
    public boolean isFocused() {
        return this.delegate != null && this.delegate.isFocused();
    }

    public void setFocused(boolean focused) {
        if (this.delegate != null) this.delegate.setFocused(focused);
    }

    public SelectionType getType() {
        return this.delegate == null ? SelectionType.NONE : this.delegate.getType();
    }

    @Override
    public boolean isDragging() {
        return this.delegate != null && this.delegate instanceof Draggable draggable && draggable.isDragging();
    }

    @Override
    public void releaseDrag(Screen screen, double mouseX, double mouseY) {
        if (delegate instanceof Draggable draggable) {
            draggable.releaseDrag(
                    screen, (mouseX - this.getX()) / this.textureScale, (mouseY - this.getY()) / this.textureScale);
        }
    }

    @Override
    public boolean startDrag(Screen screen, double mouseX, double mouseY) {
        if (delegate instanceof Draggable draggable) {
            return draggable.startDrag(
                    screen, (mouseX - this.getX()) / this.textureScale, (mouseY - this.getY()) / this.textureScale);
        }
        return false;
    }
}
