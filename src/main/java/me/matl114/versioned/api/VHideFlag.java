package me.matl114.versioned.api;

import net.minecraft.item.ItemStack;

public interface VHideFlag {
    public boolean isHide(ItemStack stack);
    public void setHideFlag(ItemStack stack,  boolean hide);
    public String displayName();
}
