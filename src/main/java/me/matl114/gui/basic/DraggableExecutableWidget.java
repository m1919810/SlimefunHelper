package me.matl114.gui.basic;

import net.minecraft.client.gui.screen.Screen;

public class DraggableExecutableWidget extends ExecutableWidget implements Draggable {
    public DraggableExecutableWidget(int x, int y, int dx, int dy) {
        super(x, y, dx, dy);
    }
    // enable mouseScroll for MouseHandler

    @Override
    public boolean isDragging() {
        return dragging;
    }

    boolean dragging = false;

    @Override
    public void releaseDrag(Screen screen, double mouseX, double mouseY) {
        dragging = false;
    }

    @Override
    public boolean startDrag(Screen screen, double mouseX, double mouseY) {
        if (isMouseOver(mouseX, mouseY)) {
            dragging = true;
            return true;
        }
        return false;
    }
}
