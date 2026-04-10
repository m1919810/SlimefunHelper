package me.matl114.gui.basic;

import java.util.function.UnaryOperator;
import lombok.Getter;
import net.minecraft.client.gui.screen.Screen;

public class ExecutableWidget extends DrawableWidget {
    @Getter
    protected InputHandler handler;

    public static ExecutableWidget instance(int x, int y, int dx, int dy) {
        return new ExecutableWidget(x, y, dx, dy);
    }

    public ExecutableWidget(int x, int y, int dx, int dy) {
        super(x, y, dx, dy);
    }

    public <T extends ExecutableWidget> T setMouseHandler(InputHandler handler) {
        this.handler = handler;
        return (T) this;
    }

    public <T extends ExecutableWidget> T updateMouseHandler(UnaryOperator<InputHandler> handlerUnaryOperator) {
        this.handler = handlerUnaryOperator.apply(this.handler);
        return (T) this;
    }

    public <T extends ExecutableWidget> T setElementHandler(ElementHandler handler) {
        this.handler = handler;
        setRenderHandler(handler);
        return (T) this;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handler != null && isMouseOver(mouseX, mouseY)) {
            return handler.onAction(this, mouseX, mouseY, button, InputHandler.Type.MOUSE_CLICK);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (handler != null && isMouseOver(mouseX, mouseY)) {
            return handler.onAction(this, mouseX, mouseY, button, InputHandler.Type.MOUSE_RELEASE);
        }
        return false;
    }

    boolean dragging = false;

    @Override
    public boolean startDrag(Screen screen, double mouseX, double mouseY) {
        if (handler != null && handler.onAction(this, mouseX, mouseY, 0, InputHandler.Type.MOUSE_START_DRAG)) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public void releaseDrag(Screen screen, double mouseX, double mouseY) {
        dragging = false;
        if (handler != null) {
            handler.onAction(this, mouseX, mouseY, 0, InputHandler.Type.MOUSE_RELEASE_DRAG);
        }
    }

    @Override
    public boolean isDragging() {
        return dragging;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (handler != null) {
            // drag do not need MouseOver
            return this.handler.onAction(this, mouseX, mouseY, button, InputHandler.Type.MOUSE_DRAG);
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // should not scrolled

        return this.handler != null && this.handler.onScroll(this, mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.handler != null && this.handler.onKey(this, keyCode, scanCode, modifiers, true);
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return this.handler != null && this.handler.onKey(this, keyCode, scanCode, modifiers, false);
    }

    public boolean charTyped(char chr, int modifiers) {
        return this.handler != null && this.handler.onTyped(this, chr, modifiers);
    }
}
