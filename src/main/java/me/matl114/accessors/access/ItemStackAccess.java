package me.matl114.accessors.access;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface ItemStackAccess {
    @Nullable
    public Item getRealItem();

    static ItemStackAccess of(ItemStack itemStack) {
        return (ItemStackAccess) (Object) itemStack;
    }
}
