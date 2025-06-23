package me.matl114.utils;

import me.matl114.utils.UtilClass.ImmutableInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

import java.util.function.Supplier;

public class InventoryUtils {
    public static Inventory createReadOnlyInventory(Supplier<ItemStack> itemStackSupplier){
        return new ImmutableInventory() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public ItemStack getStack(int slot) {
                return itemStackSupplier.get();
            }
        };
    }
}
