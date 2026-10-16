package me.matl114.hacks.modules.combat;

import java.util.*;
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
import me.matl114.utils.MathUtils;
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
    public final FlagRef foot = flagBuilder(root.add("foot")).build();
    public final FlagRef ceiling = flagBuilder(root.add("ceiling")).build();
    public final FlagRef headAroundStanding =
            flagBuilder(root.add("head-around-standing")).build();
    public final FlagRef headAroundCrawling =
            flagBuilder(root.add("head-around-crawling")).build();
    public final FlagRef scaffold = flagBuilder(root.add("scaffold")).build();

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
    public final ModulePath autoCrawlConfig = root.add("auto-crawl");
    public final FlagRef autoCrawl = flagBuilder(autoCrawlConfig.addEnable()).build();
    public final KeyBindRef hotkey2 = toggleHotkey(
                    autoCrawlConfig.addHotkey(), new MultiKeyBind(), autoCrawlConfig.addEnable())
            .build();
    public final FlagRef autoCrawlZeroTickInteract =
            flagBuilder(autoCrawlConfig.add("zero-tick-interact")).build();
    private int delayTicks;

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onInput);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPreset);
    }

    private void onInput(Event<Void> event) {
        if (checkNull()) {
            return;
        }

        if (eatingAbort.get() && mc.player.isUsingItem()) {
            return;
        }
        if (++delayTicks >= delay.get()) {
            int interactions = 0;
            int maxInteractions = multiply.get();

            if (autoCrawl.get()) {
                List<PlayerEntity> targets = getTargets();
                if (targets.isEmpty()) {
                    return;
                }
                for (PlayerEntity target : targets) {
                    Vec3d predictedPos =
                            PositionPredict.INSTANCE.attackPredictArgument.get().predict(target);
                    interactions += tryAutoCrawl(predictedPos);
                    if (interactions >= maxInteractions) {
                        delayTicks = 0;
                        return;
                    }
                }
            }
            if (enable.get()) {
                List<PlayerEntity> targets = getTargets();
                if (targets.isEmpty()) {
                    return;
                }
                for (PlayerEntity target : targets) {
                    interactions += placeTrap(target, maxInteractions - interactions);
                    if (interactions >= maxInteractions) {
                        delayTicks = 0;
                        return;
                    }
                }
            }

            if (interactions > 0) {
                delayTicks = 0;
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

    private int placeTrap(PlayerEntity target, int remainingInteractions) {
        if (remainingInteractions <= 0) {
            return 0;
        }
        Predictor predictor = PositionPredict.INSTANCE.getPredictor(target);
        Vec3d predictedPos =
                PositionPredict.INSTANCE.attackPredictArgument.get().predict(target);
        Vec3d knownMovement = predictor.getKnownDeltaMovement();
        Box predictedBox = target.getBoundingBox().offset(predictedPos.subtract(target.getPos()));
        boolean collision =
                mc.player.getBoundingBox().contains(target.getBoundingBox().getCenter());
        int maxY = (int) Math.ceil(predictedBox.maxY);
        int minY = (int) Math.floor(predictedBox.minY);
        Set<BlockPos> candidates = new LinkedHashSet<>();

        boolean crawling = target.getPose() == EntityPose.SWIMMING;

        Set<BlockPos> ceilings = new LinkedHashSet<>();
        if (ceiling.get()) {
            var headOccupied = MathUtils.getOccupiedHorizontalBlockPositions(predictedBox, maxY);
            ceilings.addAll(headOccupied);
        }

        if (footPlate.get()) {
            var headOccupied = MathUtils.getOccupiedHorizontalBlockPositions(predictedBox, minY - 1);
            ceilings.addAll(headOccupied);
        }
        candidates.addAll(ceilings);
        Direction[] footDirections = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

        Set<BlockPos> footCandidates = new LinkedHashSet<>();
        var footOccupied = MathUtils.getOccupiedHorizontalBlockPositions(predictedBox, minY);
        Set<BlockPos> footAroundCandidates = getSurroundCandidates(footOccupied, footDirections);
        if (crawling ? headAroundCrawling.get() : headAroundStanding.get()) {
            Direction[] headDirections = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
            Arrays.sort(headDirections, Comparator.comparingDouble(direction -> -(Vec3d.of(direction.getVector())
                    .dotProduct(knownMovement))));
            for (int y = (crawling ? minY : minY + 1); y < maxY; y++) {
                var headOccupied = MathUtils.getOccupiedHorizontalBlockPositions(predictedBox, y);
                footCandidates.addAll(getSurroundCandidates(headOccupied, headDirections));
            }
        }
        for (var bp : footAroundCandidates) {
            BlockState stateUp = mc.world.getBlockState(bp.up());
            if (foot.get() || (footCandidates.contains(bp.up()) && (stateUp.isAir() || stateUp.isLiquid()))) {
                footCandidates.add(bp);
            }
        }

        candidates.addAll(footCandidates);
        Set<BlockPos> scaffoldCandidates = new LinkedHashSet<>();
        if (scaffold.get()) {
            for (var y : new int[] {minY - 1, maxY}) {
                for (var bp0 : footAroundCandidates) {
                    BlockPos scaffold1 = bp0.withY(y);
                    BlockPos scaffoldAgaint = y == minY - 1 ? scaffold1.up() : scaffold1.down();
                    BlockState state1 = mc.world.getBlockState(scaffoldAgaint);
                    if (candidates.contains(scaffoldAgaint) && (state1.isAir() || state1.isLiquid())) {
                        scaffoldCandidates.add(scaffold1);
                    } else {
                        for (var dir : footDirections) {
                            BlockPos bp2 = scaffold1.offset(dir);
                            if (ceilings.contains(bp2)) {
                                BlockState state2 = mc.world.getBlockState(bp2);
                                if (state2.isAir() || state2.isLiquid()) {
                                    if (InteractionTasks.checkPositionPlace(scaffold1, dir, mc.player.getPos())) {
                                        scaffoldCandidates.add(scaffold1);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        candidates.addAll(scaffoldCandidates);
        if (collision) {
            candidates.removeAll(footCandidates);
        }
        List<BlockPos> ordered = new ArrayList<>(candidates);
        IndexEntry<ItemStack> supply = supplyBlocks();
        if (supply == null) {
            return 0;
        }

        boolean handIsOffhand = offhand.get() || supply.index() == 40;
        int maxPlaces = Math.min(remainingInteractions, supply.val().getCount());
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
            if (!InteractExtra.INSTANCE.isWithinInteractRange(
                            mc.player.getPos(), hitResult.val().getBlockPos())
                    || !InteractionTasks.canHandleInteract(mode.get(), hitResult.val())) {
                continue;
            }
            if (placed == 0) {
                callback = InvExtra.INSTANCE.swapItemToHand(supply.index(), handIsOffhand, ghostHand.get());
                if (callback == null) {
                    return 0;
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
        return placed;
    }

    private Set<BlockPos> getSurroundCandidates(Collection<BlockPos> occupied, Direction[] directions) {
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

    private int tryAutoCrawl(Vec3d predictedPos) {
        BlockPos feetPos = BlockPos.ofFloored(predictedPos);
        BlockPos supportPos = feetPos.down();
        BlockState supportState = mc.world.getBlockState(supportPos);
        if (supportState.getCollisionShape(mc.world, supportPos).isEmpty()) {
            return 0;
        }

        BlockPos headPos = feetPos.up();
        BlockState headState = mc.world.getBlockState(headPos);
        if (headState.getBlock() instanceof FenceGateBlock) {
            return headState.get(FenceGateBlock.OPEN) ? interactWithCrawlBlock(headPos) : 0;
        }
        if (headState.getBlock() instanceof TrapdoorBlock) {
            if (headState.get(TrapdoorBlock.OPEN)) {
                BlockState closedState = headState.with(TrapdoorBlock.OPEN, false);
                return isLowTrapdoor(closedState, headPos) ? interactWithCrawlBlock(headPos) : 0;
            }
            return 0;
        }
        if (!headState.isAir() && !headState.isLiquid()) {
            return 0;
        }

        IndexEntry<ItemStack> supply = supplyCrawlBlock();
        if (supply == null) {
            return 0;
        }
        boolean trapdoor = ((BlockItem) supply.val().getItem()).getBlock() instanceof TrapdoorBlock;
        BlockHalf trapdoorHalf = trapdoor
                ? findLowTrapdoorHalf(
                        ((BlockItem) supply.val().getItem()).getBlock().getDefaultState(), headPos)
                : null;
        if (trapdoor && trapdoorHalf == null) {
            return 0;
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
            return 0;
        }

        boolean handIsOffhand = offhand.get() || supply.index() == 40;
        Runnable callback = InvExtra.INSTANCE.swapItemToHand(supply.index(), handIsOffhand, ghostHand.get());
        if (callback == null) {
            return 0;
        }
        Hand hand = handIsOffhand ? Hand.OFF_HAND : Hand.MAIN_HAND;
        boolean completed = InteractionTasks.handlePlaceMode(mode.get(), hitResult.val(), hand, swingHand.get());
        InteractionTasks.finishGhostHand(mode.get(), callback);
        return completed || (trapdoor && autoCrawlZeroTickInteract.get()) ? 1 : 0;
    }

    private int interactWithCrawlBlock(BlockPos pos) {
        BlockHitResult hitResult = InteractionTasks.createHitResult(pos, mc.player.getPos());
        if (!InteractionTasks.canHandleInteract(mode.get(), hitResult)
                || !InteractExtra.INSTANCE.isWithinInteractRange(mc.player.getPos(), pos)) {
            return 0;
        }
        return InteractionTasks.handlePlaceMode(
                        mode.get(), hitResult, offhand.get() ? Hand.OFF_HAND : Hand.MAIN_HAND, swingHand.get())
                ? 1
                : 0;
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
