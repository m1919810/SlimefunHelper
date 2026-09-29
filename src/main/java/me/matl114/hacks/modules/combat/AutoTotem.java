package me.matl114.hacks.modules.combat;

import java.util.List;
import java.util.Random;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.config.NBTTypes;
import me.matl114.hacks.utils.config.OptionalPrimitive;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.tasks.StateExecutor;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.InventoryUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotem extends BaseModule {
    private final Random inventorRandom = new Random();
    public final ModulePath totem = makePath(Configs.COMBAT_CONFIG, "totem");

    public AutoTotem() {
        super("AutoTotem");
        bindFlag(enable);
    }

    public final FlagRef enable = flagBuilder(totem.add("auto-totem")).build();

    public final KeyBindRef hotkey = moduleEntry(
                    totem.add("auto-totem-hotkey"), new MultiKeyBind(), totem.add("auto-totem"))
            .build();

    public final FlagRef log = builder(totem.add("auto-totem-log"), Boolean.class)
            .defaultValue(true)
            .build();

    public final IntRef cooldown = builder(totem.add("totem-swap-cooldown"), IntRef.TYPE)
            .defaultValue(1)
            .build();

    public final NBTRef<OptionalPrimitive<Integer>> forcedHotbarSlot = builder(
                    totem.add("auto-totem-hotbar-slot"), OptionalPrimitive.INT_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.INT_TYPE, 0))
            .validator(slot -> slot.getValue() >= 0 && slot.getValue() < 9)
            .build();

    public final FlagRef mainHandTotem = builder(totem.add("main-hand-totem"), Boolean.class)
            .defaultValue(true)
            .build();

    public final NBTRef<EntrySet<Item>> enableHandItems = builder(
                    totem.add("enable-hand-items"), EntrySet.<Item>parameter())
            .defaultValue(new EntrySet<>(new Regex("^()$"), Registries.ITEM))
            .build();

    public final FlagRef antiMiss = flagBuilder(totem.add("anti-miss")).build();

    private int forcedHotbarPreviousSlot = -1;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onTick);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
        registerListener(Listener.getPacketPoint().getChannel(EntityStatusS2CPacket.class), this::onTotem);
        registerListener(Listener.getPlayerRespawnPoint(), this::onPlayerInit);
        registerListener(Listener.getPlayerScrollHotBar(), this::onTotemMainHand);
    }

    private boolean canBeAccepted(ItemStack ex) {
        return ex.getItem() == Items.TOTEM_OF_UNDYING || enableHandItems.get().test(ex.getItem());
    }

    StateExecutor noTotem = new StateExecutor();
    // todo: add legal mode (swap hand)
    public void onTick(Event<ClientPlayerEntity> ev) {
        if (enable.get()) {
            onTotemLazy();
        }
    }

    private void handleTotemSwapFailure() {
        noTotem.state(true, () -> {
            if (log.get()) {
                logI18N("message.module.auto-totem.not-found");
            }
        });
    }

    //    int lastStartSwap114514 = 0;
    //    int swapCnt1919810 = 0;
    TimerExecutor swapFrequency = new TimerExecutor();
    int swapCntCounter = 0;
    int reswapCounter = 0;

    private void handleTotemSwapSuccess() {
        noTotem.state(false);
        swap.mark();
        swapFrequency.run(20, () -> swapCntCounter = 0);
        if (++swapCntCounter > 5) {
            swapCntCounter = 0;
            if (log.get()) {
                logI18N("message.module.auto-totem.swap-too-frequent");
            }
        }
    }

    public void onTotemLazy() {
        int forcedSlot = getForcedHotbarSlot();

        // stop from duplicate swap
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            restoreHotBar(forcedSlot, false);
            restoreForcedHotbar();
            return;
        }
        if (!canBeAccepted(mc.player.getOffHandStack())) {
            // well looks
            int toSlot = 40;
            if (!swap.canRun(cooldown.get())) {
                return;
            }
            swapTo(toSlot);
            if (!swap.canRun(cooldown.get())) {
                return;
            }
            if (forcedSlot >= 0) {
                restoreHotBar(forcedSlot, false);
            }
        } else {
            // not totem
            if (forcedSlot >= 0) {
                restoreHotBar(forcedSlot, true);
                if (mainHandTotem.get()
                        && mc.player.getInventory().getStack(forcedSlot).getItem() == Items.TOTEM_OF_UNDYING
                        && InventoryUtils.getSelectedSlot() != forcedSlot) {
                    if (++reswapCounter > 1) {
                        forcedHotbarPreviousSlot = InventoryUtils.getSelectedSlot();
                        PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(forcedSlot);
                        reswapCounter = 0;
                    }
                } else {
                    reswapCounter = 0;
                }
            } else if (mc.player.getMainHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                if (mainHandTotem.get()) {
                    int toSlot = InventoryUtils.getSelectedSlot();
                    if (!swap.canRun(cooldown.get())) {
                        return;
                    }
                    swapTo(toSlot);
                }
            }
        }
    }

    public void onTotemMainHand(Event<Integer> event) {
        if (event.isCancelled()) return;
        int forcedSlot;
        if (enable.get() && mainHandTotem.get() && (forcedSlot = getForcedHotbarSlot()) >= 0) {
            if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
                return;
            }
            if (canBeAccepted(mc.player.getOffHandStack())
                    && mc.player.getInventory().getStack(forcedSlot).getItem() == Items.TOTEM_OF_UNDYING
                    && event.context != forcedSlot) {
                if (InventoryUtils.getSelectedSlot() != forcedSlot) {
                    event.context(forcedSlot);
                } else {
                    event.cancel();
                }
            }
        }
    }

    private int getForcedHotbarSlot() {
        OptionalPrimitive<Integer> forcedSlot = forcedHotbarSlot.get();
        return forcedSlot.isPresent() && forcedSlot.getValue() >= 0 && forcedSlot.getValue() < 9
                ? forcedSlot.getValue()
                : -1;
    }

    private void restoreHotBar(int forcedSlot, boolean forceTotem) {
        if (forcedSlot < 0 || forcedSlot >= 9) {
            return;
        }
        ItemStack item = mc.player.getInventory().getStack(forcedSlot);
        boolean hasTotem = forceTotem ? item.getItem() == Items.TOTEM_OF_UNDYING : canBeAccepted(item);
        if (!hasTotem) {
            if (swap.canRun(cooldown.get())) {
                swapTo(forcedSlot);
            }
            return;
        }
    }

    private void restoreForcedHotbar() {
        if (forcedHotbarPreviousSlot < 0) {
            return;
        }
        int previousSlot = forcedHotbarPreviousSlot;
        forcedHotbarPreviousSlot = -1;
        if (!checkNull()) {
            PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(previousSlot);
        }
    }

    private void swapTo(int toSlot) {
        ScreenHandler handled = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
        List<Slot> slots = handled.slots;
        int offHandSlot = 40;
        boolean hasTotem = false;
        for (var i = 0; i < slots.size(); ++i) {
            Slot slot = slots.get(i);
            if ((slot.inventory instanceof PlayerInventory || handled == mc.player.playerScreenHandler)
                    && slot.getStack().getItem() == Items.TOTEM_OF_UNDYING) {
                hasTotem = true;
                if (slot.inventory instanceof PlayerInventory
                        && (slot.getIndex() == offHandSlot
                                || slot.getIndex() == toSlot
                                || slot.getIndex() < 0
                                || slot.getIndex() > InventoryUtils.getPlayerInvSize())) {
                    continue;
                }
                MovTasks.getMovExtra().sendPacketsForInventoryAction();
                InvTasks.clickSlotAsync(i, toSlot, SlotActionType.SWAP);
                handleTotemSwapSuccess();
                return;
            }
        }
        if (!hasTotem) {
            handleTotemSwapFailure();
        } else {
            handleTotemSwapSuccess();
        }
    }

    TimerExecutor swap = new TimerExecutor();

    public void onTotem(Event<EntityStatusS2CPacket> eventTotem) {
        if (checkNull()) return;
        if (enable.get()
                && antiMiss.get()
                && eventTotem.context.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING
                && eventTotem.context.getEntity(mc.world) == mc.player) {
            ItemStack stackInMainHand = mc.player.getMainHandStack();
            boolean find = false;
            int consumeSlot =
                    stackInMainHand.getItem() == Items.TOTEM_OF_UNDYING ? InventoryUtils.getSelectedSlot() : 40;
            mc.player.getInventory().setStack(consumeSlot, ItemStack.EMPTY);
            ScreenHandler handled = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
            List<Slot> slots = handled.slots;
            // revert usage to avoid conflict
            for (var i = slots.size() - 1; i >= 0; --i) {
                Slot slot = slots.get(i);
                if (i != consumeSlot
                        && (slot.inventory instanceof PlayerInventory || handled == mc.player.playerScreenHandler)
                        && slot.getStack().getItem() == Items.TOTEM_OF_UNDYING) {
                    find = true;
                    if ((slot.inventory instanceof PlayerInventory
                            && (slot.getIndex() == 40
                                    || slot.getIndex() == getForcedHotbarSlot()
                                    || slot.getIndex() < 0
                                    || slot.getIndex() > InventoryUtils.getPlayerInvSize()))) {
                        continue;
                    }
                    MovTasks.getMovExtra().sendPacketsForInventoryAction();
                    InvTasks.clickSlotAsync(i, consumeSlot, SlotActionType.SWAP);
                    handleTotemSwapSuccess();
                    // pre tick
                    swap.mark(1);
                    return;
                }
            }
            if (!find) {
                handleTotemSwapFailure();
            } else {
                handleTotemSwapSuccess();
            }
        }
    }

    public void onPlayerInit(Event<ClientPlayerEntity> eventPlayer) {
        noTotem.state(false);
        forcedHotbarPreviousSlot = -1;
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {}
}
