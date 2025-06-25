package me.matl114.access;

import me.matl114.gui.basic.ButtonElement;
import me.matl114.gui.basic.ColorProvider;
import me.matl114.utils.UtilClass.PropertyTracker;
import net.minecraft.client.gui.widget.ClickableWidget;

public interface TextFieldAccess {
    boolean isMultiLine();
    String getTextContent();
    void setTextContent(String value);
    void setListener(PropertyTracker<TextFieldAccess, String> tracker);
    public void setBorderColorProvider(ColorProvider provider);

    public void dragSelect(int deltaX, int deltaY, boolean shiftDownAction);
    public void resetSelect();

    static TextFieldAccess of(ClickableWidget clickableWidget){
        return (TextFieldAccess) clickableWidget;
    }
}
