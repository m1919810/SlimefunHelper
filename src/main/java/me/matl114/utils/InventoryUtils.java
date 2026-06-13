package me.matl114.utils;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import me.matl114.accessors.access.ClientPlayerAccess;
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
import net.minecraft.screen.slot.Slot;
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
            Predicate<ItemStack> predicate, boolean doNotFSearchWhenOpenOtherScreen, boolean acceptEmpty) {
        return findPlayerItem(predicate, doNotFSearchWhenOpenOtherScreen, acceptEmpty, true);
    }

    public static IndexEntry<ItemStack> findPlayerItem(
            Predicate<ItemStack> predicate,
            boolean doNotFSearchWhenOpenOtherScreen,
            boolean acceptEmpty,
            boolean handPriority) {
        return findPlayerItem(predicate, doNotFSearchWhenOpenOtherScreen, acceptEmpty, handPriority, false);
    }

    public static IndexEntry<ItemStack> findPlayerItem(
            Predicate<ItemStack> predicate,
            boolean doNotFSearchWhenOpenOtherScreen,
            boolean acceptEmpty,
            boolean handPriority,
            boolean offHandPriority) {
        return findPlayerInventory(
                (val) -> predicate.test(val.val()),
                doNotFSearchWhenOpenOtherScreen,
                acceptEmpty,
                handPriority,
                offHandPriority);
    }

    public static IndexEntry<ItemStack> findPlayerInventory(
            Predicate<IndexEntry<ItemStack>> predicate,
            boolean doNotFSearchWhenOpenOtherScreen,
            boolean acceptEmpty,
            boolean handPriority,
            boolean offHandPriority) {
        PlayerInventory pinv = mc.player.getInventory();
        ItemStack item = mc.player.getStackInHand(Hand.MAIN_HAND);
        // we assert player hold block while scaffold, or it will be really annoying
        // the holding block must be a full cube
        int selecedSlot = pinv.selectedSlot;
        IndexEntry<ItemStack> result = null;
        IndexEntry<ItemStack> test;
        if ((acceptEmpty || !item.isEmpty()) && predicate.test((test = new IndexEntry<>(selecedSlot, item)))) {
            result = test;
        }
        if (handPriority && result != null) {
            return result;
        }
        if (result == null && offHandPriority) {
            item = mc.player.getStackInHand(Hand.OFF_HAND);
            if ((acceptEmpty || !item.isEmpty()) && predicate.test((test = new IndexEntry<>(40, item)))) {
                result = test;
            }
            if (result != null) {
                return result;
            }
        }
        // while player is open Screen
        if (doNotFSearchWhenOpenOtherScreen
                && ClientPlayerAccess.of(mc.player).getServerScreenHandler().syncId
                        != mc.player.playerScreenHandler.syncId) {
            return result;
        }
        for (var i = 0; i < pinv.size(); ++i) {
            ItemStack stack = pinv.getStack(i);
            test = new IndexEntry<>(i, stack);
            if ((acceptEmpty || !stack.isEmpty()) && predicate.test(test)) {
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

    public static IndexEntry<ItemStack> findPlayerHotBarItem(
            Predicate<ItemStack> predicate, boolean acceptEmpty, boolean acceptOffhand) {
        PlayerInventory pinv = mc.player.getInventory();
        ItemStack item = mc.player.getStackInHand(Hand.MAIN_HAND);
        // we assert player hold block while scaffold, or it will be really annoying
        // the holding block must be a full cube
        int selecedSlot = pinv.selectedSlot;
        if ((acceptEmpty || !item.isEmpty()) && predicate.test(item)) {
            return new IndexEntry<>(selecedSlot, item);
        }
        if (acceptOffhand) {
            item = mc.player.getStackInHand(Hand.OFF_HAND);
            if ((acceptEmpty || !item.isEmpty()) && predicate.test(item)) {
                return new IndexEntry<>(40, item);
            }
        }

        for (var i = 0; i < 9; ++i) {
            ItemStack stack = pinv.getStack(i);
            if (i == selecedSlot) continue;
            if ((acceptEmpty || !stack.isEmpty()) && predicate.test(stack)) {
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

    public static IndexEntry<ItemStack> findBestPlayerItem(
            Function<ItemStack, Double> maxFunction, boolean doNotFSearchWhenOpenOtherScreen, boolean acceptEmpty) {
        return findBestPlayerInventory(
                s -> {
                    return maxFunction.apply(s.val());
                },
                doNotFSearchWhenOpenOtherScreen,
                acceptEmpty);
    }

    public static IndexEntry<ItemStack> findBestPlayerInventory(
            Function<IndexEntry<ItemStack>, Double> maxFunction,
            boolean doNotFSearchWhenOpenOtherScreen,
            boolean acceptEmpty) {
        // while player is open Screen
        PlayerInventory pinv = mc.player.getInventory();
        ItemStack item = mc.player.getStackInHand(Hand.MAIN_HAND);
        // we assert player hold block while scaffold, or it will be really annoying
        // the holding block must be a full cube
        int selecedSlot = pinv.selectedSlot;
        Double maxValue = null;
        IndexEntry<ItemStack> result = null;
        IndexEntry<ItemStack> test = null;
        if ((acceptEmpty || !item.isEmpty())) {
            test = new IndexEntry<>(selecedSlot, item);
            maxValue = maxFunction.apply(test);
            if (maxValue != null) {
                result = test;
            }
        }
        if (doNotFSearchWhenOpenOtherScreen
                && ClientPlayerAccess.of(mc.player).getServerScreenHandler().syncId
                        != mc.player.playerScreenHandler.syncId) {
            return result;
        }

        Double currentValue;
        for (var i = 0; i < pinv.size(); ++i) {
            ItemStack stack = pinv.getStack(i);
            test = new IndexEntry<>(i, stack);
            if ((acceptEmpty || !stack.isEmpty()) && (currentValue = maxFunction.apply(test)) != null) {
                if (maxValue == null || currentValue > maxValue) {
                    maxValue = currentValue;
                    result = test;
                }
            }
        }
        return result;
    }

    public static IndexEntry<Slot> findScreenSlot(List<Slot> slots, Predicate<Slot> predicate, boolean acceptEmpty) {
        for (var i = 0; i < slots.size(); ++i) {
            var slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (!acceptEmpty && stack.isEmpty()) continue;
            if (predicate.test(slot)) {
                return new IndexEntry<>(i, slot);
            }
        }
        return null;
    }

    public static IndexEntry<Slot> findBestScreenSlot(
            List<Slot> slots, Function<Slot, Double> maxFunction, boolean acceptEmpty) {
        IndexEntry<Slot> result = null;
        Double maxVal = null;
        for (var i = 0; i < slots.size(); ++i) {
            var slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (!acceptEmpty && stack.isEmpty()) continue;
            Double val = maxFunction.apply(slot);
            if (val != null) {
                if (maxVal == null || maxVal < val) {
                    maxVal = val;
                    result = new IndexEntry<>(i, slot);
                }
            }
        }
        return result;
    }

    public static IndexEntry<Slot> findScreenItem(
            List<Slot> slots, Predicate<ItemStack> predicate, boolean acceptEmpty) {
        for (var i = 0; i < slots.size(); ++i) {
            var slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (!acceptEmpty && stack.isEmpty()) continue;
            if (predicate.test(stack)) {
                return new IndexEntry<>(i, slot);
            }
        }
        return null;
    }

    public static double computeInventory(Function<ItemStack, Double> maxFunction, boolean acceptEmpty) {
        double sum = 0.0D;
        Double currentValue;
        PlayerInventory pinv = mc.player.getInventory();
        for (var i = 0; i < pinv.size(); ++i) {
            ItemStack stack = pinv.getStack(i);
            if ((acceptEmpty || !stack.isEmpty()) && (currentValue = maxFunction.apply(stack)) != null) {
                sum += currentValue;
            }
        }
        return sum;
    }

    public static int getSelectedSlot() {
        return mc.player.getInventory().selectedSlot;
    }

    @Nonnull
    public static IndexEntry<ItemStack> getSelectedItem() {
        int idx = InventoryUtils.getSelectedSlot();
        return new IndexEntry<>(idx, mc.player.getInventory().main.get(idx));
    }
}
