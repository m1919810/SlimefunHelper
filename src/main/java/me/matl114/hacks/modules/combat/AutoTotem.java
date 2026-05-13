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
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotem extends BaseModule {
    private final Random inventorRandom = new Random();
    public static final String[] AUTO_TOTEM = {"totem", "auto-totem"};
    public static final String[] TOTEM_MODE = {"totem", "auto-totem-mode"};

    public AutoTotem() {}

    public final FlagRef enable = flagBuilder(Configs.COMBAT_CONFIG, AUTO_TOTEM).build();

    public final EnumRef<Configs.AutoInvMode> mode = builder(Configs.COMBAT_CONFIG, Configs.AutoInvMode.class)
            .path(TOTEM_MODE)
            .defaultValue(Configs.AutoInvMode.LAZY)
            .build();

    public final FlagRef smartTotem = flagBuilder(Configs.COMBAT_CONFIG, makePath("totem.smart-auto-totem"))
            .build();

    public final NBTRef<RegistryRegex<Item>> enableHandItems = builder(
                    Configs.COMBAT_CONFIG,
                    makePath("totem.enable-hand-items"),
                    NBTType.<RegistryRegex<Item>>parameter(RegistryRegex.class))
            .defaultValue(new RegistryRegex<>(new Regex("^()$"), Registries.ITEM))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onTick);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
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
            if (smartTotem.get() && canBeAccepted(mc.player.getMainHandStack())) {
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

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        switch (event.context.getValue()) {
            case AC_GRIM, AC_GRIM_LEGACY, AC_MATRIX -> mode.set(Configs.AutoInvMode.LAZY);
            default -> mode.set(Configs.AutoInvMode.TICK);
        }
    }
}
