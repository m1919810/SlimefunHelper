package me.matl114.utils;

import me.matl114.utils.UtilClass.ImmutableInventory;
import me.matl114.utils.UtilClass.ImmutableListInventory;
import me.matl114.utils.UtilClass.MutableInventory;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

@ApiMethod
public class InventoryUtils {
    public static Inventory createReadOnlyOneItemInventory(Supplier<ItemStack> itemStackSupplier){
        return new ImmutableInventory() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public boolean isEmpty() {
                return itemStackSupplier.get().isEmpty();
            }

            @Override
            public ItemStack getStack(int slot) {
                return itemStackSupplier.get();
            }
        };
    }
    public static Inventory createReadOnlyInventory(List<ItemStack> itemStackSupplier){
        return new ImmutableListInventory(itemStackSupplier);
    }

    public static Inventory createInventory(List<ItemStack> itemStackSupplier){
        return createInventory(itemStackSupplier.size(), itemStackSupplier);
    }

    public static Inventory createInventory(int size, List<ItemStack> itemStackSupplier){
        return new MutableInventory(size, itemStackSupplier);
    }

    public static List<ItemStack> getContainerFromItem(ItemStack itemStack){
        if(ItemStackUtils.hasInPatch(itemStack, DataComponentTypes.CONTAINER)){
            ContainerComponent component = ItemStackUtils.getInPatch(itemStack, DataComponentTypes.CONTAINER);
            if(component != null){
                return component.stream().toList();
            }
        }
        return null;
    }
}
