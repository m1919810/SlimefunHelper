package me.matl114.hacks.modules.combat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.interact.InteractExtra;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.config.EntrySet;
import me.matl114.hacks.utils.entity.Predictor;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.enums.LegalInteractMode;
import me.matl114.managers.Configs;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.config.NBTRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.InteractUtils;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AutoTrap extends BaseModule {
    public AutoTrap() {
        super("AutoTrap");
        bindFlag(enable);
    }

    public final ModulePath root = makePath(Configs.COMBAT_CONFIG, "combat-utils.auto-trap");
    public final FlagRef enable = flagBuilder(root.addEnable()).build();
    public final KeyBindRef hotkey =
            moduleEntry(root.addHotkey(), new MultiKeyBind(), root.addEnable()).build();
    public final DoubleRef interactRange = doubleBuilder(root.add("interact-range"))
            .defaultValue(4.5D)
            .validator(Configs.doubleRange(0.0D, 100.0D))
            .build();
    public final DoubleRef targetRange = doubleBuilder(root.add("target-range"))
            .defaultValue(10.0D)
            .validator(Configs.doubleRange(0.0D, 100.0D))
            .build();
    public final IntRef delay = intBuilder(root.add("delay"))
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();
    public final IntRef multiply = intBuilder(root.add("multiply"))
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();
    public final EnumRef<LegalInteractMode> mode = builder(root.add("mode"), LegalInteractMode.class)
            .defaultValue(LegalInteractMode.DELAY_MOVEMENT)
            .build();
    public final FlagRef airplace = flagBuilder(root.add("air-place")).build();
    public final FlagRef offhand = flagBuilder(root.add("offhand-enable")).build();
    public final FlagRef footPlate = flagBuilder(root.add("foot-plate")).build();
    public final FlagRef footIfNoChoice =
            flagBuilder(root.add("foot-if-no-choice")).build();
    public final FlagRef higher = flagBuilder(root.add("higher")).build();
    public final FlagRef lower = flagBuilder(root.add("lower")).build();
    public final FlagRef ceiling = flagBuilder(root.add("ceiling")).build();
    public final FlagRef headAroundStanding =
            flagBuilder(root.add("head-around-standing")).build();
    public final FlagRef headAroundCrawling =
            flagBuilder(root.add("head-around-crawling")).build();
    public final ModulePath autoCrawlConfig = root.add("auto-crawl");
    public final FlagRef autoCrawl = flagBuilder(autoCrawlConfig.addEnable()).build();
    public final FlagRef useWhiteList = flagBuilder(root.add("use-white-list")).build();
    public final NBTRef<EntrySet<Item>> whiteList = builder(root.add("white-list"), EntrySet.<Item>parameter())
            .defaultValue(new EntrySet<>(Registries.ITEM, List.of(Items.OBSIDIAN)))
            .build();
    public final FlagRef onlyBlastResistance = builder(root.add("only-blast-resistance"), Boolean.class)
            .defaultValue(true)
            .build();
    public final FlagRef eatingAbort = builder(root.add("using-item-abort"), Boolean.class)
            .defaultValue(false)
            .build();
    public final EnumRef<GhostHandMode> ghostHand = builder(root.add("ghost-hand-mode"), GhostHandMode.class)
            .defaultValue(GhostHandMode.INV_SWAP)
            .build();
    public final FlagRef swingHand =
            builder(root.add("swing-hand"), Boolean.class).defaultValue(true).build();

    private int delayTicks;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onInput);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPreset);
    }

    private void onInput(Event<Void> event) {
        if (checkNull() || !enable.get() || (eatingAbort.get() && mc.player.isUsingItem())) {
            return;
        }
        List<PlayerEntity> targets = getTargets();
        if (targets.isEmpty()) {
            return;
        }
        if (++delayTicks >= delay.get()) {
            if (autoCrawl.get()) {
                for (PlayerEntity target : targets) {
                    Vec3d predictedPos =
                            PositionPredict.INSTANCE.attackPredictArgument.get().predict(target);
                    if (tryAutoCrawl(predictedPos)) {
                        delayTicks = 0;
                        return;
                    }
                }
            }
            for (PlayerEntity target : targets) {
                if (placeTrap(target)) {
                    delayTicks = 0;
                    return;
                }
            }
        }
    }

    private List<PlayerEntity> getTargets() {
        if (TargetSelector.INSTANCE == null) {
            return List.of();
        }
        return TargetSelector.INSTANCE.getAttackableEntities(targetRange.get()).stream()
                .filter(PlayerEntity.class::isInstance)
                .map(PlayerEntity.class::cast)
                .filter(EntityUtils::isEntityValid)
                .filter(target -> target != mc.player)
                .sorted(Comparator.comparingDouble(mc.player::squaredDistanceTo))
                .toList();
    }

    private boolean placeTrap(PlayerEntity target) {
        Predictor predictor = PositionPredict.INSTANCE.getPredictor(target);
        Vec3d predictedPos =
                PositionPredict.INSTANCE.attackPredictArgument.get().predict(target);
        Vec3d knownMovement = predictor.getKnownDeltaMovement();
        Box predictedBox = target.getBoundingBox().offset(predictedPos.subtract(target.getPos()));
        Set<BlockPos> candidates = new LinkedHashSet<>();

        boolean crawling = target.getPose() == EntityPose.SWIMMING;
        Set<BlockPos> headCandidates = new LinkedHashSet<>();
        Set<BlockPos> headOccupied = Set.of();
        if (crawling ? headAroundCrawling.get() : headAroundStanding.get()) {
            int headY = MathHelper.floor(predictedBox.maxY - 0.01D);
            Direction[] headDirections = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
            Arrays.sort(headDirections, Comparator.comparingDouble(direction -> -(Vec3d.of(direction.getVector())
                    .dotProduct(knownMovement))));
            headOccupied = getOccupiedFootprint(predictedBox, headY);
            headCandidates.addAll(getSurroundCandidates(headOccupied, headDirections));
        }
        candidates.addAll(headCandidates);
        if (higher.get() && !hasSolidBlock(headOccupied)) {
            headCandidates.stream().map(BlockPos::up).forEach(candidates::add);
        }
        if (ceiling.get()) {
            candidates.add(BlockPos.ofFloored(predictedPos.x, Math.floor(predictedBox.maxY) + 1.0D, predictedPos.z));
        }

        Set<BlockPos> footCandidates = new LinkedHashSet<>();
        if (footPlate.get()) {
            footCandidates.add(BlockPos.ofFloored(predictedPos.x, predictedBox.minY - 1.0D, predictedPos.z));
        }
        Direction[] footDirections = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        Set<BlockPos> footOccupied = getOccupiedFootprint(predictedBox, MathHelper.floor(predictedBox.minY));
        Set<BlockPos> footAroundCandidates =
                footIfNoChoice.get() ? getSurroundCandidates(footOccupied, footDirections) : new LinkedHashSet<>();
        footCandidates.addAll(footAroundCandidates);
        candidates.addAll(footCandidates);
        if (lower.get() && !hasSolidBlock(footOccupied)) {
            footAroundCandidates.stream().map(BlockPos::down).forEach(candidates::add);
        }

        List<BlockPos> ordered = new ArrayList<>(candidates);
        IndexEntry<ItemStack> supply = supplyBlocks();
        if (supply == null) {
            return false;
        }

        boolean handIsOffhand = offhand.get() || supply.index() == 40;
        int maxPlaces = Math.min(multiply.get(), supply.val().getCount());
        int placed = 0;
        Runnable callback = null;
        for (BlockPos pos : ordered) {
            BlockState state = mc.world.getBlockState(pos);
            if (!state.isAir() && !state.isLiquid() && !state.isReplaceable()) {
                continue;
            }
            FlagEntry<BlockHitResult> hitResult = InteractionTasks.getPlaceSupportingResult(
                    pos, airplace.get(), !mode.get().isLegal());
            if (!InteractUtils.canInteractAndPlace(mc.player, hitResult)) {
                continue;
            }
            if (!InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), pos)
                    || !InteractionTasks.canHandleInteract(mode.get(), hitResult.val())) {
                continue;
            }
            if (placed == 0) {
                callback = InvExtra.INSTANCE.swapItemToHand(supply.index(), handIsOffhand, ghostHand.get());
                if (callback == null) {
                    return false;
                }
            }
            boolean completed = InteractionTasks.handlePlaceMode(
                    mode.get(), hitResult.val(), handIsOffhand ? Hand.OFF_HAND : Hand.MAIN_HAND, swingHand.get());
            placed++;
            if (!completed || placed >= maxPlaces) {
                break;
            }
        }
        if (callback != null) {
            InteractionTasks.finishGhostHand(mode.get(), callback);
        }
        return placed > 0;
    }

    private Set<BlockPos> getOccupiedFootprint(Box targetBox, int y) {
        int minX = MathHelper.floor(targetBox.minX);
        int maxX = MathHelper.ceil(targetBox.maxX) - 1;
        int minZ = MathHelper.floor(targetBox.minZ);
        int maxZ = MathHelper.ceil(targetBox.maxZ) - 1;
        Set<BlockPos> occupied = new LinkedHashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                occupied.add(new BlockPos(x, y, z));
            }
        }
        return occupied;
    }

    private Set<BlockPos> getSurroundCandidates(Set<BlockPos> occupied, Direction[] directions) {
        Set<BlockPos> result = new LinkedHashSet<>();
        for (Direction direction : directions) {
            for (BlockPos occupiedPos : occupied) {
                BlockPos candidate = occupiedPos.offset(direction);
                if (occupied.contains(candidate)) {
                    continue;
                }
                result.add(candidate);
            }
        }
        return result;
    }

    private boolean hasSolidBlock(Set<BlockPos> positions) {
        return positions.stream().anyMatch(pos -> {
            BlockState state = mc.world.getBlockState(pos);
            return !state.isAir() && !state.isLiquid();
        });
    }

    private boolean tryAutoCrawl(Vec3d predictedPos) {
        BlockPos feetPos = BlockPos.ofFloored(predictedPos);
        BlockPos supportPos = feetPos.down();
        BlockState supportState = mc.world.getBlockState(supportPos);
        if (supportState.getCollisionShape(mc.world, supportPos).isEmpty()) {
            return false;
        }

        BlockPos headPos = feetPos.up();
        BlockState headState = mc.world.getBlockState(headPos);
        if (headState.getBlock() instanceof FenceGateBlock) {
            return !headState.get(FenceGateBlock.OPEN) || interactWithCrawlBlock(headPos);
        }
        if (headState.getBlock() instanceof TrapdoorBlock) {
            if (headState.get(TrapdoorBlock.OPEN)) {
                BlockState closedState = headState.with(TrapdoorBlock.OPEN, false);
                return isLowTrapdoor(closedState, headPos) && interactWithCrawlBlock(headPos);
            }
            return isLowTrapdoor(headState, headPos);
        }
        if (!headState.isAir() && !headState.isLiquid()) {
            return false;
        }

        IndexEntry<ItemStack> supply = supplyCrawlBlock();
        if (supply == null) {
            return false;
        }
        boolean trapdoor = ((BlockItem) supply.val().getItem()).getBlock() instanceof TrapdoorBlock;
        BlockHalf trapdoorHalf = trapdoor
                ? findLowTrapdoorHalf(
                        ((BlockItem) supply.val().getItem()).getBlock().getDefaultState(), headPos)
                : null;
        if (trapdoor && trapdoorHalf == null) {
            return false;
        }
        BlockState crawlState = ((BlockItem) supply.val().getItem()).getBlock().getDefaultState();
        if (trapdoor) {
            crawlState = crawlState.with(TrapdoorBlock.HALF, trapdoorHalf).with(TrapdoorBlock.OPEN, false);
        }
        FlagEntry<BlockHitResult> hitResult = null;
        for (Direction direction :
                new Direction[] {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockState candidateState = trapdoor && direction.getAxis().isHorizontal()
                    ? crawlState.with(TrapdoorBlock.FACING, direction.getOpposite())
                    : crawlState;
            FlagEntry<BlockHitResult> candidate = InteractionTasks.createSpecificStateHitResult(
                    direction,
                    headPos,
                    candidateState,
                    airplace.get(),
                    !mode.get().isLegal());
            if (candidate == null) {
                continue;
            }
            Direction expectedSide = direction.getOpposite();
            if (candidate.val().getSide() == expectedSide && InteractUtils.canInteractAndPlace(mc.player, candidate)) {
                hitResult = candidate;
                break;
            }
        }
        if (!InteractUtils.canInteractAndPlace(mc.player, hitResult)
                || !InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), headPos)
                || !InteractionTasks.canHandleInteract(mode.get(), hitResult.val())) {
            return false;
        }

        boolean handIsOffhand = offhand.get() || supply.index() == 40;
        Runnable callback = InvExtra.INSTANCE.swapItemToHand(supply.index(), handIsOffhand, ghostHand.get());
        if (callback == null) {
            return false;
        }
        boolean completed = InteractionTasks.handlePlaceMode(
                mode.get(), hitResult.val(), handIsOffhand ? Hand.OFF_HAND : Hand.MAIN_HAND, swingHand.get());
        InteractionTasks.finishGhostHand(mode.get(), callback);
        return completed;
    }

    private boolean interactWithCrawlBlock(BlockPos pos) {
        BlockHitResult hitResult = InteractionTasks.createHitResult(pos, mc.player.getPos());
        return InteractionTasks.canHandleInteract(mode.get(), hitResult)
                && InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), pos)
                && InteractionTasks.handlePlaceMode(
                        mode.get(), hitResult, offhand.get() ? Hand.OFF_HAND : Hand.MAIN_HAND, swingHand.get());
    }

    private IndexEntry<ItemStack> supplyCrawlBlock() {
        int searchSize = ghostHand.get().getSearchSize(offhand.get());
        IndexEntry<ItemStack> trapdoor = InventoryUtils.findPlayerItem(
                stack ->
                        stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof TrapdoorBlock,
                searchSize,
                true,
                false);
        return trapdoor != null
                ? trapdoor
                : InventoryUtils.findPlayerItem(
                        stack -> stack.getItem() instanceof BlockItem blockItem
                                && blockItem.getBlock() instanceof FenceGateBlock,
                        searchSize,
                        true,
                        false);
    }

    private BlockHalf findLowTrapdoorHalf(BlockState baseState, BlockPos pos) {
        for (BlockHalf half : BlockHalf.values()) {
            BlockState state = baseState.with(TrapdoorBlock.HALF, half).with(TrapdoorBlock.OPEN, false);
            if (isLowTrapdoor(state, pos)) {
                return half;
            }
        }
        return null;
    }

    private boolean isLowTrapdoor(BlockState state, BlockPos pos) {
        return !state.get(TrapdoorBlock.OPEN)
                && state.getCollisionShape(mc.world, pos).getBoundingBox().maxY <= 0.25D;
    }

    private IndexEntry<ItemStack> supplyBlocks() {
        return InventoryUtils.findBestPlayerItem(
                stack -> {
                    if (!(stack.getItem() instanceof BlockItem blockItem)) {
                        return null;
                    }
                    if (useWhiteList.get() && !whiteList.get().test(blockItem)) {
                        return null;
                    }
                    if (onlyBlastResistance.get() && blockItem.getBlock().getBlastResistance() < 600) {
                        return null;
                    }
                    return (double) blockItem.getBlock().getBlastResistance()
                            + (blockItem == Items.OBSIDIAN ? 1E8 : 0)
                            + (blockItem.getBlock() instanceof BlockWithEntity ? -1E8 : 0);
                },
                ghostHand.get().getSearchSize(offhand.get()),
                true,
                false);
    }

    private void onPreset(Event<EventContainer<ModulePreset>> event) {
        ModulePreset preset = event.context.getValue();
        if (preset.isAirplacePreset()) airplace.set(preset.getAirplace(airplace.get()));
        if (preset.isSwingPlacePreset()) swingHand.set(preset.getSwingPlace(swingHand.get()));
        if (preset.isMultitaskPreset()) eatingAbort.set(preset.getEatingAbort(eatingAbort.get()));
        if (!preset.isOptionPreset()) {
            mode.set(LegalInteractMode.getFromPreset(preset));
            airplace.set(!preset.hasAC());
        }
    }
}
