package me.matl114.utils;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.inventory.ImmutableInventory;
import me.matl114.utils.inventory.ImmutableListInventory;
import me.matl114.utils.inventory.MutableInventory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

@ApiMethod
public class InventoryUtils {
    public static Inventory createReadOnlyOneItemInventory(Supplier<ItemStack> itemStackSupplier) {
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

    public static Inventory createReadOnlyInventory(List<ItemStack> itemStackSupplier) {
        return new ImmutableListInventory(itemStackSupplier);
    }

    public static Inventory createInventory(List<ItemStack> itemStackSupplier) {
        return createInventory(itemStackSupplier.size(), itemStackSupplier);
    }

    public static Inventory createInventory(int size, List<ItemStack> itemStackSupplier) {
        return new MutableInventory(size, itemStackSupplier);
    }

    public static List<ItemStack> getContainerFromItem(ItemStack itemStack) {
        if (ItemStackUtils.hasInPatch(itemStack, DataComponentTypes.CONTAINER)) {
            ContainerComponent component = ItemStackUtils.getInPatch(itemStack, DataComponentTypes.CONTAINER);
            if (component != null) {
                return component.stream().toList();
            }
        }
        return null;
    }

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static IndexEntry<ItemStack> findPlayerItem(
            Predicate<ItemStack> predicate, boolean doNotFSearchWhenOpenOtherScreen) {
        PlayerInventory pinv = mc.player.getInventory();
        ItemStack item = mc.player.getStackInHand(Hand.MAIN_HAND);
        // we assert player hold block while scaffold, or it will be really annoying
        // the holding block must be a full cube
        int selecedSlot = pinv.getSelectedSlot();
        if (!item.isEmpty() && predicate.test(item)) {
            return new IndexEntry<>(selecedSlot, item);
        }
        // while player is open Screen
        if (mc.player.currentScreenHandler.syncId != mc.player.playerScreenHandler.syncId) {
            return null;
        }
        for (var i = 0; i < pinv.size(); ++i) {
            ItemStack stack = pinv.getStack(i);
            if (!stack.isEmpty() && predicate.test(stack)) {
                //                if(keepInHand.get()){
                //                    MovTasks.getMovExtra().sendPacketsForInventoryAction();
                //                    OptionalInt slotIndex = mc.player.currentScreenHandler.getSlotIndex(pinv, i);
                //                    if(slotIndex.isPresent()){
                //                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,
                // slotIndex.getAsInt(), selecedSlot, SlotActionType.SWAP, mc.player);
                //                        return selecedSlot;
                //                    }
                //                }else
                return new IndexEntry<>(i, stack);
            }
        }
        return null;
    }
}
