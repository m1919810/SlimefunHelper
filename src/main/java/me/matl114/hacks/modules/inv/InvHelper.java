package me.matl114.hacks.modules.inv;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.SlotClickAction;
import me.matl114.events.impl.UseItem;
import me.matl114.events.impl.UseItemOnBlock;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.interact.InteractManager;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.InventoryUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public class InvHelper extends BaseModule {
    public InvHelper() {
        super("InvHelper");
    }

    public final ModulePath root = makePath(Configs.INV_CONFIG, "inv-helper");
    public final IntRef checkDelay =
            intBuilder(root.add("check-delay")).defaultValue(2).build();

    public final IntRef totalOp =
            intBuilder(root.add("total-operation")).defaultValue(2).build();

    public final ModulePath hotBar = root.add("hot-bar");

    public final FlagRef autoStack =
            flagBuilder(hotBar.add("auto-stack-hot-bar")).build();

    public final KeyBindRef hotkey = toggleHotkey(
                    hotBar.add("auto-stack-hot-bar-hotkey"), new MultiKeyBind(), hotBar.add("auto-stack-hot-bar"))
            .build();

    public final DoubleRef stackPercentage =
            doubleBuilder(hotBar.add("stack-percentage")).defaultValue(0.25D).build();

    public final FlagRef stackUsingHotBar =
            flagBuilder(hotBar.add("hot-bar-stack-using-hot-bar")).build();

    public final FlagRef offHandStack =
            flagBuilder(hotBar.add("off-hand-stack")).build();

    public final ModulePath resupply = root.add("re-supply");

    public final FlagRef resupplyUsing =
            flagBuilder(resupply.add("resupply-when-using")).build();

    public final KeyBindRef hotkeyUsing = toggleHotkey(
                    resupply.add("resupply-when-using-hotkey"), new MultiKeyBind(), resupply.add("resupply-when-using"))
            .build();

    public final FlagRef resupplyUsingOn =
            flagBuilder(resupply.add("resupply-when-using-on")).build();

    public final KeyBindRef hotkeyUsingOn = toggleHotkey(
                    resupply.add("resupply-when-using-on-hotkey"),
                    new MultiKeyBind(),
                    resupply.add("resupply-when-using-on"))
            .build();

    public final DoubleRef resupplyPercentage = doubleBuilder(resupply.add("resupply-percentage"))
            .defaultValue(0.125)
            .build();

    public final ModulePath stacking = root.add("stacking");

    public final FlagRef autoMerge = flagBuilder(stacking.add("auto-merge")).build();

    public final KeyBindRef autoMergeHotkey = toggleHotkey(
                    stacking.add("auto-merge-hotkey"), new MultiKeyBind(), stacking.add("auto-merge"))
            .build();

    public final DoubleRef autoMergePercentage = doubleBuilder(stacking.add("auto-merge-percentage"))
            .defaultValue(0.125)
            .build();

    public final KeyBindRef mergeKey = hotkey(stacking.add("stack-inventory-key"))
            .defaultValue(new MultiKeyBind())
            .registerHotkey(HotKeyUtils.asNoneInputHandler(this::onMergeInventory))
            .build();

    public final ModulePath drop = root.add("auto-drop");

    public final FlagRef autoDrop = flagBuilder(drop.add("auto-drop")).build();

    public final KeyBindRef dropHotkey = toggleHotkey(
                    drop.add("auto-drop-hotkey"), new MultiKeyBind(), drop.add("auto-drop"))
            .build();

    public final NBTRef<EntrySet<Item>> dropList = builder(drop.add("drop-list"), EntrySet.<Item>parameter())
            .defaultValue(new EntrySet<>(Registries.ITEM, List.of()))
            .build();

    Map<Hand, ItemStack> preTickItems = new EnumMap<>(Hand.class);
    int preTickSelected;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onInventoryTick);
        registerListener(Listener.getPostPlayerUseItem(), this::onPostUseItem);
        registerListener(Listener.getPostPlayerUseItemOnBlock(), this::onPostUseItemOn);
        registerListener(Listener.getPreClickSlot(), this::onPreSlotClick);
    }

    int timer;

    public void onInventoryTick(Event<ClientPlayerEntity> event) {
        if (checkNull()) return;
        preTickItems.put(
                Hand.MAIN_HAND, mc.player.getStackInHand(Hand.MAIN_HAND).copy());
        preTickItems.put(Hand.OFF_HAND, mc.player.getStackInHand(Hand.OFF_HAND).copy());
        preTickSelected = InventoryUtils.getSelectedSlot();
        if (++timer >= checkDelay.get()) {
            timer = 0;
        } else {
            return;
        }
        int total = totalOp.get();
        PlayerInventory inventory = mc.player.getInventory();
        if (autoDrop.get()) {
            Set<Item> dropSet = dropList.get().set();
            if (!dropSet.isEmpty()) {
                for (var re = 0; re < InventoryUtils.getPlayerBackpackSize(); ++re) {
                    ItemStack stack = inventory.getStack(re);
                    if (!stack.isEmpty() && dropSet.contains(stack.getItem())) {
                        int idx = re;
                        InvTasks.getClickExecutor().execute(() -> {
                            ItemStack stack2 = inventory.getStack(idx);
                            if (!stack2.isEmpty() && dropSet.contains(stack2.getItem())) {
                                var handler = InvTasks.getCurrentServerScreenHandler(mc.player);
                                var slotIndex = handler.getSlotIndex(inventory, idx);
                                if (slotIndex.isPresent()) {
                                    mc.interactionManager.clickSlot(
                                            handler.syncId, slotIndex.getAsInt(), 1, SlotActionType.THROW, mc.player);
                                    return true;
                                }
                            }
                            return false;
                        });
                        if (--total <= 0) {
                            return;
                        }
                    }
                }
            }
        }

        if (autoMerge.get()) {
            for (var re = 0; re < InventoryUtils.getPlayerBackpackSize(); ++re) {
                ItemStack stack = inventory.getStack(re);
                if (!stack.isEmpty() && (double) stack.getCount() < (autoMergePercentage.get() * stack.getMaxCount())) {
                    int idx = re;
                    for (var find = 0; find < InventoryUtils.getPlayerBackpackSize(); ++find) {
                        if (find == re) continue;
                        ItemStack stack2 = inventory.getStack(find);
                        if (!stack2.isEmpty()
                                && stack2.getCount() < stack2.getMaxCount()
                                && ItemStack.areItemsAndComponentsEqual(stack, stack2)) {
                            int findIdx = find;
                            InvTasks.getClickExecutor().execute(() -> {
                                ItemStack stack3 = inventory.getStack(idx);
                                ItemStack stack4 = inventory.getStack(findIdx);
                                if (!stack3.isEmpty()
                                        && (double) stack3.getCount()
                                                < (autoMergePercentage.get() * stack3.getMaxCount())
                                        && !stack4.isEmpty()
                                        && stack4.getCount() < stack4.getMaxCount()
                                        && ItemStack.areItemsAndComponentsEqual(stack3, stack4)) {
                                    var handler = InvTasks.getCurrentServerScreenHandler(mc.player);
                                    var slotIndex = handler.getSlotIndex(inventory, idx);
                                    var targetIndex = handler.getSlotIndex(inventory, findIdx);
                                    if (slotIndex.isPresent() && targetIndex.isPresent()) {
                                        boolean needPickUp =
                                                !handler.getCursorStack().isEmpty();
                                        mc.interactionManager.clickSlot(
                                                handler.syncId,
                                                slotIndex.getAsInt(),
                                                0,
                                                SlotActionType.PICKUP,
                                                mc.player);
                                        mc.interactionManager.clickSlot(
                                                handler.syncId,
                                                targetIndex.getAsInt(),
                                                0,
                                                SlotActionType.PICKUP,
                                                mc.player);
                                        needPickUp |= !handler.getCursorStack().isEmpty();
                                        if (needPickUp) {
                                            mc.interactionManager.clickSlot(
                                                    handler.syncId,
                                                    slotIndex.getAsInt(),
                                                    0,
                                                    SlotActionType.PICKUP,
                                                    mc.player);
                                        }
                                        return true;
                                    }
                                }
                                return false;
                            });
                            if (--total <= 0) {
                                return;
                            }
                            break;
                        }
                    }
                }
            }
        }
        if (autoStack.get()) {
            for (var i = 0; i < 9; ++i) {
                ItemStack stack = inventory.getStack(i);
                if (!stack.isEmpty() && (double) stack.getCount() < (stackPercentage.get() * stack.getMaxCount())) {
                    if (resupply(inventory, i, stack, stackUsingHotBar.get())) {
                        if (--total <= 0) {
                            return;
                        }
                        break;
                    }
                }
            }
            if (offHandStack.get()) {
                int i = 40;
                ItemStack stack = inventory.getStack(i);
                if (!stack.isEmpty() && (double) stack.getCount() < (stackPercentage.get() * stack.getMaxCount())) {
                    resupply(inventory, i, stack, stackUsingHotBar.get());
                }
            }
        }
    }

    boolean hasAnyUse = false;
    boolean hasAnyUseOn = false;

    public void onPostUseItem(Event<UseItem> event) {
        if (resupplyUsing.get() && InteractManager.INSTANCE.duringInputEvent) {
            ItemStack stack = event.context.handItem();
            if (stack.isEmpty()) return;
            Hand hand = event.context.hand();
            // ghost hand items
            if (!ItemStack.areItemsAndComponentsEqual(preTickItems.get(hand), stack)
                    || (hand == Hand.MAIN_HAND && preTickSelected != InventoryUtils.getSelectedSlot())) {
                return;
            }
            ItemStack currentHandItem = mc.player.getStackInHand(hand);
            if (currentHandItem.getCount() != stack.getCount()
                    || !ItemStack.areItemsAndComponentsEqual(stack, currentHandItem)) {
                int slot = hand == Hand.MAIN_HAND ? mc.player.getInventory().getSelectedSlot() : 40;
                if (currentHandItem.isEmpty()
                        || !currentHandItem.isOf(stack.getItem())
                        || (currentHandItem.getCount() < (resupplyPercentage.get() * stack.getMaxCount()))) {
                    hasAnyUse = true;
                    Tasks.scheduleDelayedPre(
                            () -> {
                                if (!hasAnyUse) return;
                                hasAnyUse = false;
                                ItemStack handItem2 = mc.player.getStackInHand(hand);
                                if (!handItem2.isEmpty()
                                        && !ItemStack.areItemsAndComponentsEqual(handItem2, currentHandItem)) {
                                    return;
                                }
                                PlayerInventory inventory2 = mc.player.getInventory();
                                resupply(inventory2, slot, stack, true);
                            },
                            0);
                }
            }
        }
    }

    public void onPostUseItemOn(Event<UseItemOnBlock> event) {
        if (resupplyUsingOn.get() && InteractManager.INSTANCE.duringInputEvent) {
            ItemStack stack = event.context.handItem();
            if (stack.isEmpty()) return;
            Hand hand = event.context.hand();
            // ghost hand items
            if (!ItemStack.areItemsAndComponentsEqual(preTickItems.get(hand), stack)
                    || (hand == Hand.MAIN_HAND && preTickSelected != InventoryUtils.getSelectedSlot())) {
                return;
            }
            ItemStack currentHandItem = mc.player.getStackInHand(hand);
            if (currentHandItem.getCount() != stack.getCount()
                    || !ItemStack.areItemsAndComponentsEqual(stack, currentHandItem)) {
                int slot = hand == Hand.MAIN_HAND ? mc.player.getInventory().getSelectedSlot() : 40;
                if (currentHandItem.isEmpty()
                        || !currentHandItem.isOf(stack.getItem())
                        || (currentHandItem.getCount() < (resupplyPercentage.get() * stack.getMaxCount()))) {
                    hasAnyUseOn = true;
                    Tasks.scheduleDelayedPre(
                            () -> {
                                if (!hasAnyUseOn) return;
                                hasAnyUseOn = false;
                                ItemStack handItem2 = mc.player.getStackInHand(hand);
                                if (!ItemStack.areItemsAndComponentsEqual(handItem2, currentHandItem)) {
                                    return;
                                }
                                PlayerInventory inventory2 = mc.player.getInventory();
                                resupply(inventory2, slot, stack, true);
                            },
                            0);
                }
            }
        }
    }

    // prevent resupply to ghost hand
    public void onPreSlotClick(Event<SlotClickAction> event) {
        if (InteractManager.INSTANCE.duringInputEvent) {
            hasAnyUse = false;
            hasAnyUseOn = false;
        }
    }

    private boolean resupply(PlayerInventory inventory, int idx, ItemStack stack, boolean usingHotbar) {
        ItemStack currentStack = inventory.getStack(idx);
        var handler0 = InvTasks.getCurrentServerScreenHandler(mc.player);
        ItemStack cursorStack = handler0.getCursorStack();
        if (ItemStack.areItemsAndComponentsEqual(stack, cursorStack)) {
            InvTasks.getClickExecutor().execute(() -> {
                var handler = InvTasks.getCurrentServerScreenHandler(mc.player);
                if (ItemStack.areItemsAndComponentsEqual(stack, handler.getCursorStack())) {
                    var slotIndex = handler.getSlotIndex(inventory, idx);
                    if (slotIndex.isPresent()) {
                        mc.interactionManager.clickSlot(
                                handler.syncId, slotIndex.getAsInt(), 0, SlotActionType.PICKUP, mc.player);
                        return true;
                    }
                }
                return false;
            });
        }
        if (ItemStack.areItemsAndComponentsEqual(stack, currentStack)) {
            for (var find = usingHotbar ? 0 : 9; find < InventoryUtils.getPlayerBackpackSize(); ++find) {
                if (find == idx) continue;
                ItemStack stack2 = inventory.getStack(find);
                if (!stack2.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, stack2)) {
                    int findIndex = find;
                    InvTasks.getClickExecutor().execute(() -> {
                        ItemStack stack3 = inventory.getStack(idx);
                        ItemStack stack4 = inventory.getStack(findIndex);
                        if (!stack3.isEmpty()
                                && (double) stack3.getCount() < stack3.getMaxCount()
                                && ItemStack.areItemsAndComponentsEqual(stack, stack3)
                                && !stack4.isEmpty()
                                && ItemStack.areItemsAndComponentsEqual(stack3, stack4)) {
                            var handler = InvTasks.getCurrentServerScreenHandler(mc.player);
                            var slotIndex = handler.getSlotIndex(inventory, idx);
                            var targetIndex = handler.getSlotIndex(inventory, findIndex);
                            if (targetIndex.isPresent()) {
                                if (stack4.getCount() >= stack4.getMaxCount()) {
                                    mc.interactionManager.clickSlot(
                                            handler.syncId,
                                            targetIndex.getAsInt(),
                                            idx,
                                            SlotActionType.SWAP,
                                            mc.player);
                                } else if (slotIndex.isPresent()) {
                                    boolean needPickUp =
                                            !handler.getCursorStack().isEmpty();
                                    mc.interactionManager.clickSlot(
                                            handler.syncId,
                                            targetIndex.getAsInt(),
                                            0,
                                            SlotActionType.PICKUP,
                                            mc.player);
                                    mc.interactionManager.clickSlot(
                                            handler.syncId, slotIndex.getAsInt(), 0, SlotActionType.PICKUP, mc.player);
                                    needPickUp |= !handler.getCursorStack().isEmpty();
                                    if (needPickUp) {
                                        mc.interactionManager.clickSlot(
                                                handler.syncId,
                                                targetIndex.getAsInt(),
                                                0,
                                                SlotActionType.PICKUP,
                                                mc.player);
                                    }
                                }
                                return true;
                            }
                        }
                        return false;
                    });
                    return true;
                }
            }
        } else {
            for (var find = usingHotbar ? 0 : 9; find < InventoryUtils.getPlayerBackpackSize(); ++find) {
                if (find == idx) continue;
                ItemStack stack2 = inventory.getStack(find);
                if (!stack2.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, stack2)) {
                    int findIndex = find;
                    InvTasks.getClickExecutor().execute(() -> {
                        ItemStack stack4 = inventory.getStack(findIndex);
                        if (!stack4.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, stack4)) {
                            var handler = InvTasks.getCurrentServerScreenHandler(mc.player);
                            var targetIndex = handler.getSlotIndex(inventory, findIndex);
                            if (targetIndex.isPresent()) {
                                mc.interactionManager.clickSlot(
                                        handler.syncId, targetIndex.getAsInt(), idx, SlotActionType.SWAP, mc.player);
                                return true;
                            }
                        }
                        return false;
                    });
                    return true;
                }
            }
        }

        return false;
    }

    private void onMergeInventory() {
        if (checkNull()) return;
        var inventory = mc.player.getInventory();
        boolean[] locked = new boolean[InventoryUtils.getPlayerBackpackSize()];
        for (var i = 0; i < InventoryUtils.getPlayerBackpackSize(); ++i) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty() || stack.getCount() >= stack.getMaxCount()) {
                locked[i] = true;
                continue;
            }
        }
        for (var i = 0; i < InventoryUtils.getPlayerBackpackSize(); ++i) {
            if (locked[i]) continue;
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            int idx = i;
            for (var j = 0; j < InventoryUtils.getPlayerBackpackSize(); ++j) {
                if (i == j) {
                    continue;
                }
                if (locked[j]) {
                    continue;
                }
                ItemStack stack3 = inventory.getStack(j);
                if (!stack3.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, stack3)) {
                    int findIndex = j;
                    InvTasks.getClickExecutor().execute(() -> {
                        ItemStack stack4 = inventory.getStack(idx);
                        ItemStack stack5 = inventory.getStack(findIndex);
                        if (stack4.isEmpty() || stack4.getCount() >= stack4.getMaxCount()) {
                            locked[idx] = true;
                        }
                        if (stack5.isEmpty() || stack5.getCount() >= stack5.getMaxCount()) {
                            locked[findIndex] = true;
                        }
                        if (!locked[idx] && !locked[findIndex]) {
                            var handler = InvTasks.getCurrentServerScreenHandler(mc.player);
                            var slotIndex = handler.getSlotIndex(inventory, idx);
                            var targetIndex = handler.getSlotIndex(inventory, findIndex);
                            if (targetIndex.isPresent() && slotIndex.isPresent()) {
                                boolean needPickUp = !handler.getCursorStack().isEmpty();
                                mc.interactionManager.clickSlot(
                                        handler.syncId, targetIndex.getAsInt(), 0, SlotActionType.PICKUP, mc.player);
                                mc.interactionManager.clickSlot(
                                        handler.syncId, slotIndex.getAsInt(), 0, SlotActionType.PICKUP, mc.player);
                                needPickUp |= !handler.getCursorStack().isEmpty();
                                if (needPickUp) {
                                    mc.interactionManager.clickSlot(
                                            handler.syncId,
                                            targetIndex.getAsInt(),
                                            0,
                                            SlotActionType.PICKUP,
                                            mc.player);
                                    locked[idx] = true;
                                } else {
                                    locked[findIndex] = true;
                                }
                                return true;
                            }
                        }
                        return false;
                    });
                }
            }
        }
    }
}
