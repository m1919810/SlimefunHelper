package me.matl114.hacks.modules.inv;

import me.matl114.hacks.InvTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.managers.Configs;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.input.KeyCode;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoubleBlockProperties;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.function.Consumer;

public class AutoSteal extends BaseModule {
    //todo:
    // title steal
    // item filter
    // 0 tick steal code
    // auto shulker dump
    // with hotkeys
    private static final String[] ENABLE_PATH = makePath("auto-steal.steal.enable");
    private static final String[] TOGGLE_KEY_PATH = makePath("auto-steal.steal.toggle-key");
    private static final String[] TITLE_REGEX_PATH = makePath("auto-steal.title-regex");
    private static final String[] ITEM_FILTER_PATH = makePath("auto-steal.item-filter");
    private static final String[] AUTO_SHULKER_PATH = makePath("auto-steal.auto-shulker.enable");
    private static final String[] AUTO_SHULKER_TOGGLE_KEY_PATH = makePath("auto-steal.auto-shulker.toggle-key");
    private static final String[] AUTO_SHULKER_0TICK_STEAL_PATH = makePath("auto-steal.auto-shulker.0tick-steal");

    // ===== 配置字段 =====
    // 主开关（FlagRef），同时也作为模块启用标志，需调用 bindFlag 绑定
    public final FlagRef enable = flagBuilder(Configs.INV_CONFIG, ENABLE_PATH)
        .build();

    // 主开关切换快捷键（toggleHotkey）
    public final KeyBindRef autoStealToggleKey = toggleHotkey(
        Configs.INV_CONFIG, TOGGLE_KEY_PATH,
        new MultiKeyBind(KeyCode.KEY_R),       // 默认按键 R（可自定义）
        ENABLE_PATH                             // 关联的开关配置路径
    ).build();

    // 标题正则（NBTRef 类型，存储正则表达式）
    public final NBTRef<Regex> titleRegex = builder(Configs.INV_CONFIG, TITLE_REGEX_PATH, NBTType.<Regex>parameter(Regex.class))
        .defaultValue(new Regex(".*"))          // 默认匹配所有标题
        .build();

    // 物品过滤器（RegistryRegex 类型，基于物品注册表过滤）
    public final NBTRef<RegistryRegex<Item>> itemFilter = builder(Configs.INV_CONFIG, ITEM_FILTER_PATH, NBTType.<RegistryRegex<Item>>parameter(RegistryRegex.class))
        .defaultValue(new RegistryRegex<>(
            new Regex(".*"), Registries.ITEM
        ))
        .build();

    // 自动潜影盒子功能开关
    public final FlagRef autoShulker = flagBuilder(Configs.INV_CONFIG, AUTO_SHULKER_PATH)
        .build();

    // 自动潜影盒切换快捷键
    public final KeyBindRef autoShulkerToggleKey = toggleHotkey(
        Configs.INV_CONFIG,
        AUTO_SHULKER_TOGGLE_KEY_PATH,
        new MultiKeyBind(KeyCode.KEY_H),        // 默认按键 H
        AUTO_SHULKER_PATH                       // 关联自动潜影盒开关
    ).build();

    // 0 Tick 偷取开关（潜影盒专用）
    public final FlagRef autoShulker0TickSteal = flagBuilder(Configs.INV_CONFIG, AUTO_SHULKER_0TICK_STEAL_PATH)
        .build();

    public AutoSteal() {

    }

    private static boolean canShulkerOpen(BlockPos pos, BlockState state) {
        Box box = ShulkerEntity.calculateBoundingBox(
                1.0F, (Direction) state.get(ShulkerBoxBlock.FACING), 0.0F, 0.5F, pos.toBottomCenterPos())
            .contract(1.0E-6);
        return mc.world.isSpaceEmpty(box);
    }

    public static int predictOpenVanillaContainerSize(BlockPos blockPos) {
        if(mc.world.getBlockEntity(blockPos) instanceof Inventory inventory){
            int size = inventory.size();
            if (inventory instanceof ChestBlockEntity chest) {
                BlockState state = chest.getCachedState();
                if (state.getBlock() instanceof ChestBlock chestBlock) {
                    if (ChestBlock.isChestBlocked(mc.world, blockPos)) {
                        size = 0;
                    } else if (ChestBlock.getDoubleBlockType(state) != DoubleBlockProperties.Type.SINGLE) {
                        size = 54;
                    }
                }
            }
            if (inventory instanceof ShulkerBoxBlockEntity shulker) {
                BlockState state = shulker.getCachedState();
                if (shulker.getAnimationStage() == ShulkerBoxBlockEntity.AnimationStage.CLOSED
                    && !canShulkerOpen(blockPos, state)) {
                    size = 0;
                }
            }
            return size;
        }
        return 0;
    }


    public static void executePredictInventoryAction(Consumer<ScreenHandler> callback) {
        int nextPredictedIndex = (InvTasks.LAST_SYNC_ID % 100) + 1;
        ScreenHandler fakeScreenHandler =
            GenericContainerScreenHandler.createGeneric9x6(
                nextPredictedIndex, mc.player.getInventory());
        ScreenHandler handler = mc.player.currentScreenHandler;
        try {
            mc.player.currentScreenHandler = fakeScreenHandler;
            callback.accept(fakeScreenHandler);
        } finally {
            mc.player.currentScreenHandler = handler;
        }
    }
}
