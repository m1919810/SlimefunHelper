package me.matl114.hacks;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import lombok.Getter;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.TileInventoryScreen;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.gui.basic.SlotElement;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.inv.*;
import me.matl114.hacks.utils.ItemCache;
import me.matl114.managers.*;
import me.matl114.utils.*;
import me.matl114.utils.inventory.ItemStackSample;
import me.matl114.utils.itemdb.ItemStackData;
import me.matl114.utils.itemdb.ItemStackDataWithAmount;
import me.matl114.utils.tasks.LimitedSpeedExecutor;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

public class InvTasks {
    public static void init() {}

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @ApiMethod
    public static Screen getCurrentServerScreen(PlayerEntity player) {
        if (player == null) {
            return null;
        }
        Screen nowScreen = null;
        if (player instanceof ClientPlayerAccess access) {
            nowScreen = access.getServerOpeningScreen();
        } else {
            nowScreen = MinecraftClient.getInstance().currentScreen;
        }
        // ignore Creative screen as it is not handled by server
        if (nowScreen instanceof CreativeInventoryScreen) {
            return null;
        }
        return nowScreen;
    }

    @ApiMethod
    public static boolean dropAllCursorStack() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return false;

        ScreenHandler handler = ClientPlayerAccess.of(player).getServerScreenHandler();
        if (handler.getCursorStack() != null && !handler.getCursorStack().isEmpty()) {
            ItemStack cleanedCursor = ItemStackUtils.getCleanedItem(handler.getCursorStack(), false, false);
            InvTasks.getClickExecutor().execute(() -> {
                MinecraftClient.getInstance()
                        .interactionManager
                        .clickSlot(handler.syncId, -999, 0, SlotActionType.PICKUP, player);
            });
            for (int i = 0; i < handler.slots.size(); i++) {
                ItemStack slot = handler.getSlot(i).getStack();
                if (!ItemStack.areItemsEqual(slot, cleanedCursor)) {
                    continue;
                }
                ItemStack cleaned = ItemStackUtils.getCleanedItem(slot, false, false);
                if (ItemStack.areItemsAndComponentsEqual(cleaned, cleanedCursor)) {
                    final int index = i;
                    InvTasks.getClickExecutor().execute(() -> {
                        MinecraftClient.getInstance()
                                .interactionManager
                                .clickSlot(handler.syncId, index, 1, SlotActionType.THROW, player);
                    });
                }
            }
            return true;
        }

