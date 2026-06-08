package me.matl114.hacks.modules.inv;

import com.google.common.util.concurrent.Runnables;
import java.util.OptionalInt;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.utils.InventoryUtils;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class InvExtra extends BaseModule {
    public static InvExtra INSTANCE;
    public final ModulePath inventory = makePath(Configs.INV_CONFIG, "inventory");

    public InvExtra() {
        INSTANCE = this;
    }

    public final IntRef inventoryClickLimit = intBuilder(inventory.add("packet-limit"))
            .defaultValue(40)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef invGrimFix =
            flagBuilder(inventory.add("move-click-grim-fix")).build();

    public final FlagRef expandInventory =
            flagBuilder(inventory.add("expand-backpack-inventory")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPacketPoint().getChannel(ClickSlotC2SPacket.class), this::onClickSlot);
        registerListener(Listener.getPacketPoint().getChannel(CloseHandledScreenC2SPacket.class), this::onCloseScreen);
    }

    public void onClickSlot(Event<ClickSlotC2SPacket> event) {
        if (invGrimFix.get()) {
            // do not support viafabric, I guess
            // just send input packets, do not change sprint status
            // do not send the fucking sprint packets, shit
            MovTasks.getMovExtra().sendInputPacketsForInventoryAction();
        }
    }

    public void onCloseScreen(Event<CloseHandledScreenC2SPacket> closeS2C) {
        if (expandInventory.get() && closeS2C.context.getSyncId() == mc.player.playerScreenHandler.syncId) {
            closeS2C.cancel();
        }
    }

    public Runnable switchOrSwapInventoryIndexToHand(int hand) {
        int selected = InventoryUtils.getSelectedSlot();
        if (selected != hand) {
            if (hand < 9) {
                PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(hand);
                return () -> {
                    PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(selected);
                };
            } else {
                OptionalInt slotIndex = mc.player.currentScreenHandler.getSlotIndex(mc.player.getInventory(), hand);
                if (slotIndex.isPresent()) {
                    int swapped = slotIndex.getAsInt();
                    if (swapped >= 0) {
                        MovTasks.getMovExtra().sendPacketsForInventoryAction();
                        mc.interactionManager.clickSlot(
                                mc.player.currentScreenHandler.syncId,
                                swapped,
                                selected,
                                SlotActionType.SWAP,
                                mc.player);
                        return () -> {
                            mc.interactionManager.clickSlot(
                                    mc.player.currentScreenHandler.syncId,
                                    swapped,
                                    selected,
                                    SlotActionType.SWAP,
                                    mc.player);
                        };
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }
        return Runnables.doNothing();
    }

    public Runnable swapInventoryIndexToHand(int hand) {
        int selected = InventoryUtils.getSelectedSlot();
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
                } else {
                    return null;
                }
            } else {
                return null;
            }
            // }
        }
        return Runnables.doNothing();
    }

    public Runnable swapInventoryIndexToOffhand(int hand) {
        if (hand == 40) return Runnables.doNothing();
        OptionalInt slotIndex = mc.player.currentScreenHandler.getSlotIndex(mc.player.getInventory(), hand);
        if (slotIndex.isPresent()) {
            int swapped = slotIndex.getAsInt();
            if (swapped >= 0) {
                MovTasks.getMovExtra().sendPacketsForInventoryAction();

                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId, swapped, 40, SlotActionType.SWAP, mc.player);
                return () -> {
                    mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId, swapped, 40, SlotActionType.SWAP, mc.player);
                };
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    //    public Runnable swapInventoryIndex(int a, int b){
    //
    //    }

    public Runnable swapInventorySlots(int armorSlot, int targetSlot) {
        if (armorSlot == targetSlot) return Runnables.doNothing();
        var handler = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
        var slots = handler.slots;
        if (slots.size() <= armorSlot || slots.size() <= targetSlot) {
            return null;
        }
        MovTasks.getMovExtra().sendPacketsForInventoryAction();
        var targetSlotInstance = handler.slots.get(targetSlot);
        if (targetSlotInstance.inventory instanceof PlayerInventory
                && (targetSlotInstance.getIndex() < 9 || targetSlotInstance.getIndex() == 40)) {
            // use number operation
            int target = targetSlotInstance.getIndex();
            mc.interactionManager.clickSlot(handler.syncId, armorSlot, target, SlotActionType.SWAP, mc.player);
            return () -> {
                mc.interactionManager.clickSlot(handler.syncId, armorSlot, target, SlotActionType.SWAP, mc.player);
            };
        } else {
            var armorSlotInstance = handler.slots.get(armorSlot);
            if (armorSlotInstance.inventory instanceof PlayerInventory
                    && (armorSlotInstance.getIndex() < 9 || armorSlotInstance.getIndex() == 40)) {
                int target = armorSlotInstance.getIndex();
                mc.interactionManager.clickSlot(handler.syncId, targetSlot, target, SlotActionType.SWAP, mc.player);
                return () -> {
                    mc.interactionManager.clickSlot(handler.syncId, targetSlot, target, SlotActionType.SWAP, mc.player);
                };
            } else {
                // fuck, do not kick me.
                swapTwoIdiotSlot(handler, targetSlot, armorSlot);
                return () -> {
                    swapTwoIdiotSlot(handler, targetSlot, armorSlot);
                };
            }
        }
    }

    private void swapTwoIdiotSlot(ScreenHandler handler, int targetSlot, int armorSlot) {
        int fuckingHotbar114514 = InventoryUtils.getSelectedSlot() == 8 ? 7 : 8;
        // swap target to hotbar, hotbar to target
        mc.interactionManager.clickSlot(
                handler.syncId, targetSlot, fuckingHotbar114514, SlotActionType.SWAP, mc.player);
        // swap hotbar to armor, armor to hotbar
        mc.interactionManager.clickSlot(handler.syncId, armorSlot, fuckingHotbar114514, SlotActionType.SWAP, mc.player);
        // swap the rest
        mc.interactionManager.clickSlot(
                handler.syncId, targetSlot, fuckingHotbar114514, SlotActionType.SWAP, mc.player);
    }
}
