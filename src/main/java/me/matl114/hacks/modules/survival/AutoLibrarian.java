package me.matl114.hacks.modules.survival;

import com.mojang.datafixers.util.Pair;
import java.util.*;
import java.util.function.Consumer;
import me.matl114.accessors.access.MerchantScreenAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.gui.basic.ButtonAction;
import me.matl114.gui.basic.DrawableWidget;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.combat.TargetSelector;
import me.matl114.hacks.modules.interact.Interact;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.utils.config.*;
import me.matl114.hacks.utils.move.AdjustmentSchedular;
import me.matl114.hacks.utils.move.PathingSchedular;
import me.matl114.hacks.utils.move.goal.*;
import me.matl114.hacks.utils.render.RenderCollectors;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.render.RenderCollector;
import net.minecraft.block.*;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;

public class AutoLibrarian extends BaseModule {

    public AutoLibrarian() {
        super("AutoLibrarian");
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.SURVIVAL_CONFIG, "survival-mine-utils.auto-librarian");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final NBTRef<WeakEntryPrimitiveMap<Enchantment, Integer>> enchantments = builder(
                    root.add("enchantments"), WeakEntryPrimitiveMap.<Enchantment, Integer>parameter())
            .defaultValue(new WeakEntryPrimitiveMap<>(
                    RegistryKeys.ENCHANTMENT, NBTTypes.INT_TYPE, Map.of(Enchantments.MENDING.getValue(), 1)))
            .build();

    public final FlagRef onlyMaxLeve =
            builder(root.add("only-max-leve"), Boolean.class).defaultValue(true).build();

    public final FlagRef log = flagBuilder(root.add("log")).build();

    public final NBTRef<EntrySet<Block>> workstationPredicate = builder(
                    root.add("work-station-down-block"), EntrySet.<Block>parameter())
            .defaultValue(new EntrySet<>(Registries.BLOCK, List.of(Blocks.MAGMA_BLOCK, Blocks.OAK_FENCE)))
            .build();

    public final FlagRef autoLockTrade =
            flagBuilder(root.add("auto-lock-trade")).build();

    public final FlagRef baritoneControl =
            flagBuilder(root.add("baritone-control")).build();

    public final FlagRef adjustmentControl =
            flagBuilder(root.add("adjustment-control")).build();

    public final FlagRef render = flagBuilder(root.add("render")).build();

