package me.matl114.gui.basic;

import net.minecraft.client.gui.screen.Screen;

public class DraggableDelegateWidget extends DelegateWidget implements Draggable{
    // can change a no-drag element to a drag-element


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
        if(this.delegate != null && this.delegate.isMouseOver(mouseX, mouseY)){
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if(dragging && this.delegate != null ){
            return  this.delegate.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }
}
