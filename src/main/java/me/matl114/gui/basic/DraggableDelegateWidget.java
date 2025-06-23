package me.matl114.gui.basic;

import net.minecraft.client.gui.screen.Screen;

public class DraggableDelegateWidget extends DelegateWidget implements Draggable{
    // can change a no-drag element to a drag-element
    @Override
    public boolean canDrag(double mouseX, double mouseY) {
        return this.delegate != null && this.delegate.isMouseOver(mouseX, mouseY);
    }

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
    public void startDrag(Screen screen, double mouseX, double mouseY) {
        dragging = true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if(dragging && this.delegate != null ){
            return  this.delegate.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }
}
