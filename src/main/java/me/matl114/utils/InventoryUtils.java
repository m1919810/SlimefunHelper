package me.matl114.utils;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.hacks.InvTasks;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.inventory.*;
import me.matl114.versioned.api.VItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Hand;
import net.minecraft.util.dynamic.Codecs;

@ApiMethod
public class InventoryUtils {
    public static Inventory createReadOnlyOneItemInventory(Supplier<ItemStack> itemStackSupplier) {
        return new ImmutableInventory() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public ItemStack getStack(int slot) {
                return itemStackSupplier.get();
            }
        };
    }

    public static final Codec<IndexEntry<ItemStack>> STACK_WITH_SLOT_CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(
                        Codecs.UNSIGNED_BYTE.fieldOf("Slot").orElse(0).forGetter(IndexEntry::index),
                        VItem.ITEM_STACK_MAP_CODEC.forGetter(IndexEntry::val))
                .apply(instance, IndexEntry::new);
    });

    public static final Codec<IndexEntry<NbtCompound>> NBT_STACK_WITH_SLOT_CODEC = NbtCompound.CODEC.comapFlatMap(
            s -> {
                if (!s.contains("id")) {
                    return DataResult.error(() -> "Can not find field \"id\"");
                }
                if (s.get("Slot") instanceof AbstractNbtNumber number) {
                    NbtCompound nbt2 = new NbtCompound(new HashMap<>(s.entries));
                    nbt2.remove("Slot");
                    return DataResult.success(new IndexEntry<>(number.intValue(), nbt2));
                } else {
                    return DataResult.error(() -> "Can not find field \"Slot\"");
                }
            },
            s -> {
                NbtCompound compound = new NbtCompound(new HashMap<>(s.val().entries));
                compound.putByte("Slot", (byte) s.index());
                return compound;
            });

    public static Inventory createReadOnlyInventory(List<ItemStack> itemStackSupplier) {
        return new ImmutableListInventory(itemStackSupplier);
    }

    public static Inventory createInventory(List<ItemStack> itemStackSupplier) {
        return createInventory(itemStackSupplier.size(), itemStackSupplier);
    }

    public static Inventory createInventory(int size, List<ItemStack> itemStackSupplier) {
        return new MutableInventory(size, itemStackSupplier);
    }

    public static Inventory createInventory(ItemStack[] array) {
        return new MutableArrayInventory(array);
    }

    public static Inventory createSubInventoryView(Inventory view, int from, int to) {

        return new ImmutableInventory() {
            @Override
            public int size() {
                return Math.min(view.size(), to) - Math.min(view.size(), from);
            }

            @Override
            public ItemStack getStack(int slot) {
                return view.getStack(slot + from);
            }
        };
    }

    public static Stream<ItemStack> streamInventory(Inventory inv) {
        return IntStream.range(0, inv instanceof PlayerInventory pinv ? getPlayerInvSize() : inv.size())
                .mapToObj(inv::getStack);
    }

    public static Inventory getTopInventory(HandledScreen<?> screen) {
        if (screen instanceof GenericContainerScreen generic) {
            return generic.getScreenHandler().getInventory();
        } else {
            List<Slot> slots = screen.getScreenHandler().slots;
            int index = 0;
            for (var i = 0; i < slots.size(); i++) {
                if (slots.get(i).inventory instanceof PlayerInventory pinv) {
                    index = i;
                    break;
                }
            }
            return new SlotInventory(slots.subList(0, index));
        }
    }

    public static Inventory getTopInventory(ScreenHandler screen) {
        if (screen instanceof GenericContainerScreenHandler generic) {
            return generic.getInventory();
        } else {
            List<Slot> slots = screen.slots;
            int index = 0;
            for (var i = 0; i < slots.size(); i++) {
                if (slots.get(i).inventory instanceof PlayerInventory pinv) {
                    index = i;
                    break;
                }
            }
            return new SlotInventory(slots.subList(0, index));
        }
    }

    public static Inventory getBottomInventory(HandledScreen<?> screen) {
        List<Slot> slots = screen.getScreenHandler().slots;
        int index = 0;
        for (var i = 0; i < slots.size(); i++) {
            if (slots.get(i).inventory instanceof PlayerInventory pinv) {
                index = i;
                break;
            }
        }
        return new SlotInventory(slots.subList(index, slots.size()));
    }

    public static Inventory getBottomInventory(ScreenHandler handler) {
        List<Slot> slots = handler.slots;
        int index = 0;
        for (var i = 0; i < slots.size(); i++) {
            if (slots.get(i).inventory instanceof PlayerInventory pinv) {
                index = i;
                break;
            }
        }
        return new SlotInventory(slots.subList(index, slots.size()));
    }

    public static List<IndexEntry<ItemStack>> getInventoryEntries(Inventory inv) {
        List<IndexEntry<ItemStack>> entries = new ArrayList<>();
        for (var re = 0; re < inv.size(); ++re) {
            ItemStack stack = inv.getStack(re);
            if (!stack.isEmpty()) {
                entries.add(new IndexEntry<>(re, stack));
            }
        }
        return entries;
    }

    public static List<ItemStack> getContainerInventory(ContainerComponent container) {
        return container.stream().toList();
    }

    public static List<ItemStack> getContainerFromItem(ItemStack itemStack) {
        if (ItemStackUtils.hasInPatch(itemStack, DataComponentTypes.CONTAINER)) {
            ContainerComponent component = ItemStackUtils.getInPatch(itemStack, DataComponentTypes.CONTAINER);
            if (component != null) {
                return getContainerInventory(component);
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
            Predicate<IndexEntry<ItemStack>> predicate, boolean doNotFSearchWhenOpenOtherScreen, boolean acceptEmpty) {
        return findPlayerInventory(predicate, doNotFSearchWhenOpenOtherScreen, acceptEmpty, true, false);
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
        int selecedSlot = pinv.getSelectedSlot();
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
        for (var i = 0; i < getPlayerInvSize(); ++i) {
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
        int selecedSlot = pinv.getSelectedSlot();
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
        int selecedSlot = pinv.getSelectedSlot();
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
        for (var i = 0; i < getPlayerInvSize(); ++i) {
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

    public static IndexEntry<Slot> findPlayerBackpackItem(
            Predicate<ItemStack> predicate, boolean acceptEmpty, boolean includeCraft) {
        return findPlayerBackpackSlot((slot) -> predicate.test(slot.getStack()), acceptEmpty, includeCraft);
    }

    public static IndexEntry<Slot> findPlayerBackpackSlot(
            Predicate<Slot> predicate, boolean acceptEmpty, boolean includeCraft) {
        var handler = mc.player.playerScreenHandler;
        var serverHandler = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
        if (serverHandler.syncId == handler.syncId) {
            if (includeCraft) {
                for (var i = 1; i < 5; ++i) {
                    var slot = handler.slots.get(i);
                    if (!acceptEmpty && slot.getStack().isEmpty()) continue;
                    if (predicate.test(slot)) {
                        return new IndexEntry<>(i, slot);
                    }
                }
            }
            var pinv = mc.player.getInventory();
            for (var i = 0; i < getPlayerInvSize(); ++i) {
                int slotIndex = InvTasks.getScreenSlotByInventoryIndex(i);
                var slot = handler.slots.get(slotIndex);
                if (!acceptEmpty && slot.getStack().isEmpty()) continue;
                if (predicate.test(slot)) {
                    return new IndexEntry<>(slotIndex, slot);
                }
            }
            return null;
        } else {
            return null;
        }
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

    public static int computePlayerInventory(Item maxFunction) {
        return (int) computePlayerInventory(
                (stack) -> {
                    if (stack.isOf(maxFunction)) {
                        return (double) stack.getCount();
                    } else {
                        return null;
                    }
                },
                false);
    }

    public static double computePlayerInventory(Function<ItemStack, Double> maxFunction, boolean acceptEmpty) {
        double sum = 0.0D;
        Double currentValue;
        PlayerInventory pinv = mc.player.getInventory();
        for (var i = 0; i < getPlayerInvSize(); ++i) {
            ItemStack stack = pinv.getStack(i);
            if ((acceptEmpty || !stack.isEmpty()) && (currentValue = maxFunction.apply(stack)) != null) {
                sum += currentValue;
            }
        }
        return sum;
    }

    public static int getPlayerBackpackSize() {
        return 36;
    }

    public static int getPlayerInvSize() {
        // 傻逼mojang你给玩家放特么的saddle槽位干什么
        return 41;
    }

    public static IndexEntry<ItemStack> findItem(Inventory inventory, Item predicate) {
        return findItem(inventory, (v) -> v.isOf(predicate), predicate == Items.AIR);
    }

    public static IndexEntry<ItemStack> findItem(
            Inventory inventory, Predicate<ItemStack> predicate, boolean acceptEmpty) {
        for (var i = 0; i < inventory.size(); ++i) {
            if (!acceptEmpty && inventory.getStack(i).isEmpty()) continue;
            if (predicate.test(inventory.getStack(i))) {
                return new IndexEntry<>(i, inventory.getStack(i));
            }
        }
        return null;
    }

    public static IndexEntry<ItemStack> findInventory(
            Inventory inventory, Predicate<IndexEntry<ItemStack>> predicate, boolean acceptEmpty) {
        IndexEntry<ItemStack> result;
        for (var i = 0; i < inventory.size(); ++i) {
            if (!acceptEmpty && inventory.getStack(i).isEmpty()) continue;
            if (predicate.test(result = new IndexEntry<>(i, inventory.getStack(i)))) {
                return result;
            }
        }
        return null;
    }

    public static IndexEntry<ItemStack> findBestItem(
            Inventory inventory, Function<ItemStack, Double> predicate, boolean acceptEmpty) {
        return findBestInventory(inventory, (v) -> predicate.apply(v.val()), acceptEmpty);
    }

    public static IndexEntry<ItemStack> findBestInventory(
            Inventory inventory, Function<IndexEntry<ItemStack>, Double> predicate, boolean acceptEmpty) {
        IndexEntry<ItemStack> maxResult = null;

        Double maxValue = null;
        for (var i = 0; i < inventory.size(); ++i) {
            if (!acceptEmpty && inventory.getStack(i).isEmpty()) continue;
            IndexEntry<ItemStack> result = new IndexEntry<>(i, inventory.getStack(i));
            Double value = predicate.apply(result);
            if (value == null) {
                continue;
            } else if (maxValue == null || maxValue < value) {
                maxValue = value;
                maxResult = result;
            }
        }
        return maxResult;
    }

    public static int getSelectedSlot() {
        return mc.player.getInventory().getSelectedSlot();
    }

    @Nonnull
    public static IndexEntry<ItemStack> getSelectedItem() {
        return new IndexEntry<>(
                InventoryUtils.getSelectedSlot(), mc.player.getInventory().getSelectedStack());
    }

    public static Map<ItemStackSample, IntList> collectItemIndexes(Iterable<ItemStack> stacks) {
        Map<ItemStackSample, IntList> indexMap = new LinkedHashMap<>();
        int idx = 0;
        for (ItemStack stack : stacks) {
            indexMap.computeIfAbsent(ItemStackSample.of(stack), (i) -> new IntArrayList())
                    .add(idx);
            idx += 1;
        }
        return indexMap;
    }
}
