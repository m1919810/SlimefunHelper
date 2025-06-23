package me.matl114.gui.basic;

import net.minecraft.client.gui.Drawable;

public interface ColorProvider{
    public int provideTextColor(Drawable widget, boolean isFocused);
}