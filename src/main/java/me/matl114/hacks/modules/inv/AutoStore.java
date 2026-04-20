package me.matl114.hacks.modules.inv;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.managers.Configs;
import me.matl114.managers.TaskManagers;
import me.matl114.managers.config.FlagRef;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;

public class AutoStore extends BaseModule {
    // do it later
    public AutoStore() {}

    public static final String[] AUTO_STORE = {"auto-inv", "auto-store", "enable"};
    public final FlagRef enable = flagBuilder(Configs.INV_CONFIG, AUTO_STORE).build();

    @Override
    public void registerAll() {
        super.registerAll();
        TaskManagers.getToggleManager().register(TaskManagers.PREFIX_BUTTON_TOGGLE + "." + "auto-store", enable);
        registerListener(Listener.getPreGameTick(), this::onTick);
    }
    // todo: rewrite this
    public void onTick(Event<ClientPlayerEntity> event) {
        if (enable.get()) {
            var player = event.context();
            Screen screen = InvTasks.getCurrentServerScreen(player);
            if (screen instanceof HandledScreen<?> handledScreen) {
                var handler = handledScreen.getScreenHandler();
                if (handledScreen instanceof CreativeInventoryScreen || handledScreen instanceof InventoryScreen) {
                    return;
                }

                IntList inputSlot = new IntArrayList();
                IntList outputSlot = new IntArrayList();
                for (int i = 0; i < handler.slots.size(); ++i) {
                    Slot slot = handler.slots.get(i);
                    if (slot.inventory instanceof PlayerInventory) {
                        inputSlot.add(i);
                    } else {
                        outputSlot.add(i);
                    }
                }

                for (int i : inputSlot) {
                    ItemStack stack = handler.slots.get(i).getStack();
                    // left one is enough
                    // left two please
                    if (stack != null && !stack.isEmpty() && stack.getCount() >= 4) {
                        // when trying to remove full stack, ensure that cursor is empty
                        if (!handler.getCursorStack().isEmpty()) {
                            ItemStack stackt = handler.getCursorStack();
                            int slot = anyMatch(handler.slots, stackt, stackt.getCount(), outputSlot.toIntArray());
                            if (slot >= 0) {
                                InvTasks.getClickExecutor().execute(() -> {
                                    mc.interactionManager.clickSlot(
                                            handledScreen.getScreenHandler().syncId,
                                            slot,
                                            0,
                                            SlotActionType.PICKUP,
                                            player);
                                });
                            } else {
                                InvTasks.getClickExecutor().execute(() -> {
                                    mc.interactionManager.clickSlot(
                                            handledScreen.getScreenHandler().syncId,
                                            slot,
                                            0,
                                            SlotActionType.THROW,
                                            player);
                                });
                            }
                            return;
                        }
                        // cursor is empty, we can execute transform
                        int toTransfer = (stack.getCount() + 1) / 2;
                        int slot = anyMatch(handler.slots, stack, toTransfer, outputSlot.toIntArray());
                        if (slot >= 0) {

                            InvTasks.getClickExecutor().execute(() -> {
                                mc.interactionManager.clickSlot(
                                        handledScreen.getScreenHandler().syncId, i, 1, SlotActionType.PICKUP, player);
                                mc.interactionManager.clickSlot(
                                        handledScreen.getScreenHandler().syncId,
                                        slot,
                                        0,
                                        SlotActionType.PICKUP,
                                        player);
                            });
                            return;
                        }
                    }
                }
            }
        }
    }

    private static int anyMatch(DefaultedList<Slot> slots, ItemStack stack, int amount, int... index) {
        for (int i : index) {
            ItemStack stack2 = slots.get(i).getStack();
            // can place stack with amount on it,
            if (stack2 != null
                    && (stack2.isEmpty()
                            || stack2.getCount() + amount <= stack2.getMaxCount()
                                    && ItemStack.areItemsAndComponentsEqual(stack, stack2))) {
                return i;
            }
        }
        return -1;
    }
}
