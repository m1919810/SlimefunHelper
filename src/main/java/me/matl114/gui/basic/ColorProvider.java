package me.matl114.gui.basic;

import net.minecraft.client.gui.Drawable;

import javax.annotation.Nullable;

public interface ColorProvider{
    @Nullable
    public Integer provideTextColor(Drawable widget, boolean isFocused);
}