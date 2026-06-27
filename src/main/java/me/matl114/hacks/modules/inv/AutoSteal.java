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
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public class AutoSteal extends BaseModule {
    // todo:
    // title steal
    // item filter
    // 0 tick steal code
    // auto shulker dump
    // with hotkeys

    public final ModulePath autoInv = makePath(Configs.INV_CONFIG, "auto-inv");
    public final ModulePath steal = autoInv.add("steal");

    // ===== 配置字段 =====
    // 主开关（FlagRef），同时也作为模块启用标志，需调用 bindFlag 绑定
    public final FlagRef enable = flagBuilder(steal.add("enable")).build();

    // 主开关切换快捷键（toggleHotkey）
    public final KeyBindRef autoStealToggleKey = moduleEntry(
                    steal.add("toggle-key"),
                    new MultiKeyBind(), // 默认按键 R（可自定义）
                    steal.add("enable") // 关联的开关配置路径
                    )
            .build();

    // 标题正则（NBTRef 类型，存储正则表达式）
    public final NBTRef<Regex> titleRegex = builder(steal.add("title-regex"), Regex.class)
            .defaultValue(new Regex(".*")) // 默认匹配所有标题
            .build();

    // 物品过滤器（RegistryRegex 类型，基于物品注册表过滤）
    public final NBTRef<RegistryRegex<Item>> itemFilter = builder(steal.add("item-filter"), RegistryRegex.ITEM_TYPE)
            .defaultValue(new RegistryRegex<>(new Regex(".*"), Registries.ITEM))
            .build();

    public AutoSteal() {
        bindFlag(enable);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onInventoryTick);
    }

    public void onInventoryTick(Event<ClientPlayerEntity> event) {
        if (mc.currentScreen instanceof HandledScreen<?> handle && enable.get()) {
            Text text = handle.getTitle();
            String titleName = text == null ? "" : ChatUtils.textToPlainString(text);
            if (titleRegex.get().test(titleName)) {
                var screenHandler = handle.getScreenHandler();
                if (screenHandler != null
                        && !(screenHandler instanceof CreativeInventoryScreen.CreativeScreenHandler)) {
                    for (var slot : screenHandler.slots) {
                        if (slot.inventory instanceof PlayerInventory playerInventory) {
                            break;
                        } else {
                            // not a player inventory
                            ItemStack stack = slot.getStack();
                            if (!stack.isEmpty() && itemFilter.get().test(stack.getItem())) {
                                mc.interactionManager.clickSlot(
                                        screenHandler.syncId, slot.getIndex(), 0, SlotActionType.QUICK_MOVE, mc.player);
                            }
                        }
                    }
                }
            }
        }
    }
}
