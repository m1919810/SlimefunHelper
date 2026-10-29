package me.matl114.hacks.modules.survival;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.RenderListener;
import me.matl114.events.impl.Render3D;
import me.matl114.hacks.MineTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.modules.mine.MineBot;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.config.Regex;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.enums.LegalMiningMode;
import me.matl114.hacks.utils.move.PathingSchedular;
import me.matl114.hacks.utils.move.goal.GoalNear;
import me.matl114.hacks.utils.move.goal.IPathGoal;
import me.matl114.managers.Configs;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.MathUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;

public class AutoMine extends BaseModule {
    private static final int DEFAULT_MAX_INSTANT_MINE = 30;
    private static final int SEARCH_VERTICAL_MARGIN = 2;

    private final PathingSchedular pathingSchedular = new PathingSchedular();
    private BatchPhase batchPhase = BatchPhase.PREPARE;
    private BlockPos anchorStandPos;
    private List<BlockPos> route = Collections.emptyList();
    private int routeIndex;
    private int collectRouteIndex;
    private BlockPos lastMinePos;
    private final Set<UUID> lockedCollectDrops = new HashSet<>();

    public AutoMine() {
        super("AutoMine");
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.SURVIVAL_CONFIG, "survival-mine-utils.auto-mine");
    public final FlagRef enable = flagBuilder(root.addEnable()).build();
    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();

    public final EnumRef<Mode> mode =
            builder(root.add("mode"), Mode.class).defaultValue(Mode.BATCH).build();

    public final FlagRef enableBaritone =
            flagBuilder(root.add("enable-baritone")).defaultValue(true).build();

    public final NBTRef<EntrySet<Block>> blockWhitelist = builder(root.add("whitelist"), EntrySet.<Block>parameter())
            .defaultValue(new EntrySet<>(new Regex("^(sand|red_sand|gravel|clay|dirt|grass_block)$"), Registries.BLOCK))
            .build();

    public final IntRef startDownOffset = intBuilder(root.add("start-down-offset"))
            .defaultValue(3)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final IntRef heightSearchLimit = intBuilder(root.add("height-search-limit"))
            .defaultValue(24)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final IntRef horizontalRange = intBuilder(root.add("horizontal-range"))
            .defaultValue(8)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final IntRef mineHeight = intBuilder(root.add("mine-height"))
            .defaultValue(6)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final IntRef routeSpacing = intBuilder(root.add("route-spacing"))
            .defaultValue(3)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final DoubleRef collectEnterDistance = doubleBuilder(root.add("collect-enter-distance"))
            .defaultValue(8.0)
            .validator(Configs.doubleRange(0.0D, Double.MAX_VALUE))
            .build();

    public final DoubleRef collectSearchRadius = doubleBuilder(root.add("collect-search-radius"))
            .defaultValue(8.0)
            .validator(Configs.doubleRange(0.0D, Double.MAX_VALUE))
            .build();

    public final FlagRef mineDuringCollect =
            flagBuilder(root.add("mine-during-collect")).defaultValue(true).build();

    public final FlagRef refreshCollectDrops =
            flagBuilder(root.add("refresh-collect-drops")).defaultValue(true).build();

    public final IntRef requiredEmptySlots = intBuilder(root.add("required-empty-slots"))
            .defaultValue(1)
            .validator(Configs.INT_NONNEGATIVE)
            .build();

    public final EnumRef<LegalMiningMode> legalMiningMode = builder(
                    root.add("legal-mining-mode"), LegalMiningMode.class)
            .defaultValue(LegalMiningMode.NONE)
            .build();

    public final FlagRef swingHand = flagBuilder(root.add("swing-hand")).build();

    public final FlagRef rotate = flagBuilder(root.add("rotate")).build();