        return false;
    }

    @ApiMethod
    public static void takeAllContainerItem() {
        Screen nowScreen = getCurrentServerScreen(mc.player);
        if (nowScreen instanceof HandledScreen<?> handled) {
            ScreenHandler handler = handled.getScreenHandler();
            for (int i = 0; i < handler.slots.size(); i++) {
                Slot slot = handler.getSlot(i);
                if (!(slot.inventory instanceof PlayerInventory)) {
                    final int index = i;
                    clickExecutor.execute(() -> {
                        mc.interactionManager.clickSlot(handler.syncId, index, 1, SlotActionType.QUICK_MOVE, mc.player);
                    });
                }
            }
        }
    }

    @ApiMethod
    public static void saveAllPlayerItem() {
        Screen nowScreen = getCurrentServerScreen(mc.player);
        if (nowScreen instanceof HandledScreen<?> handled) {
            ScreenHandler handler = handled.getScreenHandler();
            for (int i = 0; i < handler.slots.size(); i++) {
                Slot slot = handler.getSlot(i);
                if (slot.inventory instanceof PlayerInventory) {
                    final int index = i;
                    clickExecutor.execute(() -> {
                        mc.interactionManager.clickSlot(handler.syncId, index, 1, SlotActionType.QUICK_MOVE, mc.player);
                    });
                }
            }
        }
    }

    @ApiMethod
    public static boolean isHandledScreen(Screen screen) {
        return screen instanceof HandledScreen;
    }

    @ApiMethod
    public static boolean quickMoveSlotItem(HandledScreen screen, int slotIndex) {
        Slot slot = screen.getScreenHandler().getSlot(slotIndex);
        if (slot != null) {
            return quickMoveSlotItem(screen, slot);
        }
        return false;
    }

    @ApiMethod
    public static boolean quickMoveSlotItem(HandledScreen screen, Slot slot) {
        if (slot != null) {
            ScreenHandler handler = screen.getScreenHandler();
            ItemStack cleanedStack = ItemStackUtils.getCleanedItem(slot.getStack(), false, false);
            boolean isPlayerInventory = slot.inventory instanceof PlayerInventory;

            for (int i = 0; i < handler.slots.size(); i++) {
                Slot slot2 = handler.getSlot(i);
                if (((slot2.inventory instanceof PlayerInventory) == isPlayerInventory)
                        && ItemStack.areItemsEqual(cleanedStack, slot2.getStack())
                        && ItemStack.areItemsAndComponentsEqual(
                                cleanedStack, ItemStackUtils.getCleanedItem(slot2.getStack(), false, false))) {
                    quickMoveSlot(screen, i);
                }
            }
            return true;
        }
        return false;
    }

    @ApiMethod
    public static void quickMoveSlot(HandledScreen handler, int index, boolean ignoreConfig) {
        quickMoveSlot(handler.getScreenHandler(), index, ignoreConfig);
    }

    @ApiMethod
    public static void quickMoveSlot(HandledScreen handler, int index) {
        quickMoveSlot(handler.getScreenHandler(), index, false);
    }

    public static void quickMoveSlot(ScreenHandler handler, int index, boolean ignoreConfig) {
        if (!ignoreConfig
                && handler.getSlot(index).getStack().getCount() > 1
                && getFastInv().enableLeftOne.get()) {
            //            Debug.info("quick move 1");
            int syncId = handler.syncId;
            if (!handler.getCursorStack().isEmpty()) {
                Debug.chat(Text.literal("[left 1] ")
                        .formatted(Formatting.RED)
                        .append(Text.literal("cursor stack needs to be empty to apply left-one quickMove")));
                return;
            }
            Slot slot = handler.getSlot(index);
            if (slot.getStack().isEmpty()) {
                return;
            }
            boolean tryTake = slot.inventory instanceof PlayerInventory;
            boolean hasPlace = false;
            for (Slot s : handler.slots) {
                // skip same-side inventory
                if ((s.inventory instanceof PlayerInventory) == tryTake) {
                    continue;
                }
                if (s.getStack().isEmpty()
                        || (s.getStack().getCount() < s.getStack().getMaxCount()
                                && ItemStack.areItemsAndComponentsEqual(slot.getStack(), s.getStack()))) {
                    hasPlace = true;
                    break;
                }
            }
            // minimize packet amount sent
            if (!hasPlace) {
                return;
            }
            mc.interactionManager.clickSlot(syncId, index, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, index, 1, SlotActionType.PICKUP, mc.player);
            ItemStack sample = handler.getCursorStack();
            if (sample.isEmpty()) {
                return;
            }
            // save sample
            sample = sample.copy();
            for (int i = 0; i < handler.slots.size(); ++i) {
                Slot s = handler.slots.get(i);
                if ((s.inventory instanceof PlayerInventory) == tryTake) {
                    continue;
                }
                if (s.getStack().isEmpty()
                        || (s.getStack().getCount() < s.getStack().getMaxCount()
                                && ItemStack.areItemsAndComponentsEqual(slot.getStack(), s.getStack()))) {
                    mc.interactionManager.clickSlot(syncId, i, 0, SlotActionType.PICKUP, mc.player);
                    if (handler.getCursorStack().isEmpty()) {
                        return;
                    }
                }
            }
            if (!handler.getCursorStack().isEmpty()) {
                mc.interactionManager.clickSlot(syncId, index, 0, SlotActionType.PICKUP, mc.player);
            }
            //            while (handler.getSlot(index).getStack().getCount() > 1){
            //                mc.interactionManager.clickSlot(syncId, index, 1,SlotActionType.PICKUP,mc.player);
            //                mc.interactionManager.clickSlot(syncId, index, 0, SlotActionType.QUICK_MOVE, mc.player);
            //                mc.interactionManager.clickSlot(syncId, index, 0,);
            //            }
            //                mc.interactionManager.clickSlot(syncId, index, 1,SlotActionType.PICKUP,mc.player);
            //                int maxTry = 33;
            //                while (handler.getCursorStack().getCount() > 1){
            //                    mc.interactionManager.clickSlot(syncId, index, 1, SlotActionType.PICKUP, mc.player);
            //                    if(-- maxTry <= 0){
            //                        break;
            //                    }
            //                }
            //                mc.interactionManager.clickSlot(syncId, index, 0, SlotActionType.QUICK_MOVE, mc.player);
            //                mc.interactionManager.clickSlot(syncId, index, 1,SlotActionType.PICKUP,mc.player);

        } else {
            if (handler.getCursorStack().isEmpty()) {
                mc.interactionManager.clickSlot(handler.syncId, -999, 0, SlotActionType.PICKUP, mc.player);
            }
            mc.interactionManager.clickSlot(handler.syncId, index, 0, SlotActionType.QUICK_MOVE, mc.player);
        }
    }

    @ApiMethod
    public static boolean quickDropSlotItem(HandledScreen handler, int index) {
        Slot slot = handler.getScreenHandler().getSlot(index);
        if (slot != null) {
            return quickDropSlotItem(handler, slot);
        }
        return false;
    }

    public static boolean quickDropSlotItem(HandledScreen handled, Slot slot) {
        if (slot != null && slot.getStack() != null && slot.getStack().getItem() != Items.AIR) {
            ItemStack cleanedStack = ItemStackUtils.getCleanedItem(slot.getStack(), false, false);
            ScreenHandler handler = handled.getScreenHandler();
            for (int i = 0; i < handler.slots.size(); i++) {
                Slot slot2 = handler.getSlot(i);
                if (ItemStack.areItemsEqual(cleanedStack, slot2.getStack())
                        && ItemStack.areItemsAndComponentsEqual(
                                cleanedStack, ItemStackUtils.getCleanedItem(slot2.getStack(), false, false))) {
                    final int index = i;
                    clickExecutor.execute(() -> {
                        mc.interactionManager.clickSlot(handler.syncId, index, 1, SlotActionType.THROW, mc.player);
                    });
                }
            }
            return true;
        }
        return false;
    }

    public static void setCreativeInventory(ItemStack itemStack, int slot) {
        if (slot < 36) {
            mc.player.getInventory().setStack(slot, itemStack.copy());
            mc.interactionManager.clickCreativeStack(itemStack, INVENTORY_INDEX_TO_SCREEN_SLOT[slot]);
        }
    }

    @ApiMethod
    public static ItemStack getHotbarStack(int hotbar) {
        return hotbar == 40
                ? mc.player.getInventory().offHand.get(0)
                : mc.player.getInventory().main.get(hotbar);
    }

    @ApiMethod
    public static int getTopInventorySize() {
        if (mc.currentScreen instanceof HandledScreen handledScreen && handledScreen.getScreenHandler() != null) {
            int idx = 0;
            for (var slot : handledScreen.getScreenHandler().slots) {
                if (slot.inventory instanceof PlayerInventory) {
                    return idx;
                } else {
                    idx += 1;
                }
            }
            return idx;
        } else {
            return 0;
        }
    }
    // from hotbars, backpack contents.  equipments (feet to head 36-39), offhand 40, (craftingResult 41, craftingSlots
    // 42-45)
    private static final int[] INVENTORY_INDEX_TO_SCREEN_SLOT = new int[] {
        36, 37, 38, 39, 40, 41, 42, 43, 44, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27,
        28, 29, 30, 31, 32, 33, 34, 35, 8, 7, 6, 5, 45, 0, 1, 2, 3, 4
    };
    private static final int[] SCREEN_SLOT_TO_INVENTORY_INDEX = new int[46];

    static {
        for (var i = 0; i < INVENTORY_INDEX_TO_SCREEN_SLOT.length; ++i) {
            SCREEN_SLOT_TO_INVENTORY_INDEX[INVENTORY_INDEX_TO_SCREEN_SLOT[i]] = i;
        }
    }

    @ApiMethod
    public static int getScreenSlotByInventoryIndex(int v) {
        return INVENTORY_INDEX_TO_SCREEN_SLOT[v];
    }

    @ApiMethod
    public static int getInventoryIndexByScreenSlot(int v) {
        return SCREEN_SLOT_TO_INVENTORY_INDEX[v];
    }

    @ApiMethod
    public static void creativeGive(ItemStack itemStack, int count) {
        if (mc.player != null
                && mc.interactionManager != null
                && mc.interactionManager.getCurrentGameMode().isCreative()) {
            PlayerScreenHandler inventoryView = mc.player.playerScreenHandler;
            int stackMax = itemStack.getMaxCount();
            for (int i : INVENTORY_INDEX_TO_SCREEN_SLOT) {
                Slot slot0 = inventoryView.getSlot(i);
                int transfer = 0;
                ItemStack stackToSet = null;
                if (slot0.getStack().isEmpty()) {
                    transfer = Math.min(stackMax, count);
                    stackToSet = itemStack.copyWithCount(transfer);
                } else if (slot0.getStack().getCount() < stackMax
                        && ItemStack.areItemsAndComponentsEqual(slot0.getStack(), itemStack)) {
                    transfer = Math.min(stackMax - slot0.getStack().getCount(), count);
                    stackToSet = itemStack.copyWithCount(slot0.getStack().getCount() + transfer);
                }
                count -= transfer;
                if (stackToSet != null) {
                    mc.interactionManager.clickCreativeStack(stackToSet, i);
                    slot0.setStack(stackToSet);
                }
                if (count <= 0) return;
            }
            ItemStack sample = itemStack.copy();
            while (count > 0) {
                int transfer = Math.min(stackMax, count);
                count -= transfer;
                sample.setCount(transfer);
                mc.interactionManager.dropCreativeStack(sample);
            }
        }
    }

    @ApiMethod
    public static void creativeAddItem(ItemStack itemStack, int count) {
        if (mc.player != null
                && mc.interactionManager != null
                && mc.interactionManager.getCurrentGameMode().isCreative()) {
            int slot = -1;

            PlayerScreenHandler inventoryView = mc.player.playerScreenHandler;
            int stackMax = itemStack.getMaxCount();
            int countA = count;
            for (int i : INVENTORY_INDEX_TO_SCREEN_SLOT) {
                Slot slot0 = inventoryView.getSlot(i);
                if (slot0.getStack().isEmpty()) {
                    slot = i;
                    break;
                } else if (slot0.getStack().getCount() < stackMax
                        && ItemStack.areItemsAndComponentsEqual(slot0.getStack(), itemStack)) {
                    slot = i;
                    countA = count + slot0.getStack().getCount();
                    break;
                }
            }
            ItemStack stackToGive = itemStack.copyWithCount(Math.min(stackMax, countA));
            if (slot != -1) {
                inventoryView.getSlot(slot).setStack(stackToGive);
                mc.interactionManager.clickCreativeStack(stackToGive, slot);
            } else {
                mc.interactionManager.dropCreativeStack(stackToGive);
            }
        }
    }

    @ApiMethod
    public static void creativeDrop(ItemStack itemStack, int count) {
        if (itemStack.isEmpty()) return;
        mc.interactionManager.dropCreativeStack(itemStack.copyWithCount(count));
    }

    @ApiMethod
    public static void copyGiveCommand(ItemStack itemStack) {

        mc.keyboard.setClipboard(createGiveCommand(itemStack.copyWithCount(itemStack.getMaxCount())));
    }

    @ApiMethod
    public static String createGiveCommand(ItemStack itemStack) {
        if (itemStack.isEmpty()) return "";
        StringBuilder builder = new StringBuilder("/minecraft:give @s ");
        builder.append(Registries.ITEM.getId(itemStack.getItem()));
        if (ItemStackUtils.hasInPatch(itemStack)) {
            builder.append('[');
            Map<ComponentType, Optional> map = new HashMap<>(itemStack.components.changedComponents);
            int index = 0;
            for (var entry : map.entrySet()) {
                if (index > 0) {
                    builder.append(',');
                }
                Identifier identifier = Registries.DATA_COMPONENT_TYPE.getId(entry.getKey());
                if (identifier != null) {
                    String cmp = identifier.toString();
                    Optional val = entry.getValue();
                    if (val.isPresent()) {
                        try {
                            String nbtSeri = entry.getKey()
                                    .getCodecOrThrow()
                                    .encodeStart(ItemStackUtils.registry().getOps(NbtOps.INSTANCE), val.get())
                                    .getOrThrow()
                                    .toString();
                            builder.append(cmp).append('=').append(nbtSeri);
                            index++;
                        } catch (Throwable e) {
                            // exception, skip
                            Debug.chat(e.getMessage());
                        }
                    } else {
                        builder.append('!').append(cmp);
                        index++;
                    }
                }
            }
            builder.append(']');
        }
        builder.append(" ").append(itemStack.getCount());
        return builder.toString();
    }

    @ApiMethod
    public static boolean isScreenHandlerValid(ScreenHandler handler) {
        return mc.player != null && mc.player.currentScreenHandler == handler;
    }

    @ApiMethod
    public static void quickMoveSlotOrDrop(ScreenHandler handler, int slot) {
        if (!isScreenHandlerValid(handler)) return;
        if (!handler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(handler.syncId, -999, 0, SlotActionType.PICKUP, mc.player);
        }
        quickMoveSlot(handler, slot, true);
        if (!handler.getSlot(slot).getStack().isEmpty()) {
            mc.interactionManager.clickSlot(handler.syncId, slot, 1, SlotActionType.THROW, mc.player);
        }
    }

    @Getter
    public static class SlotMatchingResult {
        public ItemStack sample;
        public int count;
        public IntList slots;

        public SlotMatchingResult() {
            count = 0;
            slots = new IntArrayList();
            this.sample = null;
        }

        public void setItemSample(ItemStack stack) {
            this.sample = stack.copy();
        }

        public void addMatchingSlot(int idx, Slot slot) {
            slots.add(idx);
            count += slot.getStack().getCount();
        }

        public int[] toIntArray() {
            return slots.toIntArray();
        }
    }

    @ApiMethod
    public static SlotMatchingResult allSlotMatch(HandledScreen handledScreen) {
        var result = new SlotMatchingResult();
        int[] array = IntStream.range(0, handledScreen.getScreenHandler().slots.size())
                .toArray();
        result.slots = new IntArrayList(array);
        return result;
    }

    @ApiMethod
    public static SlotMatchingResult getContainerSlots(HandledScreen handledScreen) {
        var result = new SlotMatchingResult();
        var allSlots = handledScreen.getScreenHandler().slots;
        int size = allSlots.size();
        for (int i = 0; i < size; ++i) {
            Slot slot = allSlots.get(i);
            if (slot != null && !(slot.inventory instanceof PlayerInventory)) {
                // all match
                result.slots.add(i);
            }
        }
        return result;
    }

    @ApiMethod
    public static SlotMatchingResult getPlayerInventorySlots(ScreenHandler handledScreen) {
        var result = new SlotMatchingResult();
        var allSlots = handledScreen.slots;
        int size = allSlots.size();
        for (int i = 0; i < size; ++i) {
            Slot slot = allSlots.get(i);
            if (slot != null && slot.inventory instanceof PlayerInventory) {
                // all match
                result.slots.add(i);
            }
        }
        return result;
    }

    @ApiMethod
    public static SlotMatchingResult getEmptySlots(ScreenHandler handledScreen, int... slots) {
        var result = new SlotMatchingResult();
        var allSlots = handledScreen.slots;
        for (int i : slots) {
            Slot slot = allSlots.get(i);
            if (slot != null && slot.getStack().isEmpty()) {

                // all match
                result.slots.add(i);
            }
        }
        return result;
    }

    @ApiMethod
    public static SlotMatchingResult getItemStackMatchingSlot(ScreenHandler screen, ItemStack stack, int... list) {
        if (stack.isEmpty()) return getEmptySlots(screen, list);
        var result = new SlotMatchingResult();
        result.setItemSample(stack);
        var allSlots = screen.slots;
        for (int i : list) {
            Slot slot = allSlots.get(i);
            if (slot != null && !slot.getStack().isEmpty()) {

                if (ItemStack.areItemsAndComponentsEqual(slot.getStack(), stack)) {
                    // all match
                    result.addMatchingSlot(i, slot);
                }
            }
        }
        return result;
    }
    /**
     * move items that match itemStack <from the fromRange to the toRange> to the toSlot, try adding toAmountAdd count of itemStack
     * @param handledScreen
     * @param itemStack
     * @param toSlot
     * @param toAmountAdd
     * @param removeExist
     */
    @ApiMethod
    public static void moveStackToSlotRanged(
            ScreenHandler handledScreen,
            ItemStack itemStack,
            int toSlot,
            int toAmountAdd,
            boolean removeExist,
            int fromRange,
            int toRange) {
        moveStackToSlot(
                handledScreen,
                itemStack,
                toSlot,
                toAmountAdd,
                removeExist,
                IntStream.range(fromRange, toRange).toArray());
    }

    /**
     * move items that match itemStack from the trustedSlotIndexList to the toSlot, try adding toAmountAdd count of itemStack
     * @param handledScreen
     * @param itemStack
     * @param toSlot
     * @param toAmountAdd
     * @param removeExist
     * @param trustedSlotIndexList
     */
    @ApiMethod
    public static void moveStackToSlot(
            ScreenHandler handledScreen,
            ItemStack itemStack,
            int toSlot,
            int toAmountAdd,
            boolean removeExist,
            int... trustedSlotIndexList) {
        if (itemStack.isEmpty()) return;
        ScreenHandler handler = handledScreen;
        if (!isScreenHandlerValid(handler)) return;
        // 操作前先清空指针
        if (!handler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(handler.syncId, -999, 0, SlotActionType.PICKUP, mc.player);
        }
        ItemStack stackAt = handler.getSlot(toSlot).getStack();
        int toAmount = toAmountAdd;
        if (!stackAt.isEmpty()) {
            if (ItemStack.areItemsAndComponentsEqual(stackAt, itemStack)) {
                toAmount += stackAt.getCount();
            } else {
                // 不要动
                if (!removeExist) return;
                // remove stackAt
                mc.interactionManager.clickSlot(handler.syncId, toSlot, 1, SlotActionType.QUICK_MOVE, mc.player);
                // 不是哥们怎么没取完啊
                if (!handler.getSlot(toSlot).getStack().isEmpty()) {
                    // 看我给你丢出去
                    mc.interactionManager.clickSlot(handler.syncId, toSlot, 1, SlotActionType.THROW, mc.player);
                }
            }
        }
        // 填满一组不需要控制数量!
        if (toAmount >= itemStack.getMaxCount()) {
            toAmount = itemStack.getMaxCount();
            if (handler.getSlot(toSlot).getStack().getCount() >= toAmount) {
                return;
            }
            // 直接填满就行
            for (var i : trustedSlotIndexList) {
                if (!handler.getSlot(i).getStack().isEmpty()
                        && ItemStack.areItemsAndComponentsEqual(
                                handler.getSlot(i).getStack(), itemStack)) {
                    moveStackFromTo(handler, i, toSlot);
                    if (handler.getSlot(toSlot).getStack().getCount() >= toAmount) {
                        break;
                    }
                }
            }
        } else {
            // 考虑数量
            for (var i : trustedSlotIndexList) {
                if (!handler.getSlot(i).getStack().isEmpty()
                        && ItemStack.areItemsAndComponentsEqual(
                                handler.getSlot(i).getStack(), itemStack)) {
                    int currentAmount = handler.getSlot(toSlot).getStack().getCount();
                    //
                    if (currentAmount + handler.getSlot(i).getStack().getCount() > toAmount) {
                        // satisfy , use tasks to
                        moveStackFromToAmount(handler, i, toSlot, toAmount - currentAmount);
                        break;
                    } else {
                        moveStackFromTo(handler, i, toSlot);
                        if (handler.getSlot(toSlot).getStack().getCount() >= toAmount) {
                            break;
                        }
                    }
                }
            }
        }
    }

    @ApiMethod
    public static void moveRecipePatternToContainer(
            ScreenHandler screen, ItemStack[] ingredients, int[] slot, int patternAmount, boolean removeOrigin) {
        int[] playerInv = getPlayerInventorySlots(screen).toIntArray();
        moveRecipePatternToContainer(
                screen,
                ingredients,
                slot,
                patternAmount,
                removeOrigin,
                ((screen1, itemStack) -> getItemStackMatchingSlot(screen1, itemStack, playerInv)));
    }

    public static void moveRecipePatternToContainer(
            ScreenHandler screen,
            ItemStack[] ingredients,
            int[] slot,
            int patternAmount,
            boolean removeOrigin,
            BiFunction<ScreenHandler, ItemStack, SlotMatchingResult> slotMatchProvider) {
        int size = ingredients.length;
        Preconditions.checkArgument(slot.length == size);
        Map<ItemStackSample, IntList> stackRecipe = new HashMap<>();
        IntList emptySlots = new IntArrayList();
        for (int i = 0; i < size; ++i) {
            ItemStack item = ingredients[i];
            if (item != null && !item.isEmpty()) {
                ItemStackSample sample = new ItemStackSample(item);
                int index = i;
                stackRecipe.compute(sample, (key, list) -> {
                    if (list == null) {
                        list = new IntArrayList();
                    }
                    list.add(index);
                    return list;
                });
            } else {
                emptySlots.add(i);
            }
        }

        for (var mapEntry : stackRecipe.entrySet()) {
            ItemStackSample sample = mapEntry.getKey();
            //            String sampleId = getSfIdOrNull(sample.sample());
            var matchResult = slotMatchProvider.apply(screen, sample.sample());
            // getItemStackMatchingSlot(screen, sample.sample(), true, playerInventory);
            int counter = matchResult.count;
            int[] cachedSlots = matchResult.toIntArray();
            ItemStack realStack = matchResult.sample;

            //            int size = allSlots.size();
            //            for (int i=0; i< size; ++i){
            //                Slot slot = allSlots.get(i);
            //                if(slot != null && slot.inventory instanceof PlayerInventory && !slot.getStack().isEmpty()
            // ){
            //                    if(realStack != null){
            //                        if( ItemStack.areItemsAndComponentsEqual(slot.getStack(), realStack)){
            //                            //all match
            //                            cachedSlots.add(i);
            //                            counter += slot.getStack().getCount();
            //                        }
            //                    }else {
            //                        //the first match itemStack will be the realStack template
            //                        if(Objects.equals(sampleId,getSfIdOrNull(slot.getStack()) )){
            //                            realStack = slot.getStack();
            //                            cachedSlots.add(i);
            //                            counter += slot.getStack().getCount();
            //                        }
            //                    }
            //
            //                }
            //            }
            // nothing match this sample, , , counter must be 0, there is no meaning doing left
            if (realStack == null || counter == 0) {
                continue;
            }
            // copy stack to avoid modification
            // do not copy because it must be copied
            //            realStack = realStack.copy();
            int needed = 0;
            for (var i : mapEntry.getValue()) {
                needed += ingredients[i].getCount();
            }
            int maxSupply = Math.min(counter / needed, patternAmount);
            for (var i : mapEntry.getValue()) {
                int slotNeed = ingredients[i].getCount() * maxSupply;
                InvTasks.moveStackToSlot(screen, realStack, slot[i], slotNeed, removeOrigin, cachedSlots);
            }
        }
        for (var i : emptySlots) {
            InvTasks.quickMoveSlotOrDrop(screen, i);
        }
    }

    @ApiMethod
    public static void moveStackFromTo(ScreenHandler handler, int fromIndex, int toSlot) {
        mc.interactionManager.clickSlot(handler.syncId, fromIndex, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, toSlot, 0, SlotActionType.PICKUP, mc.player);
        if (!handler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(handler.syncId, fromIndex, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    @ApiMethod
    public static void moveStackFromToAmount(ScreenHandler handler, int fromIndex, int toSlot, int amount) {
        Slot currentFrom = handler.getSlot(fromIndex);
        Slot currentTo = handler.getSlot(toSlot);
        int currentFromAmount = currentFrom.getStack().getCount();

        // from 的数量完全不够
        if (currentFromAmount <= amount) {
            moveStackFromTo(handler, fromIndex, toSlot);
            return;
        } else {
            // from的数量超出了,我们只需要amount个
            int currentToAmount = currentTo.getStack().getCount();
            int max = currentFrom.getStack().getMaxCount();
            if (currentToAmount + amount >= max) {
                // 如果amount赛过去就满了《那和直接把from赛过去一样
                moveStackFromTo(handler, fromIndex, toSlot);
                return;
            } else {
                // amount < max - currentTo
                // currentFrom > amount
                for (int __ = 0; __ < 10; ++__) {
                    if (amount <= 0) {
                        return;
                    }
                    int halfTrans = (currentFromAmount + 1) / 2;
                    int distanceToHalf = Math.abs(halfTrans - amount);
                    int minDelta = Math.min(Math.min(amount, currentFromAmount - amount), distanceToHalf);
                    if (minDelta == amount) {
                        mc.interactionManager.clickSlot(handler.syncId, fromIndex, 0, SlotActionType.PICKUP, mc.player);
                        for (var i = 0; i < amount; ++i) {
                            mc.interactionManager.clickSlot(
                                    handler.syncId, toSlot, 1, SlotActionType.PICKUP, mc.player);
                        }
                        if (!handler.getCursorStack().isEmpty()) {
                            mc.interactionManager.clickSlot(
                                    handler.syncId, fromIndex, 0, SlotActionType.PICKUP, mc.player);
                        }
                        return;
                    } else if (minDelta == currentFromAmount - amount) {
                        mc.interactionManager.clickSlot(handler.syncId, fromIndex, 0, SlotActionType.PICKUP, mc.player);
                        for (int i = 0; i < minDelta; ++i) {
                            mc.interactionManager.clickSlot(
                                    handler.syncId, fromIndex, 1, SlotActionType.PICKUP, mc.player);
                        }
                        mc.interactionManager.clickSlot(handler.syncId, toSlot, 0, SlotActionType.PICKUP, mc.player);
                        return;
                    } else {
                        //
                        if (halfTrans <= amount) {
                            mc.interactionManager.clickSlot(
                                    handler.syncId, fromIndex, 1, SlotActionType.PICKUP, mc.player);
                            mc.interactionManager.clickSlot(
                                    handler.syncId, toSlot, 0, SlotActionType.PICKUP, mc.player);
                            // 通过计算currentTo增长了多少来更新amount
                            amount = amount - currentTo.getStack().getCount() + currentToAmount;
                            currentToAmount = currentTo.getStack().getCount();
                            currentFromAmount = currentFrom.getStack().getCount();
                            continue;
                        } else {
                            mc.interactionManager.clickSlot(
                                    handler.syncId, fromIndex, 1, SlotActionType.PICKUP, mc.player);
                            int trans = (currentFromAmount + 1) / 2 - amount;
                            for (int i = 0; i < trans; ++i) {
                                mc.interactionManager.clickSlot(
                                        handler.syncId, fromIndex, 1, SlotActionType.PICKUP, mc.player);
                            }
                            mc.interactionManager.clickSlot(
                                    handler.syncId, toSlot, 0, SlotActionType.PICKUP, mc.player);
                            return;
                        }
                    }
                }
                Debug.chat(Text.literal("Error while transfering itemStacks, which takes 10 more loop "));
            }
        }
    }

    @ApiMethod
    public List<HandledScreen<?>> getCachedInventories() {
        return InvTasks.getChestHistory().getCachedInventories();
    }

    @ApiMethod
    public static void openInventoryCacheScreen() {
        getChestHistory().openInventoryCacheScreen();
    }

    public static final ItemStack INV_ICON_UNKNOWN = new ItemStack(Items.BARRIER);
    private static final ItemStack INV_ICON_NO_ITEM = new ItemStack(Items.BEDROCK);

    public static ItemStack generateIconForScreen(HandledScreen<?> screen) {
        if (screen instanceof TileInventoryScreen tile && !tile.isVirtual()) {
            Block blockType = tile.getBlockType();
            if (blockType != null) {
                Item itemType = blockType.asItem();
                if (itemType != Items.AIR) {
                    return new ItemStack(itemType);
                }
            }
            return INV_ICON_NO_ITEM;
        }
        return INV_ICON_UNKNOWN;
    }
    // suppress random source use when dropItem
    public static final ThreadLocal<Boolean> SUPPRESS_DROPITEM_SPAWN = ThreadLocal.withInitial(() -> false);

    public static void clickSlotAsync(int slotId, int button, SlotActionType actionType) {
        if (mc.player == null) return;
        // handler or player inv

        ScreenHandler screenHandler = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
        ScreenHandler currentHandler = mc.player.currentScreenHandler;

        int syncId = screenHandler.syncId;
        // won't miss any inject
        boolean shouldReplace = syncId != currentHandler.syncId;
        try {
            if (shouldReplace) {
                // use fake screen handler
                mc.player.currentScreenHandler = screenHandler;
            }
            mc.interactionManager.clickSlot(syncId, slotId, button, actionType, mc.player);
        } finally {
            if (shouldReplace) {
                mc.player.currentScreenHandler = currentHandler;
            }
        }
        return;
    }

    // track tileEntity screen,
    private static BlockHitResult lastInteract = null;
    private static int lastInteractTimestamp = -1;

    private static void listenInteractBlockPacket(PlayerInteractBlockC2SPacket packet) {
        if (packet.getBlockHitResult().getType() == HitResult.Type.BLOCK) {
            lastInteract = packet.getBlockHitResult();
            lastInteractTimestamp = Tasks.getTick();
        }
    }

    public static BlockPos predictScreenFrom(Predicate<Block> targetBlock) {
        int timeStamp = Tasks.getTick();
        // 在一秒内反应的 可以考虑
        if (timeStamp < lastInteractTimestamp + 20 && lastInteract != null) {
            BlockPos hitPose = lastInteract.getBlockPos();
            if (hitPose != null
                    && targetBlock.test(mc.world.getBlockState(hitPose).getBlock())) {
                return hitPose;
            }
            // block Type not match,
        }
        return RaycastUtils.rayTraceSpecificBlock((b) -> b == Blocks.DISPENSER || b == Blocks.DROPPER)
                .orElse(null);
    }
    // track screen syncId
    public static int LAST_SYNC_ID = 0;
    // for screen desync fix
    public static final int MAX_DEQUE_SIZE = 8;
    public static Deque<ScreenHandler> historyScreens = new ArrayDeque<>();

    public static void onOpenScreen(Event<OpenScreenS2CPacket> packet) {
        LAST_SYNC_ID = packet.context.getSyncId();
    }

    public static void onOpenScreenCreate(Event<HandledScreen<?>> eventScreen) {
        if (eventScreen.context != null && eventScreen.context.getScreenHandler() != null) {
            historyScreens.addLast(eventScreen.context.getScreenHandler());
        }
        while (historyScreens.size() > MAX_DEQUE_SIZE) {
            historyScreens.removeFirst();
        }
    }

    public static void onInventoryOld(Event<ScreenHandlerSlotUpdateS2CPacket> invS2CPacket) {
        int syncId = invS2CPacket.context.getSyncId();
        if (mc.interactionManager.getCurrentGameMode().isSurvivalLike()
                && ClientPlayerAccess.of(mc.player).getServerScreenHandler().syncId != syncId
                && mc.player.currentScreenHandler.syncId != syncId
                && syncId != 0) {
            // maybe we click too fast that we miss something
            var pkt = invS2CPacket.context;
            for (var handler : historyScreens) {
                if (handler.syncId == syncId) {
                    handler.setStackInSlot(pkt.getSlot(), pkt.getRevision(), pkt.getStack());
                    return;
                }
            }
        }
    }

    public static void onInventoryOld2(Event<InventoryS2CPacket> eventInv) {
        int syncId = eventInv.context.getSyncId();
        if (mc.interactionManager.getCurrentGameMode().isSurvivalLike()
                && ClientPlayerAccess.of(mc.player).getServerScreenHandler().syncId != syncId
                && mc.player.currentScreenHandler.syncId != syncId
                && syncId != 0) {
            // maybe we click too fast that we miss something
            var pkt = eventInv.context;
            for (var handler : historyScreens) {
                if (handler.syncId == syncId) {
                    handler.updateSlotStacks(pkt.getRevision(), pkt.getContents(), pkt.getCursorStack());
                    return;
                }
            }
        }
    }

    public static void onGameJoin(Event<ClientPlayerEntity> gameJoin) {
        LAST_SYNC_ID = 0;
        historyScreens.clear();
    }

    public static void openEditorForPlayer() {
        if (mc.player != null) {
            getItemEditor().openEditorForPlayer(mc.player);
        }
    }

    @ApiMethod
    public static void openEditScreen(ItemStack item, Consumer<ItemStack> callback) {
        getItemEditor().openEditScreen(item, callback);
    }

    public static SlotElement.SlotClickCallback getRightClickOpenEditScreenCallback() {
        return (item, button) -> {
            if (button == 1) getItemEditor().openEditScreen(item, null);
            return true;
        };
    }

    private static boolean canShulkerOpen(BlockPos pos, BlockState state) {
        Box box = ShulkerEntity.calculateBoundingBox(1.0F, (Direction) state.get(ShulkerBoxBlock.FACING), 0.0F, 0.5F)
                .offset(pos.toBottomCenterPos())
                .contract(1.0E-6);
        return mc.world.isSpaceEmpty(box);
    }

    public static int predictOpenVanillaContainerSize(BlockPos blockPos) {
        if (mc.world.getBlockEntity(blockPos) instanceof Inventory inventory) {
            int size = inventory.size();
            if (inventory instanceof ChestBlockEntity chest) {
                BlockState state = chest.getCachedState();
                if (state.getBlock() instanceof ChestBlock chestBlock) {
                    if (ChestBlock.isChestBlocked(mc.world, blockPos)) {
                        size = 0;
                    } else if (ChestBlock.getDoubleBlockType(state) != DoubleBlockProperties.Type.SINGLE) {
                        size = 54;
                    }
                }
            }
            if (inventory instanceof ShulkerBoxBlockEntity shulker) {
                BlockState state = shulker.getCachedState();
                if (shulker.getAnimationStage() == ShulkerBoxBlockEntity.AnimationStage.CLOSED
                        && !canShulkerOpen(blockPos, state)) {
                    size = 0;
                }
            }
            return size;
        }
        return 0;
    }

    public static void executePredictInventoryAction(Consumer<ScreenHandler> callback) {
        // todo fix prediction initialization
        int nextPredictedIndex = (InvTasks.LAST_SYNC_ID % 100) + 1;
        ScreenHandler fakeScreenHandler =
                GenericContainerScreenHandler.createGeneric9x6(nextPredictedIndex, mc.player.getInventory());
        ScreenHandler handler = mc.player.currentScreenHandler;
        try {
            mc.player.currentScreenHandler = fakeScreenHandler;
            callback.accept(fakeScreenHandler);
        } finally {
            mc.player.currentScreenHandler = handler;
        }
    }

    @Getter
    @ApiMethod
    public static final ModuleGroup moduleManager = new ModuleGroup("Inv");

    @Getter
    public static InvExtra invExtra;

    @Getter
    public static GuiMove guiMove;

    @Getter
    public static FastInv fastInv;

    @Getter
    public static KeepInv keepInv;

    @Getter
    public static FastCraft fastCraft;

    @Getter
    public static FastChest fastChest;

    @Getter
    public static AutoStore autoStore;

    @Getter
    public static AutoSteal autoSteal;

    @Getter
    public static AutoShulker autoShulker;

    @Getter
    public static ChestHistory chestHistory;

    @Getter
    public static ItemEditor itemEditor;

    @Getter
    public static PickItem pickItem;

    @Getter
    public static QuickButton quickButton;

    @Getter
    public static SaveItem saveItem;

    @Getter
    public static NbtTooltips nbtTooltips;

    @Getter
    //
    public static final ItemCache customItemDatabase = new ItemCache("sfhelper-configs/recipes/item-database.json");

    public static final Codec<ItemStackData> CUSTOM_ITEM_DATA_CODEC = customItemDatabase.createStackDataCodec();

    public static final Codec<ItemStackDataWithAmount> CUSTOM_AMOUNT_ITEM_DATA_CODEC =
            ItemStackDataWithAmount.createCodecOf(CUSTOM_ITEM_DATA_CODEC);

    @Getter
    private static final LimitedSpeedExecutor clickExecutor;

    private static void initModules(ModuleManager m) {
        invExtra = new InvExtra().register(m);
        guiMove = new GuiMove().register(m);
        keepInv = new KeepInv().register(m);
        fastInv = new FastInv().register(m);
        fastCraft = new FastCraft().register(m);
        fastChest = new FastChest().register(m);
        autoStore = new AutoStore().register(m);
        autoSteal = new AutoSteal().register(m);
        autoShulker = new AutoShulker().register(m);
        chestHistory = new ChestHistory().register(m);
        itemEditor = new ItemEditor().register(m);
        pickItem = new PickItem().register(m);
        quickButton = new QuickButton().register(m);
        saveItem = new SaveItem().register(m);
        nbtTooltips = new NbtTooltips().register(m);
    }

    static {
        Tasks.registerGameTask(r -> {
            InvTasks.clickExecutor.reset();
        });
        Listener.registerSinglePacketListener(PlayerInteractBlockC2SPacket.class, InvTasks::listenInteractBlockPacket);
        Listener.getPacketPoint().getChannel(OpenScreenS2CPacket.class).registerHandler(InvTasks::onOpenScreen);
        Listener.getGameJoinPoint().registerHandler(InvTasks::onGameJoin);
        Listener.getPostOpenHandledScreen().registerHandler(InvTasks::onOpenScreenCreate);
        Listener.getPacketPostHandlePoint()
                .getChannel(ScreenHandlerSlotUpdateS2CPacket.class)
                .registerHandler(InvTasks::onInventoryOld);
        Listener.getPacketPostHandlePoint()
                .getChannel(InventoryS2CPacket.class)
                .registerHandler(InvTasks::onInventoryOld2);
        moduleManager.registerFactories(InvTasks::initModules);
        HackModules.registerModuleGroup(moduleManager);
        clickExecutor = new LimitedSpeedExecutor(invExtra.inventoryClickLimit);
    }
}
