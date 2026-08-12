package me.matl114.hacks.modules.inv;

import com.google.common.util.concurrent.Runnables;
import java.util.Locale;
import java.util.OptionalInt;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.access.HandledScreenAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.hooks.ViaProtocols;
import me.matl114.managers.Configs;
import me.matl114.managers.TaskManagers;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.AttributeUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.collections.Point;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public class InvExtra extends BaseModule {
    public static InvExtra INSTANCE;
    public final ModulePath inventory = makePath(Configs.INV_CONFIG, "inventory");

    public InvExtra() {
        super("InvExtra");
        INSTANCE = this;
    }

    public final IntRef inventoryClickLimit = intBuilder(inventory.add("packet-limit"))
            .defaultValue(40)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef invGrimFix =
            flagBuilder(inventory.add("move-click-grim-fix")).build();

    public final FlagRef invSprintGrimFix =
            flagBuilder(inventory.add("sprint-click-grim-fix")).build();

    public final FlagRef expandInventory =
            flagBuilder(inventory.add("expand-backpack-inventory")).build();

    public final FlagRef ghostHandAttribute =
            flagBuilder(inventory.add("ghost-hand-attribute-sync")).build();

    public final KeyBindRef pickItemHotkey = hotkey(inventory.add("pick-item"))
            .defaultValue(new MultiKeyBind(KeyCode.KEY_LEFT_CONTROL, KeyCode.MOUSE_BUTTON_3))
            .registerHotkey(HotKeyUtils.asHandler(this::onPickItem))
            .build();

    public final ModulePath keepInv = inventory.add("keep-inv");
    public final FlagRef enableKeepInv = flagBuilder(keepInv).build();

    public static final String CLEAR_KEEP = "clear-keep";

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreClickSlot(), this::onClickSlot);
        registerListener(Listener.getPacketPoint().getChannel(CloseHandledScreenC2SPacket.class), this::onCloseScreen);
        TaskManagers.getToggleManager().register(TaskManagers.PREFIX_BUTTON_TOGGLE + "." + "keep-inv", enableKeepInv);
        TaskManagers.getTaskManager().register(TaskManagers.PREFIX_BUTTON_TASKS + "." + CLEAR_KEEP, this::clearKeep);
    }

    public void onClickSlot(Event<SlotActionType> event) {
        onInvClick(event.getArgs(0));
    }

    public void onInvClick(int syncId) {
        // check if it is manually clicked
        if (mc.currentScreen instanceof HandledScreen<?> handled && handled.getScreenHandler().syncId == syncId) {
            // do not fix all of them
            // some module may use MultiAction to gain advantage
            if (invSprintGrimFix.get()) {
                // fix GuiMove situation
                MovTasks.getMovExtra().sendSprintPacketsForInventoryAction();
            }
        }
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

    public void syncAttr() {
        if (ghostHandAttribute.get()) {
            AttributeUtils.updateAttribute(mc.player);
        }
    }

    public Runnable switchOrSwapInventoryIndexToHand(int hand) {
        int selected = InventoryUtils.getSelectedSlot();
        if (selected != hand) {
            if (hand < 9) {
                PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(hand);
                syncAttr();
                return () -> {
                    PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(selected);
                    syncAttr();
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
                        syncAttr();
                        return () -> {
                            MovTasks.getMovExtra().sendPacketsForInventoryAction();
                            mc.interactionManager.clickSlot(
                                    mc.player.currentScreenHandler.syncId,
                                    swapped,
                                    selected,
                                    SlotActionType.SWAP,
                                    mc.player);
                            syncAttr();
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
                    syncAttr();
                    return () -> {
                        MovTasks.getMovExtra().sendPacketsForInventoryAction();
                        mc.interactionManager.clickSlot(
                                mc.player.currentScreenHandler.syncId,
                                swapped,
                                selected,
                                SlotActionType.SWAP,
                                mc.player);
                        syncAttr();
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
                syncAttr();
                return () -> {
                    MovTasks.getMovExtra().sendPacketsForInventoryAction();
                    mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId, swapped, 40, SlotActionType.SWAP, mc.player);
                    syncAttr();
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

    public Runnable swapInventorySlotToHand(int slot) {
        int selected = InventoryUtils.getSelectedSlot();
        OptionalInt slotIndex = mc.player.currentScreenHandler.getSlotIndex(mc.player.getInventory(), selected);
        if (slotIndex.isPresent()) {
            return swapInventorySlots(slot, slotIndex.getAsInt());
        } else {
            return null;
        }
    }

    public Runnable swapInventorySlotToOffhand(int slot) {
        int selected = 40;
        OptionalInt slotIndex = mc.player.currentScreenHandler.getSlotIndex(mc.player.getInventory(), selected);
        if (slotIndex.isPresent()) {
            return swapInventorySlots(slot, slotIndex.getAsInt());
        } else {
            return null;
        }
    }

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
            syncAttr();
            return () -> {
                MovTasks.getMovExtra().sendPacketsForInventoryAction();
                mc.interactionManager.clickSlot(handler.syncId, armorSlot, target, SlotActionType.SWAP, mc.player);
                syncAttr();
            };
        } else {
            var armorSlotInstance = handler.slots.get(armorSlot);
            if (armorSlotInstance.inventory instanceof PlayerInventory
                    && (armorSlotInstance.getIndex() < 9 || armorSlotInstance.getIndex() == 40)) {
                int target = armorSlotInstance.getIndex();
                mc.interactionManager.clickSlot(handler.syncId, targetSlot, target, SlotActionType.SWAP, mc.player);
                syncAttr();
                return () -> {
                    MovTasks.getMovExtra().sendPacketsForInventoryAction();
                    mc.interactionManager.clickSlot(handler.syncId, targetSlot, target, SlotActionType.SWAP, mc.player);
                    syncAttr();
                };
            } else {
                // fuck, do not kick me.
                swapTwoIdiotSlot(handler, targetSlot, armorSlot);
                syncAttr();
                return () -> {
                    MovTasks.getMovExtra().sendPacketsForInventoryAction();
                    swapTwoIdiotSlot(handler, targetSlot, armorSlot);
                    syncAttr();
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
                        if (ViaFabricPlusHooks.getInstance().isViaEnabled()
                                && ViaFabricPlusHooks.getInstance()
                                        .getCurrentVersion()
                                        .isLowerOrEqualTo(21, 3)) {
                            // use via shit to send pickup packet
                            var wrapper = ViaFabricPlusHooks.getInstance().createViaPacket();
                            wrapper.writePacketType(
                                    ViaProtocols.V1_21_2_TO_1_21_4, "pick_item".toUpperCase(Locale.ROOT));
                            wrapper.write("VAR_INT", slot.getIndex());
                            wrapper.scheduleSendToServer(ViaProtocols.V1_21_2_TO_1_21_4, true);
                            Debug.chat("run pickup");
                        } else {
                            Debug.chat("No Longer support this feat in version "
                                    + ViaFabricPlusHooks.getInstance().getCurrentVersion());
                        }
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

    public void clearKeep() {

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            ClientPlayerAccess access = ClientPlayerAccess.of(player);
            access.clearKeepedInventory(true);
            Debug.chat(Text.literal("已清除界面历史记录"));
        }
    }
}
