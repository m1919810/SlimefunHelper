package me.matl114.utils.inventory;

import lombok.AllArgsConstructor;
import net.minecraft.item.ItemStack;

import java.util.List;

@AllArgsConstructor
public class ImmutableListInventory extends ImmutableInventory {
    List<ItemStack> itemStacks;

    @Override
    public int size() {
        return itemStacks.size();
    }

    @Override
    public boolean isEmpty() {
        return itemStacks.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getStack(int slot) {
        return itemStacks.get(slot);
    }


}
