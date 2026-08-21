package me.matl114.hacks.modules.combat;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Random;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.tasks.StateExecutor;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
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

    public final FlagRef smartTotem = flagBuilder(totem.add("smart-auto-totem")).build();

    public final NBTRef<EntrySet<Item>> enableHandItems = builder(
                    totem.add("enable-hand-items"), EntrySet.<Item>parameter())
            .defaultValue(new EntrySet<>(new Regex("^()$"), Registries.ITEM))
            .build();

    public final FlagRef antiMiss = flagBuilder(totem.add("anti-miss")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onTick);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
        registerListener(Listener.getPacketPoint().getChannel(EntityStatusS2CPacket.class), this::onTotem);
        registerListener(Listener.getPlayerInitConfiguration(), this::onPlayerInit);
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
        // stop from duplicate swap
        if (!swap.canRun(cooldown.get())) {
            return;
        }
        if (!canBeAccepted(mc.player.getOffHandStack())) {
            if (smartTotem.get() && mc.player.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
                return;
            }
            // well looks
            int toSlot = (smartTotem.get()
                            && mc.player.getMainHandStack().isEmpty()
                            && !mc.player.getOffHandStack().isEmpty())
                    ? InventoryUtils.getSelectedSlot()
                    : 40;
            ScreenHandler handled = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
            List<Slot> slots = handled.slots;
            for (var i = 0; i < slots.size(); ++i) {
                if ((slots.get(i).inventory instanceof PlayerInventory || handled == mc.player.playerScreenHandler)
                        && slots.get(i).getStack().getItem() == Items.TOTEM_OF_UNDYING) {
                    MovTasks.getMovExtra().sendPacketsForInventoryAction();
                    InvTasks.clickSlotAsync(i, toSlot, SlotActionType.SWAP);
                    Debug.debug("handle swap success");
                    handleTotemSwapSuccess();
                    return;
                }
            }
            handleTotemSwapFailure();
        }
    }

    public void onTotemTick() {
        if (!canBeAccepted(mc.player.getOffHandStack())) {
            onTotemLazy();
        } else {
            IntList totemList = new IntArrayList();
            ScreenHandler handled = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
            List<Slot> slots = handled.slots;
            for (var i = 0; i < slots.size(); ++i) {
                if ((slots.get(i).inventory instanceof PlayerInventory || handled == mc.player.playerScreenHandler)
                        && slots.get(i).getStack().getItem() == Items.TOTEM_OF_UNDYING
                        && i != 40) {
                    totemList.add(i);
                }
            }
            if (!totemList.isEmpty()) {
                int random = totemList.getInt(inventorRandom.nextInt(totemList.size()));
                MovTasks.getMovExtra().sendPacketsForInventoryAction();
                InvTasks.clickSlotAsync(random, 40, SlotActionType.SWAP);
                handleTotemSwapSuccess();
            } else {
                handleTotemSwapFailure();
            }
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
            int consumeSlot =
                    stackInMainHand.getItem() == Items.TOTEM_OF_UNDYING ? InventoryUtils.getSelectedSlot() : 40;
            mc.player.getInventory().setStack(consumeSlot, ItemStack.EMPTY);
            ScreenHandler handled = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
            List<Slot> slots = handled.slots;
            // revert usage to avoid conflict
            for (var i = slots.size() - 1; i >= 0; --i) {
                if (i != consumeSlot
                        && (slots.get(i).inventory instanceof PlayerInventory
                                || handled == mc.player.playerScreenHandler)
                        && slots.get(i).getStack().getItem() == Items.TOTEM_OF_UNDYING) {
                    MovTasks.getMovExtra().sendPacketsForInventoryAction();
                    InvTasks.clickSlotAsync(i, consumeSlot, SlotActionType.SWAP);
                    handleTotemSwapSuccess();
                    Debug.debug("handle antimiss success");
                    // pre tick
                    swap.mark(1);
                    return;
                }
            }
            handleTotemSwapFailure();
        }
    }

    public void onPlayerInit(Event<ClientPlayerEntity> eventPlayer) {
        noTotem.state(false);
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {}
}