    public final FlagRef considerCooldown = builder(root.add("consider-cooldown"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef autoSwap =
            flagBuilder(root.add("auto-swap")).defaultValue(true).build();

    public final FlagRef toolProtect =
            flagBuilder(root.add("durability-protect")).defaultValue(true).build();

    public final FlagRef doubleBreak = flagBuilder(root.add("use-double-break")).build();

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
        batchPhase = BatchPhase.PREPARE;
        anchorStandPos = null;
        route = Collections.emptyList();
        routeIndex = 0;
        collectRouteIndex = 0;
        lastMinePos = null;
        lockedCollectDrops.clear();
        pathingSchedular.disable();
    }

    public void onPreInputEvent() {
        if (checkNull() || !enable.get()) {
            return;
        }
        switch (mode.get()) {
            case BATCH -> tickBatch();
            case ACCURATE -> clearState();
        }
    }

    public void onRender(Event<Render3D> event) {
        if (enable.get() && mode.get() == Mode.BATCH) {
            pathingSchedular.renderPathing(event);
        }
    }

    private void tickBatch() {
        ensureBatchPlan();
        updateBatchPhase();
        pathingSchedular.tickPathing(mc.player);
        if (shouldTickMine()) {
            onMineCommon(this::findNextMinePosLayeredDown);
            updateBatchPhase();
        }
    }

    private void ensureBatchPlan() {
        if (anchorStandPos != null) {
            return;
        }
        BlockPos currentStandPos = mc.player.getSteppingPos().add(0, 1, 0);
        anchorStandPos =
                new BlockPos(currentStandPos.getX(), resolveTargetStandY(currentStandPos), currentStandPos.getZ());
        route = buildBatchRoute(anchorStandPos);
        routeIndex = 0;
        collectRouteIndex = 0;
        batchPhase = route.isEmpty() ? BatchPhase.FINISHED : BatchPhase.PREPARE;
    }

    private int resolveTargetStandY(BlockPos currentStandPos) {
        int fixedY = Math.max(mc.world.getBottomY() + 1, currentStandPos.getY() - startDownOffset.get());
        int minY = Math.max(mc.world.getBottomY(), fixedY - heightSearchLimit.get());
        for (int y = fixedY - 1; y >= minY; --y) {
            BlockPos sample = new BlockPos(currentStandPos.getX(), y, currentStandPos.getZ());
            if (!isMineable(mc.world.getBlockState(sample))) {
                return y + 1;
            }
        }
        return fixedY;
    }

    private List<BlockPos> buildBatchRoute(BlockPos center) {
        int range = horizontalRange.get();
        int step = Math.max(1, routeSpacing.get());
        List<Integer> xs = new ArrayList<>();
        List<Integer> zs = new ArrayList<>();
        for (int x = center.getX() - range; x <= center.getX() + range; x += step) {
            xs.add(x);
        }
        if (xs.isEmpty() || xs.get(xs.size() - 1) != center.getX() + range) {
            xs.add(center.getX() + range);
        }
        for (int z = center.getZ() - range; z <= center.getZ() + range; z += step) {
            zs.add(z);
        }
        if (zs.isEmpty() || zs.get(zs.size() - 1) != center.getZ() + range) {
            zs.add(center.getZ() + range);
        }
        List<BlockPos> result = new ArrayList<>();
        boolean reverse = false;
        for (int z : zs) {
            if (!reverse) {
                for (int x : xs) {
                    BlockPos pos = new BlockPos(x, center.getY(), z);
                    if (hasMineableAroundWaypoint(pos)) {
                        result.add(pos);
                    }
                }
            } else {
                for (int i = xs.size() - 1; i >= 0; --i) {
                    BlockPos pos = new BlockPos(xs.get(i), center.getY(), z);
                    if (hasMineableAroundWaypoint(pos)) {
                        result.add(pos);
                    }
                }
            }
            reverse = !reverse;
        }
        if (result.isEmpty()) {
            result.add(center);
        }
        return result;
    }

    private void updateBatchPhase() {
        if (anchorStandPos == null || shouldDischarge()) {
            return;
        }
        switch (batchPhase) {
            case PREPARE -> {
                if (isNear(anchorStandPos, 1.75)) {
                    batchPhase = BatchPhase.MINE;
                }
            }
            case MINE -> {
                advanceRouteIndex();
                if (routeIndex >= route.size()) {
                    if (hasCollectTarget(true)) {
                        enterCollectMode();
                    } else {
                        batchPhase = BatchPhase.FINISHED;
                    }
                    return;
                }
                if (shouldEnterCollectMode()) {
                    enterCollectMode();
                }
            }
            case COLLECT -> {
                if (findCollectTarget(refreshCollectDrops.get()) != null) {
                    return;
                }
                if (collectRouteIndex >= 0) {
                    return;
                }
                advanceRouteIndex();
                if (routeIndex < route.size()) {
                    batchPhase = BatchPhase.MINE;
                } else if (!hasCollectTarget(true)) {
                    batchPhase = BatchPhase.FINISHED;
                }
            }
            case FINISHED -> {
                if (hasCollectTarget(true)) {
                    enterCollectMode();
                }
            }
        }
    }

    private void advanceRouteIndex() {
        while (routeIndex < route.size()) {
            if (hasMineableAroundWaypoint(route.get(routeIndex))) {
                return;
            }
            routeIndex++;
        }
    }

    private boolean shouldEnterCollectMode() {
        if (!hasCollectTarget(true)) {
            return false;
        }
        if (findNextMinePosLayeredDown() != null) {
            return false;
        }
        if (routeIndex >= route.size()) {
            return true;
        }
        return mc.player.getPos().squaredDistanceTo(route.get(routeIndex).toCenterPos())
                > MathUtils.s2(collectEnterDistance.get());
    }

    private void enterCollectMode() {
        batchPhase = BatchPhase.COLLECT;
        collectRouteIndex = Math.min(route.size() - 1, Math.max(routeIndex - 1, 0));
        lockedCollectDrops.clear();
        if (!refreshCollectDrops.get()) {
            Box box = mc.player
                    .getBoundingBox()
                    .expand(collectSearchRadius.get(), SEARCH_VERTICAL_MARGIN, collectSearchRadius.get());
            for (ItemEntity item : mc.world.getEntitiesByType(EntityType.ITEM, box, this::isCollectibleDrop)) {
                lockedCollectDrops.add(item.getUuid());
            }
        }
    }

    private boolean shouldTickMine() {
        if (mode.get() != Mode.BATCH || anchorStandPos == null || shouldDischarge()) {
            return false;
        }
        return switch (batchPhase) {
            case MINE -> true;
            case COLLECT -> mineDuringCollect.get();
            default -> false;
        };
    }

    private IPathGoal processGoal() {
        if (mode.get() != Mode.BATCH || anchorStandPos == null) {
            return null;
        }
        return switch (batchPhase) {
            case PREPARE -> PathingSchedular.pathToOrNearStopGoal(anchorStandPos, 1.25);
            case MINE -> processMineGoal();
            case COLLECT -> processCollectGoal();
            case FINISHED -> null;
        };
    }

    private IPathGoal processMineGoal() {
        advanceRouteIndex();
        if (routeIndex >= route.size()) {
            return null;
        }
        return PathingSchedular.pathToOrNearStopGoal(route.get(routeIndex), 1.25);
    }

    private IPathGoal processCollectGoal() {
        ItemEntity target = findCollectTarget(refreshCollectDrops.get());
        if (target != null) {
            return new GoalNear(target.getPos(), 1.25);
        }
        while (collectRouteIndex >= 0) {
            BlockPos targetPos = route.get(collectRouteIndex);
            if (isNear(targetPos, 1.5)) {
                collectRouteIndex--;
                continue;
            }
            return PathingSchedular.pathToOrNearStopGoal(targetPos, 1.25);
        }
        return null;
    }

    private boolean shouldDischarge() {
        return countEmptyInventorySlots() < requiredEmptySlots.get();
    }

    private int countEmptyInventorySlots() {
        int count = 0;
        for (int i = 0; i < 36; ++i) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private boolean shouldDischargeStack(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        return blockWhitelist.get().test(blockItem.getBlock());
    }

    private boolean isCollectibleDrop(ItemEntity itemEntity) {
        return itemEntity != null
                && !itemEntity.isRemoved()
                && !itemEntity.getStack().isEmpty();
    }

    private boolean hasCollectTarget(boolean dynamic) {
        return findCollectTarget(dynamic) != null;
    }

    private ItemEntity findCollectTarget(boolean dynamic) {
        Box box = mc.player
                .getBoundingBox()
                .expand(collectSearchRadius.get(), SEARCH_VERTICAL_MARGIN, collectSearchRadius.get());
        return mc
                .world
                .getEntitiesByType(EntityType.ITEM, box, item -> {
                    if (!isCollectibleDrop(item)) {
                        return false;
                    }
                    return dynamic || lockedCollectDrops.contains(item.getUuid());
                })
                .stream()
                .min(Comparator.comparingDouble(item -> item.getPos().squaredDistanceTo(mc.player.getPos())))
                .orElse(null);
    }

    private boolean hasMineableAroundWaypoint(BlockPos standPos) {
        return findMinePosAround(standPos, false) != null;
    }

    private BlockPos findNextMinePosLayeredDown() {
        return findMinePosAround(getMiningBasePos(), true);
    }

    private BlockPos getMiningBasePos() {
        return new BlockPos(mc.player.getBlockX(), anchorStandPos.getY(), mc.player.getBlockZ());
    }

    private BlockPos findMinePosAround(BlockPos standPos, boolean requireReachable) {
        for (int y = mineHeight.get() - 1; y >= 0; --y) {
            for (var plate : InteractExtra.INSTANCE.getPlatesAround()) {
                BlockPos targetPos = standPos.add(plate.x, y, plate.y);
                if (!isInsidePlan(targetPos)) {
                    continue;
                }
                if (requireReachable) {
                    if (!checkDistanceAndCondition(targetPos)) {
                        continue;
                    }
                } else if (!isMineable(mc.world.getBlockState(targetPos))) {
                    continue;
                }
                return targetPos;
            }
        }
        return null;
    }

    private boolean isInsidePlan(BlockPos pos) {
        if (anchorStandPos == null) {
            return false;
        }
        return Math.abs(pos.getX() - anchorStandPos.getX()) <= horizontalRange.get()
                && Math.abs(pos.getZ() - anchorStandPos.getZ()) <= horizontalRange.get()
                && pos.getY() >= anchorStandPos.getY()
                && pos.getY() < anchorStandPos.getY() + mineHeight.get();
    }

    private boolean isMineable(BlockState state) {
        if (state == null || state.isAir() || state.isLiquid()) {
            return false;
        }
        Block block = state.getBlock();
        return block.getHardness() >= 0.0F && blockWhitelist.get().test(block);
    }

    private boolean checkDistanceAndCondition(BlockPos newPos) {
        if (newPos == null) {
            return false;
        }
        var access = PlayerInteractionAccess.of(mc.interactionManager);
        if (Objects.equals(access.getCurrentFailBreakPos(), newPos)) {
            return false;
        }
        if (!isInsidePlan(newPos)) {
            return false;
        }
        if (!isMineable(mc.world.getBlockState(newPos))) {
            return false;
        }
        return !MineTasks.distanceOutOfReach(newPos, mc.player.getEyePos());
    }

    private int onMineCommon(Supplier<BlockPos> posFinder) {
        int tryMine = 0;
        Vec2f originPy = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
        MineBot.MiningSettings settings = new MineBot.MiningSettings(
                considerCooldown.get(),
                toolProtect.get(),
                autoSwap.get(),
                doubleBreak.get(),
                swingHand.get(),
                rotate.get(),
                legalMiningMode.get(),
                GhostHandMode.INV_SWAP);
        while (true) {
            if (tryMine >= DEFAULT_MAX_INSTANT_MINE) {
                break;
            }
            if (!checkDistanceAndCondition(lastMinePos)) {
                lastMinePos = posFinder.get();
            }
            if (lastMinePos == null) {
                break;
            }
            tryMine += 1;
            MineBot.TickMineResult result = MineBot.INSTANCE.tickMine(lastMinePos, settings);
            if (result == MineBot.TickMineResult.TOOL_PROTECTED) {
                enable.set(false);
            }
            if (result != MineBot.TickMineResult.CONTINUE) {
                break;
            }
            lastMinePos = posFinder.get();
            if (lastMinePos == null) {
                break;
            }
        }
        if (mc.player.getPitch() != originPy.x || mc.player.getYaw() != originPy.y) {
            mc.player.setPitch(originPy.x);
            mc.player.setYaw(originPy.y);
            ClientPlayerAccess.of(mc.player).resyncRot();
        }
        return tryMine;
    }

    private boolean isNear(BlockPos pos, double distance) {
        return pos != null && mc.player.getPos().squaredDistanceTo(pos.toCenterPos()) <= MathUtils.s2(distance);
    }

    {
        pathingSchedular
                .mine(true)
                .active(this::isPathingEnabled)
                .processGoal(this::processGoal)
                .discharge(this::shouldDischarge)
                .dischargePlayerInventory(this::shouldDischargeStack);
    }

    private boolean isPathingEnabled() {
        return enable.get() && mode.get() == Mode.BATCH && enableBaritone.get() && batchPhase != BatchPhase.FINISHED;
    }

    public enum Mode implements ConfigEnum {
        BATCH,
        ACCURATE;

        @Override
        public String getConfigEnumType() {
            return "auto_mine_pathing_mode";
        }
    }

    private enum BatchPhase {
        PREPARE,
        MINE,
        COLLECT,
        FINISHED
    }
}
