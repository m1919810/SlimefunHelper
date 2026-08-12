package me.matl114.utils.inventory;

import java.util.Arrays;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

public class MutableArrayInventory implements Inventory {
    ItemStack[] stacks;

    public MutableArrayInventory(ItemStack[] stacks) {
        this.stacks = stacks;
    }

    @Override
    public int size() {
        return stacks.length;
    }

    @Override
    public boolean isEmpty() {
        return Arrays.stream(stacks).allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getStack(int slot) {
        return stacks[slot];
    }

    private ItemStack splitStack(int slot, int amount) {
        return slot >= 0 && slot < stacks.length && !((ItemStack) stacks[slot]).isEmpty() && amount > 0
                ? ((ItemStack) stacks[slot]).split(amount)
                : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack itemStack = splitStack(slot, amount);
        if (!itemStack.isEmpty()) {
            this.markDirty();
        }

        return itemStack;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack itemStack = (ItemStack) this.stacks[slot];
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            this.stacks[slot] = ItemStack.EMPTY; // .set(slot, ItemStack.EMPTY);
            markDirty();
            return itemStack;
        }
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        this.stacks[slot] = stack; // .set(slot, stack);
        markDirty();
    }

    @Override
    public void markDirty() {}

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void clear() {
        for (var i = 0; i < this.stacks.length; i++) {
            stacks[i] = ItemStack.EMPTY; // .set(i, ItemStack.EMPTY);
        }
        markDirty();
    }
}