    public final NBTRef<WrapColor> renderColor = builder(root.add("render-color"), WrapColor.class)
            .defaultValue(new WrapColor((Formatting.GREEN)))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
    }

    final PathingSchedular pathingSchedular = new PathingSchedular();
    final AdjustmentSchedular adjustmentSchedular = new AdjustmentSchedular();

    @Override
    public void addCustomWidgets(Consumer<DrawableWidget> acceptor, int dx, int dy, int dblank) {
        super.addCustomWidgets(acceptor, dx, dy, dblank);
        acceptor.accept(createTitleLabel("widget.interact.interact-all.use-argument", 0, dblank, dx, dy));
        if (mc.getNetworkHandler() != null) {
            acceptor.accept(createExecuteButton(
                    "widget.auto-librarian.set-min-price",
                    ButtonAction.run(this::setLowestPriceForAllEnchantments),
                    0,
                    dblank,
                    dx,
                    dy));
        }
    }

    public void setLowestPriceForAllEnchantments() {
        var handler = mc.getNetworkHandler();
        if (handler == null) return;
        Map<Identifier, Integer> map = enchantments.get().idMap();
        Map<Identifier, Integer> map2 = new LinkedHashMap<>();
        for (var re : map.entrySet()) {
            if (Objects.equals(WeakHolder.DEFAULT_KEY, re.getKey())) {
                map2.put(re.getKey(), re.getValue());
            } else {
                RegistryKey<Enchantment> registryKey = RegistryKey.of(RegistryKeys.ENCHANTMENT, re.getKey());
                RegistryEntry<Enchantment> entry =
                        RegistryUtils.getRegistryEntry(handler.getRegistryManager(), registryKey);
                if (entry == null) {
                    map2.put(re.getKey(), re.getValue());
                } else {
                    Enchantment ench = entry.value();
                    int minLevel = 2 + 3 * ench.getMaxLevel();
                    if (entry.isIn(EnchantmentTags.DOUBLE_TRADE_PRICE)) {
                        minLevel *= 2;
                    }
                    map2.put(re.getKey(), minLevel);
                }
            }
        }
        enchantments.set(new WeakEntryPrimitiveMap<>(RegistryKeys.ENCHANTMENT, NBTTypes.INT_TYPE, map2));
    }

    VillagerEntity targetVillager;
    BlockPos targetWorkStationBase;

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        clearTarget();
    }

    private void clearTarget() {
        targetVillager = null;
        targetWorkStationBase = null;
        hasOpened = false;
        lastMerchantScreenSyncId = -1;
        pathingSchedular.disable();
    }

    private boolean isLowLevelOrNoProfessionVillager(VillagerEntity villager) {
        VillagerData villagerData = villager.getVillagerData();
        if (villagerData != null) {
            var profession = villagerData.profession().getKey().orElse(null);
            if (Objects.equals(profession, VillagerProfession.NONE)) {
                return true;
            }
            if (Objects.equals(profession, VillagerProfession.LIBRARIAN)) {
                return villagerData.level() <= 1 && !WorldManager.INSTANCE.isVillagerTradeLock(villager);
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    public boolean isRefreshTradeVillager(VillagerEntity villagerEntity) {
        // check on ground
        return EntityUtils.isEntityValid(villagerEntity)
                && villagerEntity.isOnGround()
                && !villagerEntity.isTouchingWater()
                && isLowLevelOrNoProfessionVillager(villagerEntity)
                && locateWorkStation(villagerEntity) != null;
    }

    private BlockPos locateWorkStation(VillagerEntity villager) {
        BlockPos pos = villager.getSteppingPos();
        for (var re : MathUtils.HORIZONTALS) {
            BlockPos pos2 = pos.offset(re);
            BlockState state = mc.world.getBlockState(pos2);
            if (workstationPredicate.get().test(state.getBlock())) {
                return pos2;
            }
        }
        return null;
    }

    public void refreshTarget() {
        if (targetVillager != null) {
            targetWorkStationBase = locateWorkStation(targetVillager);
            return;
        }
        List<VillagerEntity> allVillagersInWorkSpace = mc.world.getEntitiesByType(
                EntityType.VILLAGER, mc.player.getBoundingBox().expand(100, 100, 100), this::isRefreshTradeVillager);
        if (allVillagersInWorkSpace.isEmpty()) {
            clearTarget();
            return;
        }
        targetVillager = allVillagersInWorkSpace.stream()
                .min(Comparator.comparingDouble(s -> s.getPos().squaredDistanceTo(mc.player.getPos())))
                .orElseThrow();
        targetWorkStationBase = locateWorkStation(targetVillager);
        return;
    }

    public void onPreInputEvent(Event<Void> event) {
        if (checkNull()) return;
        if (enable.get()) {
            refreshTarget();
            if (targetVillager != null && targetWorkStationBase != null) {
                tickRefreshEnchantment();
            }
            pathingSchedular.tickPathing(mc.player);
            adjustmentSchedular.tickAdjustment(mc.player);
        }
    }

    public void onRender(Event<MatrixStack> event) {
        if (enable.get() && render.get()) {
            if (targetVillager != null && targetWorkStationBase != null) {
                float partialTicks = event.getArgs(0);
                RenderUtils.startDrawVirtual(event.context);
                try {
                    RenderCollector<Box> collector = RenderCollectors.createBoxCollector(true, false, false);
                    collector.submit(
                            RenderUtils.getLerpedBox(targetVillager, partialTicks),
                            renderColor.get().withAlpha(255));
                    collector.submit(
                            new Box(targetWorkStationBase.add(0, 1, 0)),
                            renderColor.get().withAlpha(255));
                    collector.render3D(event.context);
                } finally {
                    RenderUtils.stopDrawVirtual(event.context);
                }
                if (baritoneControl.get()) {
                    pathingSchedular.renderPathing(event);
                }
                if (adjustmentControl.get()) {
                    adjustmentSchedular.renderAdjustment(event);
                }
            }
        }
    }

    private int lastInteractTick = 0;
    private boolean noLecternNotify = false;
    private int lastMerchantScreenSyncId = -1;
    private boolean currentAccepted = false;
    private boolean hasOpened = false;

    public void tickRefreshEnchantment() {
        if (!(isRefreshTradeVillager(targetVillager))) {
            clearTarget();
            // end
            if (mc.currentScreen instanceof MerchantScreen merchant) {
                merchant.close();
            }
            return;
        }
        if (!TargetSelector.INSTANCE.isWithinAttackRange(mc.player.getPos(), targetVillager)) {
            return;
        }
        if (pathingSchedular.isPathing()) {
            return;
        }
        PlayerInteractionAccess access = PlayerInteractionAccess.of(mc.interactionManager);
        BlockPos targetWorkspace = targetWorkStationBase.add(0, 1, 0);
        BlockState currentState = mc.world.getBlockState(targetWorkspace);
        VillagerData data = targetVillager.getVillagerData();
        RegistryKey<VillagerProfession> professionRegistryKey =
                data.profession().getKey().orElse(null);
        if (currentState.isAir() || currentState.isLiquid() || currentState.isReplaceable()) {
            hasOpened = false;
            if (Objects.equals(professionRegistryKey, VillagerProfession.NONE)) {
                IndexEntry<ItemStack> findStack =
                        InventoryUtils.findPlayerItem(s -> s.isOf(Items.LECTERN), true, false);
                if (findStack != null) {
                    noLecternNotify = false;
                    Runnable callback = InvExtra.INSTANCE.swapInventoryIndexToHand(findStack.index());
                    if (callback != null) {
                        Direction direction = MathUtils.getHorizontalFacing(
                                targetWorkspace.toCenterPos().subtract(targetVillager.getPos()));
                        Interact.INSTANCE.placeBlockStrict(
                                targetWorkspace,
                                Blocks.LECTERN.getDefaultState().with(HorizontalFacingBlock.FACING, direction));
                        callback.run();
                        return;
                    }
                } else {
                    if (!noLecternNotify && log.get()) {
                        noLecternNotify = true;
                        logI18N("message.module.auto-librarian.no-lectern");
                    }
                    return;
                }
            } else {
                // wait till its profession disappear
                return;
            }
            return;
        }

        // refresh a trade
        if (Objects.equals(professionRegistryKey, VillagerProfession.LIBRARIAN)) {
            // we pretend that this is the screen
            if (mc.currentScreen instanceof MerchantScreen merchantScreen) {
                MerchantScreenHandler handler = merchantScreen.getScreenHandler();
                if (lastMerchantScreenSyncId != handler.syncId) {
                    lastMerchantScreenSyncId = handler.syncId;
                    hasOpened = true;
                    onMerchantScreenUpdate(merchantScreen);
                }
                if (!currentAccepted) {
                    if (!Objects.equals(access.getCurrentMiningPos(), targetWorkspace)) {
                        access.sendStartBreakPacket(targetWorkspace);
                    }
                    if (access.predictCurrentMiningProgressWithTool(ItemStack.EMPTY) < 0.7) {
                        return;
                    }
                    mc.player.swingHand(Hand.MAIN_HAND);
                    access.sendBreakPacket();
                } else if (!WorldManager.canVillagerResetTrade(merchantScreen.getScreenHandler())) {
                    WorldManager.INSTANCE.setVillagerTradeLock(targetVillager, true);
                }
            } else if (mc.currentScreen == null || mc.currentScreen instanceof HandledScreen<?>) {
                if (!hasOpened && lastInteractTick + 5 < Tasks.getTick()) {
                    Interact.INSTANCE.interactEntity(targetVillager);
                    lastInteractTick = Tasks.getTick();
                }
            }
        }
    }

    public void onMerchantScreenUpdate(MerchantScreen screen) {
        MerchantScreenHandler handler = screen.getScreenHandler();
        if (WorldManager.canVillagerResetTrade(handler)) {

            IndexEntry<Pair<Integer, RegistryEntry<Enchantment>>> findIndex = checkTradingIndex(handler);
            if (findIndex == null) {
                currentAccepted = false;
                return;
            }

            Integer currentLimit =
                    enchantments.get().getOrDefault(findIndex.val().getSecond());
            if (currentLimit == null) {
                currentAccepted = false;
                if (log.get()) {
                    logI18N(
                            "message.module.auto-librarian.enchantment-not-whitelisted",
                            findIndex.val().getSecond().value().description());
                }
                return;
            }
            int price = getPriceAt(handler, findIndex.index());
            if (price > currentLimit) {
                currentAccepted = false;
                if (log.get()) {
                    logI18N(
                            "message.module.auto-librarian.enchantment-price-too-high",
                            findIndex.val().getSecond().value().description(),
                            price,
                            currentLimit);
                }
                return;
            }
            int level = findIndex.val().getFirst();
            int maxLevel = findIndex.val().getSecond().value().getMaxLevel();
            if (onlyMaxLeve.get() && level < maxLevel) {
                currentAccepted = false;
                if (log.get()) {
                    logI18N(
                            "message.module.auto-librarian.enchantment-level-too-low",
                            findIndex.val().getSecond().value().description(),
                            level,
                            maxLevel);
                }
                return;
            }
            if (log.get()) {
                logI18N(
                        "message.module.auto-librarian.enchantment-success",
                        findIndex.val().getSecond().value().description(),
                        price);
            }
            currentAccepted = true;
            if (autoLockTrade.get()) {
                if (InventoryUtils.findPlayerItem(s -> s.isOf(Items.BOOK), true, false) != null) {
                    if (InventoryUtils.computePlayerInventory(Items.EMERALD) >= price) {
                        var access = MerchantScreenAccess.of(screen);
                        access.setSelectedIndex(access.getSelectedIndex());
                        mc.interactionManager.clickSlot(handler.syncId, 2, 1, SlotActionType.PICKUP, mc.player);
                    } else {
                        if (log.get()) {
                            logI18N("message.module.auto-librarian.auto-lock.no-item", Items.EMERALD.getName());
                        }
                    }
                } else {
                    if (log.get()) {
                        logI18N("message.module.auto-librarian.auto-lock.no-item", Items.BOOK.getName());
                    }
                }
            }
        } else {
            WorldManager.INSTANCE.setVillagerTradeLock(targetVillager, true);
            currentAccepted = true;
        }
    }

    private int getPriceAt(MerchantScreenHandler handler, int idx) {
        var re = handler.getRecipes().get(idx);
        return Math.max(
                re.getFirstBuyItem().count(),
                re.getSecondBuyItem().map(TradedItem::count).orElse(0));
    }

    public IndexEntry<Pair<Integer, RegistryEntry<Enchantment>>> checkTradingIndex(MerchantScreenHandler handler) {
        var offers = handler.getRecipes();
        int idx = 0;
        for (var re : offers) {
            ItemStack stack1 = re.getSellItem();
            // if(stack1)
            if (stack1.isOf(Items.ENCHANTED_BOOK) && stack1.contains(DataComponentTypes.STORED_ENCHANTMENTS)) {
                // check price and enchantments
                var firstEnch = stack1.get(DataComponentTypes.STORED_ENCHANTMENTS).getEnchantmentEntries().stream()
                        .findFirst()
                        .orElse(null);
                if (firstEnch != null) {
                    return new IndexEntry<>(idx, new Pair<>(firstEnch.getIntValue(), firstEnch.getKey()));
                }
            }
            idx += 1;
        }
        return null;
    }

    boolean pickupLecterns = false;

    public IPathGoal processGoal() {
        IndexEntry<ItemStack> findLectern = InventoryUtils.findItem(mc.player.getInventory(), Items.LECTERN);
        if (findLectern == null) {
            pickupLecterns = true;
        }
        if (pickupLecterns) {
            BlockPos doNotIntersect = targetVillager.getBlockPos();
            Box doNotIntersectBox = new Box(doNotIntersect).expand(0, 1, 0);
            List<ItemEntity> nearbyLecterns = mc.world.getEntitiesByType(
                    EntityType.ITEM,
                    mc.player.getBoundingBox().expand(6, 2, 6),
                    (item) -> !doNotIntersectBox.intersects(item.getBoundingBox())
                            && (item).getStack().isOf(Items.LECTERN));
            ItemEntity nearest = nearbyLecterns.stream()
                    .min(Comparator.comparingDouble(s -> s.getPos().squaredDistanceTo(mc.player.getPos())))
                    .orElse(null);
            if (nearest == null) {
                pickupLecterns = false;
                return null;
            } else {
                return new GoalNearBlockPos(nearest.getBlockPos());
            }
        } else {
            Direction lastDirection = MathUtils.getHorizontalFacing(
                    targetWorkStationBase.toCenterPos().subtract(targetVillager.getPos()));
            Direction clockWise = lastDirection.rotateClockwise(Direction.Axis.Y);
            BlockPos testPos = targetWorkStationBase.add(0, 1, 0).offset(clockWise);
            BlockState testState = mc.world.getBlockState(testPos);
            if (!testState
                    .getCollisionShape(mc.world, testPos, ShapeContext.of(mc.player))
                    .isEmpty()) {
                testPos = targetWorkStationBase.add(0, 1, 0).offset(clockWise.getOpposite());
            }
            return new GoalBlockPos(testPos);
        }
    }

    {
        pathingSchedular.active(this::isEnablePathing).processGoal(this::processGoal);
    }

    public boolean isEnablePathing() {
        return baritoneControl.get() && targetVillager != null && targetWorkStationBase != null;
    }

    {
        adjustmentSchedular.adjustRange(1.5);
        adjustmentSchedular.center(this::adjustmentGoal);
    }

    public Vec3d adjustmentGoal() {
        if (adjustmentControl.get() && targetVillager != null && targetWorkStationBase != null) {
            BlockPos targetWorkSpace = targetWorkStationBase.add(0, 1, 0);
            BlockPos playerPos = mc.player.getBlockPos();
            int manDistance = MathUtils.getManhattanDistance(targetWorkSpace, playerPos);
            if (manDistance <= 1) {
                Direction lastDirection = MathUtils.getHorizontalFacing(
                        targetWorkStationBase.toCenterPos().subtract(targetVillager.getPos()));
                Direction leftPos = lastDirection.rotateYClockwise();
                Direction rightPos = lastDirection.rotateYCounterclockwise();
                BlockPos leftBp = targetWorkSpace.offset(leftPos);
                BlockPos rightBp = targetWorkSpace.offset(rightPos);
                if (Objects.equals(playerPos, leftBp)) {
                    Vec3d corner = playerPos
                            .toBottomCenterPos()
                            .offset(lastDirection.getOpposite(), 0.2)
                            .offset(rightPos, 0.15);
                    if (MathUtils.isInBox(corner, mc.player.getPos(), 0.05)) {
                        return null;
                    }
                    return corner.offset(lastDirection.getOpposite(), 0.5);

                } else if (Objects.equals(playerPos, rightBp)) {
                    Vec3d corner = playerPos
                            .toBottomCenterPos()
                            .offset(lastDirection.getOpposite(), 0.23)
                            .offset(leftPos, 0.15);
                    if (MathUtils.isInBox(corner, mc.player.getPos(), 0.05)) {
                        return null;
                    }
                    return corner.offset(lastDirection.getOpposite(), 0.5);
                } else if (Objects.equals(playerPos, targetWorkSpace)) {
                    if (MathUtils.isInBox(leftBp.toCenterPos(), mc.player.getPos(), 1)) {
                        return leftBp.toBottomCenterPos();
                    } else {
                        return rightBp.toBottomCenterPos();
                    }
                }
            } else if (manDistance == 2) {
                return targetVillager.getPos();
            }
        }
        return null;
    }
}
