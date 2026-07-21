package me.matl114.hacks.modules.inv;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.Debug;
import me.matl114.utils.InventoryUtils;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public class NoQDrop extends BaseModule {
    public NoQDrop() {
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.INV_CONFIG, "inv-utils.no-q-drop");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            toggleHotkey(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final FlagRef forceEquipment =
            flagBuilder(root.add("force-equipment")).build();

    public final NBTRef<RegistryRegex<Item>> whiteListItem = builder(
                    root.add("white-list-items"), RegistryRegex.<Item>parameter())
            .defaultValue(new RegistryRegex<>(
                    new Regex("^(.*diamond.*|.*netherite.*|elytra|mace|.*sword)$"), Registries.ITEM))
            .build();

    public final FlagRef log = flagBuilder(root.add("log-to-player")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPlayerDropSelectedItem(), this::onPlayerDropAction);
    }

    public void onPlayerDropAction(Event<Boolean> eventDrop) {
        if (enable.get()) {
            ItemStack stack = InventoryUtils.getSelectedItem().val();
            if ((forceEquipment.get() && stack.isDamageable())
                    || whiteListItem.get().test(stack.getItem())) {
                if (log.get()) {
                    Debug.chat(ChatUtils.stringToText("&c[NoQDrop] &fCancel dropping"), stack.getName());
                }
                eventDrop.cancel();
            }
        }
    }
}
