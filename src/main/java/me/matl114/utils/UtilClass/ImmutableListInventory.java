package me.matl114.utils.UtilClass;

import lombok.AllArgsConstructor;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
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
        return size() == 0;
    }

    @Override
    public ItemStack getStack(int slot) {
        return itemStacks.get(slot);
    }


}
