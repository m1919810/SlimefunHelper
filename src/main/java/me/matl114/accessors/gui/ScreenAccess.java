package me.matl114.accessors.gui;

import me.matl114.accessors.events.MetadataHolder;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;

public interface ScreenAccess extends MetadataHolder {
    public <T extends Element & Drawable & Selectable> T addDrawableChildTo(T val);
    public void removeChildFrom(Element val);
    public static ScreenAccess of(Screen screen){
        return (ScreenAccess) screen;
    }
    public Screen getParent();
    public void setParent(Screen screen);
    public void open();
    public void openFromCurrent();
    public void switchToScreen(Screen anotherScreen);
    public void switchFromCurrent();
}
