package me.matl114.utils.inventory;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

public abstract class ImmutableInventory implements Inventory {
    @Override
    public ItemStack removeStack(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {}

    @Override
    public void markDirty() {}

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return false;
    }

    @Override
    public void clear() {}

    @Override
    public boolean isEmpty() {
        int size = size();
        for (var re = 0; re < size; ++re) {
            var item = getStack(re);
            if (!item.isEmpty()) return false;
        }
        return true;
    }
}
