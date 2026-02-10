package me.matl114.accessors.access;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public interface ItemStackAccess {
    public Item getRealItem();

    static ItemStackAccess of(ItemStack itemStack) {
        return (ItemStackAccess) (Object) itemStack;
    }
}
