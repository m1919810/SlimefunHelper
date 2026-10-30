package me.matl114.gui.basic;

import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;

/**
 * this class handles the delegate content's render and click behaviour, but the position and transformation is applied before the handle's
 */
public class ContentDelegateWidget<W extends Element & Drawable & Selectable> extends DrawableWidget
        implements Draggable {

    public ContentDelegateWidget(int x, int y, int dx, int dy) {
        super(x, y, dx, dy);
    }
    // you can put nms widget or sth here, not only DrawableWidget

    public W getDelegate() {
        return delegate;
    }

    private W delegate;

    public ContentDelegateWidget<W> setContentDelegate(W delegate) {
        this.delegate = delegate;
        return this;
    }

    public int height() {
        return this.getDelegate() instanceof Widget widget ? widget.getHeight() : this.dy;
    }

    public int width() {
        return this.getDelegate() instanceof Widget widget ? widget.getWidth() : this.dx;
    }

    @Override
    public boolean canSelect() {
        return this.getDelegate() != null
                && ((!(this.getDelegate() instanceof DrawableWidget draw)) || draw.canSelect());
    }

    public void renderInDefaultMatrix(
            VDrawContext context, int mouseX, int mouseY, float delta, boolean disableSelect) {
        super.renderInDefaultMatrix(context, mouseX, mouseY, delta, disableSelect);
        if (this.getDelegate() != null) {
            int translatedMouseX = (mouseX - this.x());

            int translatedMouseY = mouseY - this.y();
            float textureScale = getTextureScale();
            if (textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / textureScale);
                translatedMouseY = (int) (translatedMouseY / textureScale);
            }
            if (this.getDelegate() instanceof DrawableWidget draw) {

                draw.render0(context, translatedMouseX, translatedMouseY, delta, disableSelect);
            } else {
                var drawContext = context.pushMatrix();
                try {
                    this.getDelegate().render(drawContext, translatedMouseX, translatedMouseY, delta);
                } finally {
                    context.popMatrix();
                }
            }
        }
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {

        if (this.getDelegate() != null) {
            int translatedMouseX = (int) (mouseX - this.x());

            int translatedMouseY = (int) (mouseY - this.y());
            float textureScale = getTextureScale();
            if (textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / textureScale);
                translatedMouseY = (int) (translatedMouseY / textureScale);
            }
            if (this.getDelegate() instanceof DrawableWidget widget) {
                if (widget.onMouseClicked(translatedMouseX, translatedMouseY, button)) {
                    return true;
                }
            } else {
                if (this.getDelegate().mouseClicked(translatedMouseX, translatedMouseY, button)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (this.getDelegate() != null) {
            int translatedMouseX = (int) (mouseX - this.x());

            int translatedMouseY = (int) (mouseY - this.y());
            float textureScale = getTextureScale();
            if (textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / textureScale);
                translatedMouseY = (int) (translatedMouseY / textureScale);
            }
            if (this.getDelegate() instanceof DrawableWidget widget) {
                if (widget.onMouseReleased(translatedMouseX, translatedMouseY, button)) {
                    return true;
                }
            } else {
                if (this.getDelegate().mouseReleased(translatedMouseX, translatedMouseY, button)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void onMouseMoved(double mouseX, double mouseY) {
        // should not move
        if (this.getDelegate() != null) {
            int translatedMouseX = (int) (mouseX - this.x());

            int translatedMouseY = (int) (mouseY - this.y());
            float textureScale = getTextureScale();
            if (textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / textureScale);
                translatedMouseY = (int) (translatedMouseY / textureScale);
            }
            if (this.getDelegate() instanceof DrawableWidget drawableWidget) {
                drawableWidget.onMouseMoved(translatedMouseX, translatedMouseY);
            } else {
                this.getDelegate().mouseMoved(translatedMouseX, translatedMouseY);
            }
        }
    }

    public boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // should not drag
        if (this.getDelegate() != null) {
            int translatedMouseX = (int) (mouseX - this.x());

            int translatedMouseY = (int) (mouseY - this.y());
            float textureScale = getTextureScale();
            if (textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / textureScale);
                translatedMouseY = (int) (translatedMouseY / textureScale);
            }
            if (this.getDelegate() instanceof DrawableWidget widget) {
                if (widget.onMouseDragged(
                        translatedMouseX, translatedMouseY, button, deltaX * textureScale, deltaY * textureScale)) {
                    return true;
                }
            } else {
                if (this.getDelegate()
                        .mouseDragged(
                                translatedMouseX,
                                translatedMouseY,
                                button,
                                deltaX * textureScale,
                                deltaY * textureScale)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // should not scrolled
        if (this.getDelegate() != null) {
            int translatedMouseX = (int) (mouseX - this.x());

            int translatedMouseY = (int) (mouseY - this.y());
            float textureScale = getTextureScale();
            if (textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / textureScale);
                translatedMouseY = (int) (translatedMouseY / textureScale);
            }
            if (this.getDelegate() instanceof DrawableWidget drawable) {
                return drawable.onMouseScrolled(translatedMouseX, translatedMouseY, horizontalAmount, verticalAmount);
            } else {
                return this.getDelegate()
                        .mouseScrolled(translatedMouseX, translatedMouseY, horizontalAmount, verticalAmount);
            }
        }
        return false;
    }

    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.getDelegate() instanceof DrawableWidget drawable) {
            return drawable.onKeyPressed(keyCode, scanCode, modifiers);
        } else return this.getDelegate() != null && this.getDelegate().keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean onKeyReleased(int keyCode, int scanCode, int modifiers) {
        if (this.getDelegate() instanceof DrawableWidget drawable) {
            return drawable.onKeyReleased(keyCode, scanCode, modifiers);
        } else return this.getDelegate() != null && this.getDelegate().keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean onCharTyped(char chr, int modifiers) {
        if (this.getDelegate() instanceof DrawableWidget drawable) {
            return drawable.onCharTyped(chr, modifiers);
        } else return this.getDelegate() != null && this.getDelegate().charTyped(chr, modifiers);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        float textureScale = getTextureScale();
        return this.getDelegate() != null
                && this.getDelegate()
                        .isMouseOver((mouseX - this.x()) / textureScale, (mouseY - this.y()) / textureScale);
    }

    @Override
    public boolean isFocused() {
        return this.getDelegate() != null && this.getDelegate().isFocused();
    }

    public void setFocused(boolean focused) {
        if (this.getDelegate() != null) this.getDelegate().setFocused(focused);
    }

    public SelectionType getType() {
        return this.getDelegate() == null
                ? SelectionType.NONE
                : this.getDelegate().getType();
    }

    @Override
    public boolean isDragging() {
        return this.getDelegate() != null
                && this.getDelegate() instanceof Draggable draggable
                && draggable.isDragging();
    }

    @Override
    public void releaseDrag(Screen screen, double mouseX, double mouseY) {
        if (getDelegate() instanceof Draggable draggable) {
            float textureScale = getTextureScale();
            draggable.releaseDrag(screen, (mouseX - this.x()) / textureScale, (mouseY - this.y()) / textureScale);
        }
    }

    @Override
    public boolean startDrag(Screen screen, double mouseX, double mouseY) {
        if (getDelegate() instanceof Draggable draggable) {
            float textureScale = getTextureScale();
            return draggable.startDrag(screen, (mouseX - this.x()) / textureScale, (mouseY - this.y()) / textureScale);
        }
        return false;
    }
}
