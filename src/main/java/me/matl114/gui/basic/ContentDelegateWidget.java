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

    public int getHeight() {
        return this.getDelegate() instanceof Widget widget ? widget.getHeight() : this.dy;
    }

    public int getWidth() {
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
            int translatedMouseX = (mouseX - this.getX());

            int translatedMouseY = mouseY - this.getY();
            float textureScale = getTextureScale();
            if (textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / textureScale);
                translatedMouseY = (int) (translatedMouseY / textureScale);
            }
            if (this.getDelegate() instanceof DrawableWidget draw) {

                draw.render0(context, translatedMouseX, translatedMouseY, delta, disableSelect);
            } else {
                this.getDelegate().render(context.pushMatrix(), translatedMouseX, translatedMouseY, delta);
                context.popMatrix();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if (this.getDelegate() != null) {
            int translatedMouseX = (int) (mouseX - this.getX());

            int translatedMouseY = (int) (mouseY - this.getY());
            float textureScale = getTextureScale();
            if (textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / textureScale);
                translatedMouseY = (int) (translatedMouseY / textureScale);
            }
            if (this.getDelegate().mouseClicked(translatedMouseX, translatedMouseY, button)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.getDelegate() != null) {
            int translatedMouseX = (int) (mouseX - this.getX());

            int translatedMouseY = (int) (mouseY - this.getY());
            float textureScale = getTextureScale();
            if (textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / textureScale);
                translatedMouseY = (int) (translatedMouseY / textureScale);
            }
            if (this.getDelegate().mouseReleased(translatedMouseX, translatedMouseY, button)) {
                return true;
            }
        }

        return false;
    }

    public void mouseMoved(double mouseX, double mouseY) {
        // should not move
        if (this.getDelegate() != null) {
            int translatedMouseX = (int) (mouseX - this.getX());

            int translatedMouseY = (int) (mouseY - this.getY());
            float textureScale = getTextureScale();
            if (textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / textureScale);
                translatedMouseY = (int) (translatedMouseY / textureScale);
            }
            this.getDelegate().mouseMoved(translatedMouseX, translatedMouseY);
        }
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // should not drag
        if (this.getDelegate() != null) {
            int translatedMouseX = (int) (mouseX - this.getX());

            int translatedMouseY = (int) (mouseY - this.getY());
            float textureScale = getTextureScale();
            if (textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / textureScale);
                translatedMouseY = (int) (translatedMouseY / textureScale);
            }
            if (this.getDelegate().mouseDragged(
                    translatedMouseX,
                    translatedMouseY,
                    button,
                    deltaX * this.textureScale,
                    deltaY * this.textureScale)) {
                return true;
            }
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // should not scrolled
        if (this.getDelegate() != null) {
            int translatedMouseX = (int) (mouseX - this.getX());

            int translatedMouseY = (int) (mouseY - this.getY());
            float textureScale = getTextureScale();
            if (textureScale != 1.0f) {
                translatedMouseX = (int) (translatedMouseX / textureScale);
                translatedMouseY = (int) (translatedMouseY / textureScale);
            }
            if (this.getDelegate()
                    .mouseScrolled(translatedMouseX, translatedMouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.getDelegate() != null && this.getDelegate().keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return this.getDelegate() != null && this.getDelegate().keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return this.getDelegate() != null && this.getDelegate().charTyped(chr, modifiers);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        float textureScale = getTextureScale();
        return this.getDelegate() != null
                && this.getDelegate()
                        .isMouseOver((mouseX - this.getX()) / textureScale, (mouseY - this.getY()) / textureScale);
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
            draggable.releaseDrag(screen, (mouseX - this.getX()) / textureScale, (mouseY - this.getY()) / textureScale);
        }
    }

    @Override
    public boolean startDrag(Screen screen, double mouseX, double mouseY) {
        if (getDelegate() instanceof Draggable draggable) {
            float textureScale = getTextureScale();
            return draggable.startDrag(
                    screen, (mouseX - this.getX()) / textureScale, (mouseY - this.getY()) / textureScale);
        }
        return false;
    }
}
