package me.matl114.gui.basic;

import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;

public interface Draggable extends Element {

    public boolean isDragging();

    public void releaseDrag(Screen screen, double mouseX, double mouseY);

    public boolean startDrag(Screen screen, double mouseX, double mouseY);
}
