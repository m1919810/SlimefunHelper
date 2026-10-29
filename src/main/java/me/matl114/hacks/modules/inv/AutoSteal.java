package me.matl114.hacks.modules.inv;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.config.EntryPrimitiveMap;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class AutoSteal extends BaseModule {
    public final ModulePath autoInv = makePath(Configs.INV_CONFIG, "auto-inv");
    public final ModulePath steal = autoInv.add("steal");

    public final FlagRef enable = flagBuilder(steal.add("enable")).build();

    public final KeyBindRef hotkey = hotkey(steal.add("hotkey"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.asNonInputHandler(this::stealCurrentScreen))
            .build();

    public final NBTRef<EntrySet<Item>> blacklistItems = builder(
                    steal.add("blacklist-items"), EntrySet.<Item>parameter())
            .defaultValue(new EntrySet<>(Registries.ITEM, Set.of()))
            .build();

    public final NBTRef<EntryPrimitiveMap<Item, Double>> itemPriorities = builder(
                    steal.add("item-priorities"), EntryPrimitiveMap.<Item, Double>parameter())
            .defaultValue(new EntryPrimitiveMap<>(Registries.ITEM, NBTTypes.DOUBLE_TYPE, Map.of(), 0.0D))
            .build();

    public final FlagRef supplyAsKit = flagBuilder(steal.add("supply-as-kit")).build();
    public final FlagRef openOnceOnly = flagBuilder(steal.add("open-once-only")).build();

    private ScreenHandler activeHandler;
    private boolean cycleFinished;
    /** Replaced on every inventory refresh so queued tasks from the old refresh become stale. */
    private AtomicInteger executingTask = new AtomicInteger();

    public AutoSteal() {
        super("AutoSteal");
        bindFlag(enable);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onInventoryTick);
        registerListener(
                Listener.getPacketPostHandlePoint().getChannel(InventoryS2CPacket.class), this::onInventoryReset);
    }

    public void onInventoryTick(Event<ClientPlayerEntity> event) {
        if (enable.get()) {
            stealCurrentScreen();
        } else {
            reset();
        }
    }

    public void onInventoryReset(Event<InventoryS2CPacket> event) {
        // Invalidate queued slot actions without ending the current screen's one-time cycle.
        executingTask = new AtomicInteger();
    }

    private boolean stealCurrentScreen() {
        if (mc.player == null) {
            return false;
        }
        if (!(InvTasks.getCurrentServerScreen(mc.player) instanceof HandledScreen<?> screen)
                || screen instanceof CreativeInventoryScreen) {
            resetIfClosed();
            return false;
        }

        ScreenHandler handler = screen.getScreenHandler();
        if (handler != activeHandler) {
            reset();
            activeHandler = handler;
        }
        if (openOnceOnly.get() && cycleFinished) {
            return false;
        }
        AtomicInteger taskCounter = executingTask;
        if (taskCounter.get() > 0) {
            return false;
        }

        Map<KitReplenish.ReplenishTemplate, Integer> targetCounts = supplyAsKit.get() ? getKitTargetCounts() : Map.of();
        Map<KitReplenish.ReplenishTemplate, Integer> inventoryCounts =
                supplyAsKit.get() ? getPlayerItemCounts() : Map.of();
        boolean supply = supplyAsKit.get();
        List<Slot> candidates = handler.slots.stream()
                .filter(slot -> !(slot.inventory instanceof PlayerInventory))
                .filter(slot -> !slot.getStack().isEmpty())
                .filter(slot -> !blacklistItems.get().test(slot.getStack().getItem()))
                .filter(slot -> shouldTake(slot.getStack(), targetCounts, inventoryCounts))
                .sorted(Comparator.comparingDouble(
                                (Slot slot) -> priorityOf(slot.getStack().getItem()))
                        .reversed())
                .toList();

        for (Slot slot : candidates) {
            ItemStack template = slot.getStack().copy();
            int amount = supply
                    ? Math.min(
                            template.getCount(),
                            Math.max(
                                    0,
                                    targetCounts.getOrDefault(KitReplenish.ReplenishTemplate.of(template), 0)
                                            - inventoryCounts.getOrDefault(
                                                    KitReplenish.ReplenishTemplate.of(template), 0)))
                    : template.getCount();
            if (amount <= 0) {
                continue;
            }
            taskCounter.incrementAndGet();
            int slotIndex = handler.slots.indexOf(slot);
            InvTasks.getClickExecutor()
                    .execute(() -> executeStealTask(handler, taskCounter, slotIndex, template, amount, supply) > 0);
        }

        if (openOnceOnly.get() && taskCounter.get() == 0) {
            cycleFinished = true;
        }
        return true;
    }

    private int executeStealTask(
            ScreenHandler handler,
            AtomicInteger taskCounter,
            int slotIndex,
            ItemStack template,
            int requestedAmount,
            boolean supply) {
        try {
            // A refresh replaces executingTask. The task must not touch the new generation.
            if (taskCounter != executingTask
                    || activeHandler != handler
                    || mc.player == null
                    || mc.interactionManager == null
                    || InvTasks.getCurrentServerScreenHandler(mc.player) != handler
                    || slotIndex < 0
                    || slotIndex >= handler.slots.size()) {
                return 0;
            }
            ItemStack current = handler.getSlot(slotIndex).getStack();
            if (current.isEmpty()
                    || !ItemStack.areItemsAndComponentsEqual(current, template)
                    || blacklistItems.get().test(current.getItem())) {
                return 0;
            }
            if (!supply) {
                InvTasks.clickSlotAsync(slotIndex, 0, SlotActionType.QUICK_MOVE);
                return current.getCount();
            }

            KitReplenish.ReplenishTemplate templateKey = KitReplenish.ReplenishTemplate.of(current);
            int missing = getKitTargetCounts().getOrDefault(templateKey, 0)
                    - getPlayerItemCounts().getOrDefault(templateKey, 0);
            int amount = Math.min(requestedAmount, Math.min(current.getCount(), missing));
            if (amount <= 0) {
                return 0;
            }
            if (amount >= current.getCount()) {
                InvTasks.clickSlotAsync(slotIndex, 0, SlotActionType.QUICK_MOVE);
                return current.getCount();
            }

            Slot destination = findPlayerDestination(handler, current);
            if (destination == null) {
                return 0;
            }
            int destinationIndex = handler.slots.indexOf(destination);
            int sourceCount = current.getCount();
            int transferAmount = Math.min(amount, destinationCapacity(destination, current));
            InvTasks.moveStackFromToAmount(handler, slotIndex, destinationIndex, transferAmount);
            return Math.max(
                    0, sourceCount - handler.getSlot(slotIndex).getStack().getCount());
        } finally {
            // Always release the count held by this task, including stale and failed tasks.
            taskCounter.decrementAndGet();
        }
    }

    private Slot findPlayerDestination(ScreenHandler handler, ItemStack source) {
        for (Slot slot : handler.slots) {
            if (slot.inventory instanceof PlayerInventory
                    && slot.canInsert(source)
                    && destinationCapacity(slot, source) > 0) {
                return slot;
            }
        }
        return null;
    }

    private int destinationCapacity(Slot destination, ItemStack source) {
        ItemStack current = destination.getStack();
        if (current.isEmpty()) {
            return source.getMaxCount();
        }
        if (!ItemStack.areItemsAndComponentsEqual(current, source)) {
            return 0;
        }
        return current.getMaxCount() - current.getCount();
    }

    private boolean shouldTake(
            ItemStack stack,
            Map<KitReplenish.ReplenishTemplate, Integer> targetCounts,
            Map<KitReplenish.ReplenishTemplate, Integer> inventoryCounts) {
        KitReplenish.ReplenishTemplate template = KitReplenish.ReplenishTemplate.of(stack);
        return !supplyAsKit.get() || targetCounts.getOrDefault(template, 0) > inventoryCounts.getOrDefault(template, 0);
    }

    private double priorityOf(Item item) {
        return itemPriorities.get().getEntryValueOr(item, 0.0D);
    }

    private Map<KitReplenish.ReplenishTemplate, Integer> getKitTargetCounts() {
        Map<KitReplenish.ReplenishTemplate, Integer> counts = new HashMap<>();
        if (KitReplenish.INSTANCE == null) {
            return counts;
        }
        KitReplenish.Kit kit = KitReplenish.INSTANCE.getCurrentKit();
        if (kit == null) {
            return counts;
        }
        int maxSize = Math.min(kit.maxSize(), 36);
        int from = Math.clamp(kit.rule().from(), 0, maxSize);
        int to = Math.clamp(kit.rule().to(), from, maxSize);
        for (var entry : kit.toItem()) {
            if (entry.index() >= from && entry.index() < to) {
                counts.merge(
                        KitReplenish.ReplenishTemplate.of(entry.val()),
                        entry.val().getCount(),
                        Integer::sum);
            }
        }
        return counts;
    }

    private Map<KitReplenish.ReplenishTemplate, Integer> getPlayerItemCounts() {
        Map<KitReplenish.ReplenishTemplate, Integer> counts = new HashMap<>();
        if (mc.player != null) {
            PlayerInventory inventory = mc.player.getInventory();
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.getStack(i);
                if (!stack.isEmpty()) {
                    counts.merge(KitReplenish.ReplenishTemplate.of(stack), stack.getCount(), Integer::sum);
                }
            }
        }
        return counts;
    }

    private void resetIfClosed() {
        if (mc.player == null || InvTasks.getCurrentServerScreen(mc.player) == null) {
            reset();
        }
    }

    private void reset() {
        activeHandler = null;
        cycleFinished = false;
        executingTask = new AtomicInteger();
    }
}
