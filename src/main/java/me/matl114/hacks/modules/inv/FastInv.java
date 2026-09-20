package me.matl114.hacks.modules.inv;

import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.managers.Configs;
import me.matl114.managers.TaskManagers;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ItemStackUtils;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.collections.Point;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class FastInv extends BaseModule {
    public final ModulePath fastInv = makePath(Configs.INV_CONFIG, "fastinv");

    public FastInv() {
        super("FastInv");
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(fastInv.add("fast-inv")).build();

    public final FlagRef enableLeftOne = flagBuilder(fastInv.add("left-one")).build();

    public final FlagRef enableDrop = flagBuilder(fastInv.add("apply-drop")).build();

    public final FlagRef enableShift = flagBuilder(fastInv.add("apply-shift")).build();

    public final FlagRef enableEmptyClick =
            flagBuilder(fastInv.add("empty-click")).build();

    public final KeyBindRef shiftAction = hotkey(
                    Configs.INV_CONFIG,
                    fastInv.add("fast-mov").toPath(),
                    new MultiKeyBind(KeyCode.KEY_LEFT_SHIFT, KeyCode.MOUSE_BUTTON_1))
            .registerHotkey(HotKeyUtils.asNoneInputHandler(this::onShiftAction))
            .build();

    public final KeyBindRef dropAction = hotkey(
                    Configs.INV_CONFIG,
                    fastInv.add("fast-drop").toPath(),
                    new MultiKeyBind(KeyCode.KEY_LEFT_SHIFT, KeyCode.KEY_Q))
            .registerHotkey(HotKeyUtils.asNoneInputHandler(this::onDropAction))
            .build();

    public static final String TAKE_ALL = "take-all";
    public static final String SAVE_ALL = "save-all";

    @Override
    public void registerAll() {
        super.registerAll();
        TaskManagers.getToggleManager().register(TaskManagers.PREFIX_BUTTON_TOGGLE + "." + "fast-inv", enable);
        TaskManagers.getToggleManager().register(TaskManagers.PREFIX_BUTTON_TOGGLE + "." + "left-one", enableLeftOne);
        TaskManagers.getTaskManager().register(TaskManagers.PREFIX_BUTTON_TASKS + "." + TAKE_ALL, this::takeAll);
        TaskManagers.getTaskManager().register(TaskManagers.PREFIX_BUTTON_TASKS + "." + SAVE_ALL, this::saveAll);
    }

    public boolean onShiftAction() {
        if (mc.player == null) return false;
        Screen nowScreen = InvTasks.getCurrentServerScreen(mc.player);
        // filter inventory screen
        if (nowScreen instanceof HandledScreen<?> handled && !(nowScreen instanceof InventoryScreen)) {
            ScreenHandler handler = handled.getScreenHandler();
            Point mouseCoord = ScreenUtils.getMouseCoord(mc);
            Slot slot = HandledScreenAccess.of(handled).reallyGetSlotAt(mouseCoord.x, mouseCoord.y);
            if (enable.get() && enableShift.get()) {
                if (slot != null) {
                    // Debug.info("debug at ",slot.getIndex());
                    int index = handler.slots.indexOf(slot);
                    // Debug.info("index at", index);
                    if (index >= 0) {
                        ItemStack template = slot.getStack();
                        if (!enableEmptyClick.get() && template.isEmpty()) {
                            return false;
                        }
                        ItemStack cleanedStack = ItemStackUtils.getCleanedItem(template, false, false);
                        var slots = handled.getScreenHandler().slots;
                        for (var re = 0; re < slots.size(); re++) {
                            Slot sl = slots.get(re);
                            if ((sl.inventory instanceof PlayerInventory)
                                    != (slot.inventory instanceof PlayerInventory)) {
                                continue;
                            }
                            final int idx = re;
                            InvTasks.getClickExecutor().execute(() -> {
                                if (mc.interactionManager == null
                                        || InvTasks.getCurrentServerScreenHandler(mc.player) != handler) {
                                    return false;
                                }
                                ItemStack stack = sl.getStack();
                                if (!ItemStack.areItemsEqual(stack, cleanedStack)
                                        || !ItemStack.areItemsAndComponentsEqual(
                                                ItemStackUtils.getCleanedItem(stack, false, false), cleanedStack)) {
                                    return false;
                                }

                                mc.interactionManager.clickSlot(
                                        handler.syncId, idx, 0, SlotActionType.QUICK_MOVE, mc.player);
                                return true;
                            });
                        }
                        return true;
                    }
                }
            } else if (enableLeftOne.get()) {
                if (slot != null) {
                    // Debug.info("debug at ",slot.getIndex());
                    int index = handler.slots.indexOf(slot);
                    // Debug.info("index at", index);
                    if (index >= 0) {
                        InvTasks.quickMoveSlot(handler, slot.getIndex(), true, true);
                    }
                }
            }
        }
        return false;
    }

    public boolean onDropAction() {
        if (mc.player == null) return false;
        if (enable.get() && enableDrop.get()) {
            Screen nowScreen = InvTasks.getCurrentServerScreen(mc.player);
            if (nowScreen instanceof HandledScreen<?> handled) {
                Point mouseCoord = ScreenUtils.getMouseCoord(mc);
                Slot slot = HandledScreenAccess.of(handled).reallyGetSlotAt(mouseCoord.x, mouseCoord.y);
                if (slot != null) {
                    var handler = handled.getScreenHandler();
                    int index = handler.slots.indexOf(slot);
                    if (index >= 0) {
                        ItemStack template = slot.getStack();
                        if (!enableEmptyClick.get() && template.isEmpty()) {
                            return true;
                        }
                        ItemStack cleanedStack = ItemStackUtils.getCleanedItem(template, false, false);
                        var slots = handled.getScreenHandler().slots;
                        InvTasks.getClickExecutor().execute(() -> {
                            if (mc.interactionManager == null
                                    || InvTasks.getCurrentServerScreenHandler(mc.player) != handler) {
                                return false;
                            }
                            if (handler.getCursorStack().isEmpty()) {
                                return false;
                            }
                            ItemStack stack = handler.getCursorStack();
                            if (!ItemStack.areItemsEqual(stack, cleanedStack)
                                    || !ItemStack.areItemsAndComponentsEqual(
                                            ItemStackUtils.getCleanedItem(stack, false, false), cleanedStack)) {
                                mc.interactionManager.clickSlot(
                                        handler.syncId, index, 0, SlotActionType.PICKUP, mc.player);
                                mc.interactionManager.clickSlot(
                                        handler.syncId, -999, 0, SlotActionType.PICKUP, mc.player);
                                return true;
                            }
                            mc.interactionManager.clickSlot(handler.syncId, -999, 0, SlotActionType.PICKUP, mc.player);
                            return true;
                        });
                        for (var re = 0; re < slots.size(); re++) {
                            Slot sl = slots.get(re);
                            final int idx = re;
                            InvTasks.getClickExecutor().execute(() -> {
                                if (mc.interactionManager == null
                                        || InvTasks.getCurrentServerScreenHandler(mc.player) != handler) {
                                    return false;
                                }
                                ItemStack stack = sl.getStack();
                                if (!ItemStack.areItemsEqual(stack, cleanedStack)
                                        || !ItemStack.areItemsAndComponentsEqual(
                                                ItemStackUtils.getCleanedItem(stack, false, false), cleanedStack)) {
                                    return false;
                                }

                                mc.interactionManager.clickSlot(
                                        handler.syncId, idx, 1, SlotActionType.THROW, mc.player);
                                return true;
                            });
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void takeAll() {
        if (checkNull()) return;
        ScreenHandler nowScreen = InvTasks.getCurrentServerScreenHandler(mc.player);
        if (!(nowScreen instanceof PlayerScreenHandler)) {
            ScreenHandler handler = nowScreen;
            for (int i = 0; i < handler.slots.size(); i++) {
                Slot slot = handler.getSlot(i);
                if (!(slot.inventory instanceof PlayerInventory)) {
                    final int index = i;
                    InvTasks.getClickExecutor().execute(() -> {
                        if (mc.interactionManager == null
                                || InvTasks.getCurrentServerScreenHandler(mc.player) != handler) {
                            return false;
                        }
                        ItemStack stack = slot.getStack();
                        if (enableEmptyClick.get() || !stack.isEmpty()) {
                            mc.interactionManager.clickSlot(
                                    handler.syncId, index, 1, SlotActionType.QUICK_MOVE, mc.player);
                            return true;
                        } else {
                            return false;
                        }
                    });
                }
            }
        }
    }

    public void saveAll() {
        if (checkNull()) return;
        ScreenHandler nowScreen = InvTasks.getCurrentServerScreenHandler(mc.player);
        if (!(nowScreen instanceof PlayerScreenHandler)) {
            ScreenHandler handler = nowScreen;
            for (int i = 0; i < handler.slots.size(); i++) {
                Slot slot = handler.getSlot(i);
                if (slot.inventory instanceof PlayerInventory) {
                    final int index = i;
                    InvTasks.getClickExecutor().execute(() -> {
                        if (mc.interactionManager == null
                                || InvTasks.getCurrentServerScreenHandler(mc.player) != handler) {
                            return false;
                        }
                        ItemStack stack = slot.getStack();
                        if (enableEmptyClick.get() || !stack.isEmpty()) {
                            mc.interactionManager.clickSlot(
                                    handler.syncId, index, 1, SlotActionType.QUICK_MOVE, mc.player);
                            return true;
                        } else {
                            return false;
                        }
                    });
                }
            }
        }
    }
}
