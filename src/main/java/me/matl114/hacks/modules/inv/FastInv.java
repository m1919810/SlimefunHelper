package me.matl114.hacks.modules.inv;

import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.TaskManagers;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.collections.Point;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class FastInv extends BaseModule {
    public static final String[] DO_SHIFT = {"fastinv", "apply-shift"};
    public static final String[] DO_DROP = {"fastinv", "apply-drop"};
    public static final String[] DROP_HOTKEY = {"fastinv", "fast-drop"};
    public static final String[] QUICK_DROP_HOTKEY = {"fastinv", "quick-drop"};
    public static final String[] SHIFT_HOTKEY = {"fastinv", "fast-mov"};
    public static final String[] FAST_INV = {"fastinv", "fast-inv"};
    public static final String[] LEFT_ONE = {"fastinv", "left-one"};

    public FastInv() {
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(Configs.INV_CONFIG, FAST_INV).build();

    public final FlagRef enableLeftOne =
            flagBuilder(Configs.INV_CONFIG, LEFT_ONE).build();

    public final FlagRef enableDrop = flagBuilder(Configs.INV_CONFIG, DO_DROP).build();

    public final FlagRef enableShift = flagBuilder(Configs.INV_CONFIG, DO_SHIFT).build();

    public final KeyBindRef shiftAction = hotkey(
                    Configs.INV_CONFIG, SHIFT_HOTKEY, new MultiKeyBind(KeyCode.KEY_LEFT_SHIFT, KeyCode.MOUSE_BUTTON_1))
            .registerHotkey(HotKeyUtils.asHandler(this::onShiftAction))
            .build();

    public final KeyBindRef dropAction = hotkey(
                    Configs.INV_CONFIG, DROP_HOTKEY, new MultiKeyBind(KeyCode.KEY_LEFT_SHIFT, KeyCode.KEY_Q))
            .registerHotkey(HotKeyUtils.asHandler(this::onDropAction))
            .build();

    public final KeyBindRef quickDropAction = hotkey(
                    Configs.INV_CONFIG,
                    QUICK_DROP_HOTKEY,
                    new MultiKeyBind(KeyCode.KEY_LEFT_SHIFT, KeyCode.KEY_Q, KeyCode.MOUSE_BUTTON_1))
            .registerHotkey(HotKeyUtils.asHandler(this::onQuickDropAction))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        TaskManagers.getToggleManager().register(TaskManagers.PREFIX_BUTTON_TOGGLE + "." + "fast-inv", enable);
        TaskManagers.getToggleManager().register(TaskManagers.PREFIX_BUTTON_TOGGLE + "." + "left-one", enableLeftOne);
    }

    public boolean onShiftAction() {
        PlayerEntity player = mc.player;
        if (player == null) return false;
        Screen nowScreen = InvTasks.getCurrentServerScreen(player);
        // filter inventory screen
        if (nowScreen instanceof HandledScreen<?> handled && !(nowScreen instanceof InventoryScreen)) {
            ScreenHandler handler = handled.getScreenHandler();
            Point mouseCoord = ScreenUtils.getMouseCoord(mc);
            Slot slot = HandledScreenAccess.of(handled).reallyGetSlotAt(mouseCoord.x, mouseCoord.y);
            if (enable.get() && enableShift.get()) {
                InvTasks.quickMoveSlotItem(handled, slot);
            } else if (enableLeftOne.get()) {
                if (slot != null) {
                    // Debug.info("debug at ",slot.getIndex());
                    int index = handler.slots.indexOf(slot);
                    // Debug.info("index at", index);
                    if (index >= 0) {
                        InvTasks.quickMoveSlot(handled, index);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean onDropAction() {
        if (mc.player == null) return false;
        if (enable.get() && enableDrop.get()) {
            PlayerEntity player = mc.player;
            Screen nowScreen = InvTasks.getCurrentServerScreen(player);
            if (nowScreen instanceof HandledScreen<?> handled) {

                Point mouseCoord = ScreenUtils.getMouseCoord(mc);
                Slot slot = HandledScreenAccess.of(handled).reallyGetSlotAt(mouseCoord.x, mouseCoord.y);
                if (InvTasks.quickDropSlotItem(handled, slot)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean onQuickDropAction() {
        if (mc.player == null) return false;
        if (enable.get() && enableDrop.get()) {
            return InvTasks.dropAllCursorStack();
        }
        return false;
    }
}
