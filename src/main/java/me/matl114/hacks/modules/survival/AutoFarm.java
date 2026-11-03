package me.matl114.hacks.modules.survival;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.Render3D;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.interact.Interact;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.mine.QueueMine;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.move.AdjustmentSchedular;
import me.matl114.hacks.utils.move.PathingSchedular;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.FarmingUtils;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.PitcherCropBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class AutoFarm extends BaseModule {
    private static final int SEARCH_VERTICAL_RADIUS = 4;
    private static final double ACTION_RANGE = 4.5D;
    private static final double DROP_SEARCH_RANGE = 1.5D;

    public AutoFarm() {
        super("AutoFarm");
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.SURVIVAL_CONFIG, "survival-interact-utils.auto-farm");

    public final FlagRef enable = flagBuilder(root.addEnable()).build();

    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final IntRef delay = intBuilder(root.add("delay"))
            .defaultValue(6)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final IntRef mul = intBuilder(root.add("multiply"))
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final FlagRef useBoneMeal =
            flagBuilder(root.add("use-bone-meal")).defaultValue(false).build();

    public final IntRef plantingRadius = intBuilder(root.add("planting-radius"))
            .defaultValue(8)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final FlagRef baritoneControl =
            flagBuilder(root.add("baritone-control")).defaultValue(true).build();

    public final FlagRef adjustmentControl =
            flagBuilder(root.add("adjustment-control")).defaultValue(true).build();

    // TODO: add auto-inventory and auto-replenishment settings.

    private final PathingSchedular pathingSchedular = new PathingSchedular();
    private final AdjustmentSchedular adjustmentSchedular = new AdjustmentSchedular();
    private final Map<BlockPos, FarmingUtils.PlantType> replantRecords = new HashMap<>();
    private final Set<BlockPos> queuedHarvests = new HashSet<>();

    private FarmTarget target;
    private BlockPos lastReplantAttemptPos;
    private boolean lastReplantAttemptFailed;
    private int lastActionTick = Integer.MIN_VALUE;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
        registerListener(RenderListener.getRender3DEvent(), this::onRender);
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        clearState();
    }

    private void clearState() {
        target = null;
        replantRecords.clear();
        queuedHarvests.clear();
        lastReplantAttemptPos = null;
        lastReplantAttemptFailed = false;
        lastActionTick = Integer.MIN_VALUE;
        pathingSchedular.disable();
    }

    private void onPreInputEvent(Event<Void> event) {
        if (checkNull() || !enable.get()) {
            return;
        }

        refreshRecords();
        refreshQueuedHarvests();
        target = findTarget();

        if (target != null
                && !pathingSchedular.isPathing()
                && isWithinActionRange(target.pos())
                && lastActionTick + delay.get() <= Tasks.getTick()
                && performTarget(target)) {
            lastActionTick = Tasks.getTick();
        }

        pathingSchedular.tickPathing(mc.player);
        adjustmentSchedular.tickAdjustment(mc.player);
    }

    private void onRender(Event<Render3D> event) {
        if (!enable.get()) {
            return;
        }
        if (baritoneControl.get()) {
            pathingSchedular.renderPathing(event);
        }
        if (adjustmentControl.get()) {
            adjustmentSchedular.renderAdjustment(event);
        }
    }

    private FarmTarget findTarget() {
        FarmTarget replant = findReplantTarget();
        if (replant != null) {
            return replant;
        }

        FarmTarget harvest = findHarvestTarget();
        if (harvest != null) {
            return harvest;
        }

        if (useBoneMeal.get() && hasBoneMeal()) {
            return findBoneMealTarget();
        }
        return null;
    }

    private FarmTarget findReplantTarget() {
        return replantRecords.entrySet().stream()
                .map(entry -> {
                    BlockPos pos = entry.getKey();
                    FarmingUtils.PlantType type = entry.getValue();
                    BlockHitResult hit = FarmingUtils.tryPlantAt(mc.world, pos, type);
                    if (hit == null || findSeed(type) == null) {
                        return null;
                    }
                    return new FarmTarget(pos, type, Action.REPLANT);
                })
                .filter(candidate -> candidate != null)
                .min(Comparator.comparingDouble(
                        candidate -> candidate.pos().toCenterPos().squaredDistanceTo(mc.player.getPos())))
                .orElse(null);
    }

    private FarmTarget findHarvestTarget() {
        return findCropTarget((type, pos, state) -> type.canHarvest(mc.world, pos), Action.HARVEST);
    }

    private FarmTarget findBoneMealTarget() {
        return findCropTarget(
                (type, pos, state) -> state.getBlock() instanceof Fertilizable fertilizable
                        && fertilizable.isFertilizable(mc.world, pos, state),
                Action.BONE_MEAL);
    }

    private FarmTarget findCropTarget(CropPredicate predicate, Action action) {
        FarmTarget best = null;
        BlockPos playerPos = mc.player.getBlockPos();
        int radius = plantingRadius.get();
        for (int x = playerPos.getX() - radius; x <= playerPos.getX() + radius; x++) {
            for (int z = playerPos.getZ() - radius; z <= playerPos.getZ() + radius; z++) {
                for (int y = playerPos.getY() - SEARCH_VERTICAL_RADIUS;
                        y <= playerPos.getY() + SEARCH_VERTICAL_RADIUS;
                        y++) {
                    BlockPos scannedPos = new BlockPos(x, y, z);
                    FarmingUtils.PlantType scannedType = FarmingUtils.getPlantType(
                            mc.world.getBlockState(scannedPos).getBlock());
                    if (scannedType == null) {
                        continue;
                    }

                    BlockPos plantPos = normalizePlantPos(scannedPos);
                    BlockState state = mc.world.getBlockState(plantPos);
                    FarmingUtils.PlantType type = FarmingUtils.getPlantType(state.getBlock());
                    if (type == null || !predicate.test(type, plantPos, state)) {
                        continue;
                    }
                    if (queuedHarvests.contains(plantPos) && type.canHarvest(mc.world, plantPos)) {
                        continue;
                    }
                    FarmTarget candidate = new FarmTarget(plantPos, type, action);
                    if (best == null
                            || candidate.pos().toCenterPos().squaredDistanceTo(mc.player.getPos())
                                    < best.pos().toCenterPos().squaredDistanceTo(mc.player.getPos())) {
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private void refreshRecords() {
        Iterator<Map.Entry<BlockPos, FarmingUtils.PlantType>> iterator =
                replantRecords.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, FarmingUtils.PlantType> entry = iterator.next();
            if (isReplanted(entry.getKey(), entry.getValue())) {
                if (entry.getKey().equals(lastReplantAttemptPos)) {
                    lastReplantAttemptPos = null;
                    lastReplantAttemptFailed = false;
                }
                iterator.remove();
            }
        }
        if (lastReplantAttemptPos != null && !replantRecords.containsKey(lastReplantAttemptPos)) {
            lastReplantAttemptPos = null;
            lastReplantAttemptFailed = false;
        }
    }

    private void refreshQueuedHarvests() {
        queuedHarvests.removeIf(pos -> {
            FarmingUtils.PlantType type =
                    FarmingUtils.getPlantType(mc.world.getBlockState(pos).getBlock());
            return type == null || !type.canHarvest(mc.world, pos);
        });
    }

    private boolean performTarget(FarmTarget target) {
        return switch (target.action()) {
            case REPLANT -> replant(target);
            case HARVEST -> harvest(target);
            case BONE_MEAL -> boneMeal(target);
        };
    }

    private boolean harvest(FarmTarget target) {
        if (QueueMine.INSTANCE == null) {
            return false;
        }
        boolean submitted = QueueMine.INSTANCE.sumitMine(target.pos());
        if (submitted) {
            queuedHarvests.add(target.pos());
            if (target.type().needReplant()) {
                replantRecords.putIfAbsent(target.pos(), target.type());
            }
        }
        return submitted;
    }

    private boolean replant(FarmTarget target) {
        if (Interact.INSTANCE == null) {
            return false;
        }
        IndexEntry<ItemStack> seed = findSeed(target.type());
        if (seed == null) {
            return false;
        }
        Runnable restore = InvExtra.INSTANCE.swapItemToHand(seed.index(), false, GhostHandMode.INV_SWAP);
        if (restore == null) {
            return false;
        }

        boolean attempted = false;
        try {
            for (int i = 0; i < mul.get(); i++) {
                BlockHitResult hit = FarmingUtils.tryPlantAt(mc.world, target.pos(), target.type());
                if (hit == null) {
                    break;
                }
                attempted = true;
                if (!Interact.INSTANCE.interactBlock(hit)) {
                    break;
                }
            }
        } finally {
            restore.run();
        }

        if (attempted) {
            lastReplantAttemptPos = target.pos();
            lastReplantAttemptFailed = !isReplanted(target.pos(), target.type());
        }
        return attempted;
    }

    private boolean boneMeal(FarmTarget target) {
        if (Interact.INSTANCE == null) {
            return false;
        }
        IndexEntry<ItemStack> boneMeal =
                InventoryUtils.findPlayerItem(stack -> stack.isOf(Items.BONE_MEAL), true, false);
        if (boneMeal == null) {
            return false;
        }
        Runnable restore = InvExtra.INSTANCE.swapItemToHand(boneMeal.index(), false, GhostHandMode.INV_SWAP);
        if (restore == null) {
            return false;
        }

        boolean attempted = false;
        try {
            for (int i = 0; i < mul.get(); i++) {
                BlockState state = mc.world.getBlockState(target.pos());
                if (!(state.getBlock() instanceof Fertilizable fertilizable)
                        || !fertilizable.isFertilizable(mc.world, target.pos(), state)) {
                    break;
                }
                attempted = true;
                if (!Interact.INSTANCE.interactBlock(target.pos())) {
                    break;
                }
            }
        } finally {
            restore.run();
        }
        return attempted;
    }

    private IndexEntry<ItemStack> findSeed(FarmingUtils.PlantType type) {
        return InventoryUtils.findPlayerItem(stack -> stack.isOf(type.getSeedItem()), true, false);
    }

    private boolean hasBoneMeal() {
        return InventoryUtils.findPlayerItem(stack -> stack.isOf(Items.BONE_MEAL), true, false) != null;
    }

    private boolean isReplanted(BlockPos pos, FarmingUtils.PlantType type) {
        BlockState state = mc.world.getBlockState(pos);
        return type.getRelatedBlocks().contains(state.getBlock()) && !type.canHarvest(mc.world, pos);
    }

    private BlockPos normalizePlantPos(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (state.isOf(net.minecraft.block.Blocks.PITCHER_CROP)
                && state.get(PitcherCropBlock.HALF) == DoubleBlockHalf.UPPER) {
            return pos.down();
        }
        return pos;
    }

    private boolean isWithinActionRange(BlockPos pos) {
        return InteractExtra.INSTANCE != null
                && InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), pos, ACTION_RANGE);
    }

    private Vec3d adjustmentGoal() {
        if (!adjustmentControl.get()
                || !lastReplantAttemptFailed
                || lastReplantAttemptPos == null
                || !replantRecords.containsKey(lastReplantAttemptPos)) {
            return null;
        }
        Box searchBox = new Box(lastReplantAttemptPos).expand(DROP_SEARCH_RANGE, 1.0D, DROP_SEARCH_RANGE);
        ItemEntity item = mc
                .world
                .getEntitiesByType(
                        EntityType.ITEM,
                        searchBox,
                        entity -> !entity.isRemoved() && !entity.getStack().isEmpty())
                .stream()
                .min(Comparator.comparingDouble(
                        entity -> entity.squaredDistanceTo(lastReplantAttemptPos.toCenterPos())))
                .orElse(null);
        return item == null ? null : item.getPos();
    }

    {
        pathingSchedular
                .active(() -> enable.get() && baritoneControl.get() && target != null)
                .processGoal(() -> target == null ? null : PathingSchedular.pathToOrNearStopGoal(target.pos(), 1.25D));
        adjustmentSchedular.adjustRange(1.5D).center(this::adjustmentGoal);
    }

    private record FarmTarget(BlockPos pos, FarmingUtils.PlantType type, Action action) {}

    private enum Action {
        REPLANT,
        HARVEST,
        BONE_MEAL
    }

    @FunctionalInterface
    private interface CropPredicate {
        boolean test(FarmingUtils.PlantType type, BlockPos pos, BlockState state);
    }
}
