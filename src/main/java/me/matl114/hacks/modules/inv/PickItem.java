package me.matl114.hacks.modules.inv;

import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.HotKeyUtils;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.collections.Point;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;

public class PickItem extends BaseModule {

    public PickItem() {}

    public static final String[] PICK_ITEM = {"inventory", "pick-item"};

    public final KeyBindRef pickItemHotkey = hotkey(Configs.INV_CONFIG, PICK_ITEM)
            .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.MOUSE_BUTTON_3))
            .registerHotkey(HotKeyUtils.asHandler(this::onPickItem))
            .build();

    public boolean onPickItem() {
        PlayerEntity player = mc.player;
        if (player == null) return false;
        Screen nowScreen = InvTasks.getCurrentServerScreen(player);
        if (!player.isCreative() && nowScreen instanceof HandledScreen<?> handled) {
            Point mouseCoord = ScreenUtils.getMouseCoord(mc);
            Slot slot = HandledScreenAccess.of(handled).reallyGetSlotAt(mouseCoord.x, mouseCoord.y);
            if (slot != null) {
                if (slot.inventory instanceof PlayerInventory) {
                    if (slot.getIndex() >= 36) {
                        Debug.chat("Invalid slot for player Inventory", slot.getIndex());
                    } else {
                        Debug.chat("run pickup");
                        Debug.chat("No Longer support this feat");
                        // mc.interactionManager.pickFromInventory(slot.getIndex());
                    }
                    return true;
                } else {
                    Debug.chat("Invalid slot outside player Inventory");
                }
            }
        }
        return false;
    }
}
