package me.matl114.hacks.modules.inv;

import com.google.common.util.concurrent.Runnables;
import java.util.OptionalInt;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

public class InvExtra extends BaseModule {
    public static final String[] INV_CLICK_LIMIT = {"inventory", "packet-limit"};

    public static final String[] INV_GUI_CLICK_GRIM_FIX = {"inventory", "move-click-grim-fix"};

    public InvExtra() {}

    public final IntRef inventoryClickLimit = builder(Configs.INV_CONFIG, IntRef.TYPE)
            .path(INV_CLICK_LIMIT)
            .defaultValue(40)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef invGrimFix =
            flagBuilder(Configs.INV_CONFIG, INV_GUI_CLICK_GRIM_FIX).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(ClickSlotC2SPacket.class), this::onClickSlot);
    }

    public void onClickSlot(Event<ClickSlotC2SPacket> event) {
        if (invGrimFix.get()) {
            // do not support viafabric, I guess
            MovTasks.getMovExtra().sendPacketsForInventoryAction();
        }
    }

    public Runnable swapInventoryIndexToHand(int hand) {
        int selected = mc.player.getInventory().getSelectedSlot();
        if (selected != hand) {
            //            if (hand < 9) {
            //                PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(hand);
            //                return () -> {
            //                    PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(selected);
            //                };
            //            } else {
            OptionalInt slotIndex = mc.player.currentScreenHandler.getSlotIndex(mc.player.getInventory(), hand);
            if (slotIndex.isPresent()) {
                int swapped = slotIndex.getAsInt();
                if (swapped >= 0) {
                    MovTasks.getMovExtra().sendPacketsForInventoryAction();

                    mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId, swapped, selected, SlotActionType.SWAP, mc.player);
                    return () -> {
                        mc.interactionManager.clickSlot(
                                mc.player.currentScreenHandler.syncId,
                                swapped,
                                selected,
                                SlotActionType.SWAP,
                                mc.player);
                    };
                }
            } else {
                return null;
            }
            // }
        }
        return Runnables.doNothing();
    }
}
