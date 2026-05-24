package me.matl114.hacks.modules.combat;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Random;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.MovTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
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

    public AutoTotem() {}

    public final FlagRef enable = flagBuilder(totem.add("auto-totem")).build();

    public final EnumRef<Configs.AutoInvMode> mode = builder(totem.add("auto-totem-mode"), Configs.AutoInvMode.class)
            .defaultValue(Configs.AutoInvMode.LAZY)
            .build();

    public final FlagRef smartTotem = flagBuilder(totem.add("smart-auto-totem")).build();

    public final NBTRef<RegistryRegex<Item>> enableHandItems = builder(
                    totem.add("enable-hand-items"), NBTType.<RegistryRegex<Item>>parameter(RegistryRegex.class))
            .defaultValue(new RegistryRegex<>(new Regex("^()$"), Registries.ITEM))
            .build();

    public final FlagRef antiMiss = flagBuilder(totem.add("anti-miss")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onTick);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
        registerListener(Listener.getPacketPoint().getChannel(EntityStatusS2CPacket.class), this::onTotem);
    }

    private boolean canBeAccepted(ItemStack ex) {
        return ex.getItem() == Items.TOTEM_OF_UNDYING || enableHandItems.get().test(ex.getItem());
    }

    // todo: add legal mode (swap hand)
    public void onTick(Event<ClientPlayerEntity> ev) {
        var player = ev.context();
        if (enable.get()) {
            // ScreenHandler handled = ClientPlayerAccess.of(player).getServerScreenHandler();
            //            if( handled != player.playerScreenHandler){
            //                return;
            //            }
            switch (mode.get()) {
                case LAZY -> {
                    onTotemLazy();
                }
                case TICK -> {
                    onTotemTick();
                }
            }
        }
    }

    public void onTotemLazy() {
        if (!canBeAccepted(mc.player.getOffHandStack())) {
            if (smartTotem.get() && mc.player.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
                return;
            }
            // well looks
            int toSlot = (smartTotem.get()
                            && mc.player.getMainHandStack().isEmpty()
                            && !mc.player.getOffHandStack().isEmpty())
                    ? mc.player.getInventory().getSelectedSlot()
                    : 40;
            ScreenHandler handled = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
            List<Slot> slots = handled.slots;
            for (var i = 0; i < slots.size(); ++i) {
                if (slots.get(i).inventory instanceof PlayerInventory
                        && slots.get(i).getStack().getItem() == Items.TOTEM_OF_UNDYING) {
                    MovTasks.getMovExtra().sendPacketsForInventoryAction();
                    InvTasks.clickSlotAsync(i, toSlot, SlotActionType.SWAP);
                    return;
                }
            }
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
                if (slots.get(i).inventory instanceof PlayerInventory
                        && slots.get(i).getStack().getItem() == Items.TOTEM_OF_UNDYING
                        && i != 40) {
                    totemList.add(i);
                }
            }
            if (!totemList.isEmpty()) {
                int random = totemList.getInt(inventorRandom.nextInt(totemList.size()));
                MovTasks.getMovExtra().sendPacketsForInventoryAction();
                InvTasks.clickSlotAsync(random, 40, SlotActionType.SWAP);
            }
        }
    }

    public void onTotem(Event<EntityStatusS2CPacket> eventTotem) {
        if (checkNull()) return;
        if (enable.get()
                && antiMiss.get()
                && eventTotem.context.getStatus() == EntityStatuses.USE_TOTEM_OF_UNDYING
                && eventTotem.context.getEntity(mc.world) == mc.player) {
            ItemStack stackInMainHand = mc.player.getMainHandStack();
            ItemStack stackInOffHand = mc.player.getOffHandStack();
            int consumeSlot = stackInMainHand.getItem() == Items.TOTEM_OF_UNDYING
                    ? mc.player.getInventory().getSelectedSlot()
                    : 40;
            ScreenHandler handled = ClientPlayerAccess.of(mc.player).getServerScreenHandler();
            List<Slot> slots = handled.slots;
            for (var i = 0; i < slots.size(); ++i) {
                if (i != consumeSlot
                        && slots.get(i).inventory instanceof PlayerInventory
                        && slots.get(i).getStack().getItem() == Items.TOTEM_OF_UNDYING) {
                    MovTasks.getMovExtra().sendPacketsForInventoryAction();
                    InvTasks.clickSlotAsync(i, consumeSlot, SlotActionType.SWAP);
                    return;
                }
            }
        }
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        switch (event.context.getValue()) {
            case AC_GRIM, AC_GRIM_LEGACY, AC_MATRIX -> mode.set(Configs.AutoInvMode.LAZY);
            default -> mode.set(Configs.AutoInvMode.TICK);
        }
    }
}
