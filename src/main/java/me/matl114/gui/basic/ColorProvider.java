package me.matl114.gui.basic;

import javax.annotation.Nullable;
import net.minecraft.client.gui.Drawable;

public interface ColorProvider {
    @Nullable
    public Integer provideTextColor(Drawable widget, boolean isFocused);
}
