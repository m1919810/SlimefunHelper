package me.matl114.utils.inventory;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

public class SlotInventory implements Inventory {
    public SlotInventory(List<Slot> slots) {
        this.slots = slots;
    }

    public List<Slot> slots;

    @Override
    public int size() {
        return slots.size();
    }

    @Override
    public boolean isEmpty() {
        return slots.stream().allMatch(s -> s.getStack().isEmpty());
    }

    @Override
    public ItemStack getStack(int slot) {
        return slots.get(slot).getStack();
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack stack = slots.get(slot).getStack();
        ItemStack removed;
        if (stack.isEmpty() || amount <= 0) {
            removed = ItemStack.EMPTY;
        } else {
            removed = stack.split(amount);
        }

        if (!removed.isEmpty()) {
            this.markDirty();
        }

        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack itemStack = this.slots.get(slot).getStack();
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            this.slots.get(slot).setStack(ItemStack.EMPTY);
            markDirty();
            return itemStack;
        }
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        this.slots.get(slot).setStack(stack);
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
        for (var i = 0; i < this.slots.size(); i++) {
            slots.get(i).setStack(ItemStack.EMPTY);
        }
        markDirty();
    }
}
