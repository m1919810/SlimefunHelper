package me.matl114.accessors.gui;

import me.matl114.gui.basic.ColorProvider;
import net.minecraft.client.gui.widget.ClickableWidget;

public interface TextFieldAccess {
    public void setBorderColorProvider(ColorProvider provider);

    public boolean canStartDrag(double mouseX, double mouseY);

    public void dragSelect(int deltaX, int deltaY, boolean shiftDownAction);

    public void resetSelect();

    static TextFieldAccess of(ClickableWidget clickableWidget) {
        return (TextFieldAccess) clickableWidget;
    }
}
