package me.matl114.hacks.modules.inv;

import java.util.function.Consumer;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.ACTasks;
import me.matl114.hacks.InvTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.config.RegistryRegex;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.config.NBTType;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.ChatUtils;
import me.matl114.utils.RaycastUtils;
import me.matl114.utils.ScreenUtils;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoubleBlockProperties;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

public class AutoSteal extends BaseModule {
    // todo:
    // title steal
    // item filter
    // 0 tick steal code
    // auto shulker dump
    // with hotkeys
    private static final String[] ENABLE_PATH = makePath("auto-inv.steal.enable");
    private static final String[] TOGGLE_KEY_PATH = makePath("auto-inv.steal.toggle-key");
    private static final String[] TITLE_REGEX_PATH = makePath("auto-inv.steal.title-regex");
    private static final String[] ITEM_FILTER_PATH = makePath("auto-inv.steal.item-filter");
    private static final String[] AUTO_SHULKER_PATH = makePath("auto-inv.auto-shulker.enable");
    private static final String[] AUTO_SHULKER_TOGGLE_KEY_PATH = makePath("auto-inv.auto-shulker.toggle-key");
    private static final String[] AUTO_SHULKER_0TICK_STEAL_PATH = makePath("auto-inv.auto-shulker.0tick-steal");

    // ===== 配置字段 =====
    // 主开关（FlagRef），同时也作为模块启用标志，需调用 bindFlag 绑定
    public final FlagRef enable = flagBuilder(Configs.INV_CONFIG, ENABLE_PATH).build();

    // 主开关切换快捷键（toggleHotkey）
    public final KeyBindRef autoStealToggleKey = toggleHotkey(
                    Configs.INV_CONFIG,
                    TOGGLE_KEY_PATH,
                    new MultiKeyBind(), // 默认按键 R（可自定义）
                    ENABLE_PATH // 关联的开关配置路径
                    )
            .build();

    // 标题正则（NBTRef 类型，存储正则表达式）
    public final NBTRef<Regex> titleRegex = builder(
                    Configs.INV_CONFIG, TITLE_REGEX_PATH, NBTType.<Regex>parameter(Regex.class))
            .defaultValue(new Regex(".*")) // 默认匹配所有标题
            .build();

    // 物品过滤器（RegistryRegex 类型，基于物品注册表过滤）
    public final NBTRef<RegistryRegex<Item>> itemFilter = builder(
                    Configs.INV_CONFIG, ITEM_FILTER_PATH, NBTType.<RegistryRegex<Item>>parameter(RegistryRegex.class))
            .defaultValue(new RegistryRegex<>(new Regex(".*"), Registries.ITEM))
            .build();

    // 自动潜影盒子功能开关
    public final FlagRef autoShulker =
            flagBuilder(Configs.INV_CONFIG, AUTO_SHULKER_PATH).build();

    // 自动潜影盒切换快捷键
    public final KeyBindRef autoShulkerToggleKey = toggleHotkey(
                    Configs.INV_CONFIG,
                    AUTO_SHULKER_TOGGLE_KEY_PATH,
                    new MultiKeyBind(), // 默认按键 H
                    AUTO_SHULKER_PATH // 关联自动潜影盒开关
                    )
            .build();

    // 0 Tick 偷取开关（潜影盒专用）
    public final FlagRef autoShulker0TickSteal =
            flagBuilder(Configs.INV_CONFIG, AUTO_SHULKER_0TICK_STEAL_PATH).build();

    public AutoSteal() {}

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreGameTick(), this::onInventoryTick);
        registerListener(
                Listener.getPacketPostSendPoint().getChannel(PlayerInteractBlockC2SPacket.class),
                this::onClickShulkerBoxOrPlaceShulkerBox);
    }

    private static boolean canShulkerOpen(BlockPos pos, BlockState state) {
        Box box = ShulkerEntity.calculateBoundingBox(
                        1.0F, (Direction) state.get(ShulkerBoxBlock.FACING), 0.0F, 0.5F, pos.toBottomCenterPos())
                .contract(1.0E-6);
        return mc.world.isSpaceEmpty(box);
    }

    public static int predictOpenVanillaContainerSize(BlockPos blockPos) {
        if (mc.world.getBlockEntity(blockPos) instanceof Inventory inventory) {
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
        // todo fix prediction initialization
        int nextPredictedIndex = (InvTasks.LAST_SYNC_ID % 100) + 1;
        ScreenHandler fakeScreenHandler =
                GenericContainerScreenHandler.createGeneric9x6(nextPredictedIndex, mc.player.getInventory());
        ScreenHandler handler = mc.player.currentScreenHandler;
        try {
            mc.player.currentScreenHandler = fakeScreenHandler;
            callback.accept(fakeScreenHandler);
        } finally {
            mc.player.currentScreenHandler = handler;
        }
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

    public void onClickShulkerBoxOrPlaceShulkerBox(Event<PlayerInteractBlockC2SPacket> event) {

        if (autoShulker.get()) {
            PlayerInteractBlockC2SPacket packet = event.context;
            BlockHitResult hitResult = packet.getBlockHitResult();
            boolean hasShift = mc.player.isSneaking();
            BlockState state = mc.world.getBlockState(hitResult.getBlockPos());
            if (state.getBlock() instanceof ShulkerBoxBlock && !hasShift) {
                int size = predictOpenVanillaContainerSize(hitResult.getBlockPos());
                // can open
                if (size > 0) {
                    // interact shulker
                    if (autoShulker0TickSteal.get()) {
                        executePredictInventoryAction(handler -> {
                            for (var i = 0; i < size; ++i) {
                                mc.interactionManager.clickSlot(
                                        handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                            }
                        });
                        int tick = Tasks.getTick();
                        // add timeout
                        ScreenUtils.getOpenScreenFuture()
                                .thenRunAsync(
                                        () -> {
                                            if (tick + 4 > Tasks.getTick()) {
                                                mc.player.closeHandledScreen();
                                            }
                                        },
                                        mc);
                    } else {
                        int tick = Tasks.getTick();
                        ScreenUtils.getOpenScreenFuture().thenRun(() -> {
                            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                                if (tick + 4 <= Tasks.getTick()) {
                                    return;
                                }
                                for (var i = 0; i < size; ++i) {
                                    mc.interactionManager.clickSlot(
                                            mc.player.currentScreenHandler.syncId,
                                            i,
                                            0,
                                            SlotActionType.QUICK_MOVE,
                                            mc.player);
                                }
                            }
                            mc.player.closeHandledScreen();
                        });
                    }
                }
            } else {
                if (hasShift) {
                    mc.player.setSneaking(false);
                    PlayerInputUtils.of(mc.player.input).sneak(false).sendPlayerSneakUpdatePacket();
                    ClientPlayerAccess.of(mc.player).resyncSneak();
                }
                BlockPos placedBlock = hitResult.getBlockPos().offset(hitResult.getSide());
                BlockState placedState = mc.world.getBlockState(placedBlock);

                if (placedState.getBlock() instanceof ShulkerBoxBlock) {
                    BlockHitResult hitResult1 = RaycastUtils.createHitResult(placedBlock);
                    // mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult1);
                    ACTasks.addPostTransactionAction((ch) -> {
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult1);
                    });
                }
            }
        }
    }
}
