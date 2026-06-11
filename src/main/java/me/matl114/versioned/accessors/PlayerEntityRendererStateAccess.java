package me.matl114.versioned.accessors;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;

public interface PlayerEntityRendererStateAccess {
    public Arm getSpearingHand();

    public void setSpearingHand(Arm hand);

    public ItemStack getSpearingItem();

    public void setSpearingItem(ItemStack stack);
}
