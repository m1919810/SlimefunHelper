package me.matl114.hacks.modules.extra;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Listener;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.interact.Interact;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.modules.task.DisablerManager;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.enums.LegalInteractMode;
import me.matl114.hacks.utils.interact.InteractRequest;
import me.matl114.hacks.utils.interact.InteractionQueue;
import me.matl114.hacks.utils.interact.TaskAction;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.Debug;
import me.matl114.utils.InteractUtils;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.collections.FlagEntry;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class GrimIdiots extends BaseModule {
    public GrimIdiots() {
        super("GrimIdiots");
    }

    public final ModulePath root = makePath(Configs.EXTRA_CONFIG, "other.grim-experiments");
    public final ModulePath autoDick = root.add("auto-dick");

    public final KeyBindRef dickHotkey = hotkey(autoDick.add("place-dick-at-target"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::markDick))
            .build();

    public final EnumRef<Mode> dickMode = builder(autoDick.add("mode"), Mode.class)
            .defaultValue(Mode.GHOST_HAND_AIR)
            .build();

    public final IntRef dickDelay = intBuilder(autoDick.add("delay-ms"))
            .show(() -> dickMode.get().isIn(Mode.OVER_INTERACT))
            .defaultValue(6)
            .build();

    public final ModulePath autoWither = root.add("auto-wither");

    public final EnumRef<BypassMode> witherBypassMode = builder(autoWither.add("bypass-mode"), BypassMode.class)
            .defaultValue(BypassMode.GRIM)
            .build();

    public final KeyBindRef witherHotkey = hotkey(autoWither.add("place-wither"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::markWither))
            .build();

    public final FlagRef autoName =
            flagBuilder(autoWither.add("auto-name-wither")).build();

    public final ModulePath autoGolem = autoWither.add("auto-golem");

    public final EnumRef<BypassMode> golemBypassMode = builder(autoGolem.add("bypass-mode"), BypassMode.class)
            .defaultValue(BypassMode.GRIM)
            .build();

    public final KeyBindRef golemHotkey = hotkey(autoGolem.add("place-golem"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::markGolem))
            .build();

    public final ModulePath airMine = root.add("air-mine");

    public final KeyBindRef airMineKey = hotkey(airMine.add("air-mine-select-target"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::markAirMine))
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onPreInputEvent);
    }

    private final InteractionQueue placementQueue = new InteractionQueue(
            64, Hand.MAIN_HAND, GhostHandMode.INV_SWAP, false, LegalInteractMode.LEGACY_SLIENT_ROT, true);

    private Double scoreItem(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi) {
            if (bi == Items.OBSIDIAN) {
                return 1E8;
            }
            return bi.getBlock() instanceof BlockWithEntity
                    ? -333
                    : (double) bi.getBlock().getHardness();
        }
        return null;
    }

    private void markDick() {
        if (checkNull()) return;
        if (mc.crosshairTarget != null
                && mc.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos centerPos = hit.getBlockPos().offset(hit.getSide());
            Direction hor = mc.player.getHorizontalFacing().rotateClockwise(Direction.Axis.Y);
            List<BlockPos> placeSequence = List.of(
                    centerPos.offset(hor),
                    centerPos.offset(hor.getOpposite()),
                    centerPos.up(),
                    centerPos.up().up());
            Mode mode = dickMode.get();
            if (mode != Mode.NONE) warnGrimRequirements();
            enqueueDick(centerPos, placeSequence, mode);
        }
    }

    private void markWither() {
        if (checkNull()) return;
        Direction facing = mc.player.getHorizontalFacing();
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos center = findFloor(playerPos, facing);
        if (center == null) return;
        if (witherBypassMode.get() == BypassMode.GRIM) {
            warnGrimRequirements();
            enqueueTStructure(
                    center, facing, this::isWitherBase, stack -> stack.isOf(Items.WITHER_SKELETON_SKULL), false);
        } else {
            enqueueAirTStructure(
                    center, facing, this::isWitherBase, stack -> stack.isOf(Items.WITHER_SKELETON_SKULL), false);
        }
    }

    private void markGolem() {
        if (checkNull()) return;
        Direction facing = mc.player.getHorizontalFacing();
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos center = findFloor(playerPos, facing);
        if (center == null) return;
        Predicate<ItemStack> above = stack ->
                stack.isOf(Items.CARVED_PUMPKIN) || stack.isOf(Items.JACK_O_LANTERN) || stack.isOf(Items.PUMPKIN);
        if (golemBypassMode.get() == BypassMode.GRIM) {
            warnGrimRequirements();
            enqueueTStructure(center, facing, this::isGolemBase, above, true);
        } else {
            enqueueAirTStructure(center, facing, this::isGolemBase, above, true);
        }
    }

    private void warnGrimRequirements() {
        boolean grimDisabler = DisablerManager.INSTANCE != null
                && DisablerManager.INSTANCE.currentAC.get() == DisablerManager.SupportAC.GRIM;
        boolean dupRot = ViaFabricPlusHooks.isSupportDupRot();
        if (grimDisabler && dupRot) return;
        String missing = !grimDisabler && !dupRot
                ? "Disabler 当前不是 GRIM，且 ViaFabricPlus 不支持 DupRot"
                : !grimDisabler ? "Disabler 当前不是 GRIM" : "ViaFabricPlus 不支持 DupRot";
        Debug.chat(Text.literal("[GrimIdiots] " + missing + "；仍继续执行").formatted(Formatting.YELLOW));
    }

    private void enqueueAirTStructure(
            BlockPos pos,
            Direction dir,
            Predicate<ItemStack> tStruct,
            Predicate<ItemStack> aboveStruct,
            boolean golem) {
        Function<ItemStack, Double> baseScore = stack -> score(tStruct, stack);
        Function<ItemStack, Double> aboveScore = stack -> score(aboveStruct, stack);
        Direction turn = dir.rotateClockwise(Direction.Axis.Y);
        List<InteractRequest> steps = new ArrayList<>();
        steps.add(directRequest(pos, baseScore));
        steps.add(directRequest(pos.up(), baseScore));
        steps.add(directRequest(pos.up().offset(turn), baseScore));
        steps.add(directRequest(pos.up().offset(turn.getOpposite()), baseScore));
        addAboveRequests(steps, pos, dir, aboveScore, golem);
        placementQueue.enqueueAll(steps);
    }

    private BlockPos findFloor(BlockPos playerPos, Direction facing) {
        // Search the two requested distances, with a small vertical tolerance for nearby level ground.
        for (int distance = 2; distance <= 3; distance++) {
            BlockPos column = playerPos.offset(facing, distance);
            for (int yOffset = -3; yOffset <= 0; yOffset++) {
                BlockPos candidate = column.add(0, yOffset, 0);
                if (!mc.world.getBlockState(candidate.down()).isAir()
                        && mc.world.getBlockState(candidate).isAir()
                        && mc.world.getBlockState(candidate.up()).isAir()
                        && mc.world.getBlockState(candidate.up().up()).isAir()) return candidate;
            }
        }
        return null;
    }

    private boolean isWitherBase(ItemStack stack) {
        return stack.getItem() instanceof BlockItem it
                && it.getBlock().getDefaultState().isIn(BlockTags.WITHER_SUMMON_BASE_BLOCKS);
    }

    private boolean isGolemBase(ItemStack stack) {
        return stack.isOf(Items.IRON_BLOCK);
    }

    private Double score(Predicate<ItemStack> predicate, ItemStack stack) {
        return predicate.test(stack) ? 1.0 : null;
    }

    private InteractRequest request(
            Function<ItemStack, Double> itemScore,
            BiFunction<InteractionQueue, Boolean, TaskAction> action,
            Consumer<InteractionQueue> callback) {
        return new InteractRequest() {
            @Override
            public Double itemScore(InteractionQueue queue, ItemStack stack) {
                return itemScore.apply(stack);
            }

            @Override
            public TaskAction tickTask(InteractionQueue queue, boolean matched) {
                return action.apply(queue, matched);
            }

            @Override
            public void successCallback(InteractionQueue queue) {
                if (callback != null) callback.accept(queue);
            }
        };
    }

    private InteractRequest placeBlockRequest(BlockPos pos, Function<ItemStack, Double> itemScore) {
        return placeBlockRequest(pos, itemScore, null);
    }

    private InteractRequest placeBlockRequest(
            BlockPos pos, Function<ItemStack, Double> itemScore, Consumer<InteractionQueue> callback) {
        boolean[] interacted = {false};
        return request(
                itemScore,
                (queue, matched) -> {
                    interacted[0] = false;
                    if (!matched) return queue.abort();
                    FlagEntry<BlockHitResult> supporting = InteractionTasks.getPlaceSupportingResult(
                            pos, queue.enableAirPlace(), queue.enablePositionPlace());
                    if (!InteractUtils.canInteractAndPlace(mc.player, supporting)) return queue.success();
                    interacted[0] = true;
                    return queue.success(supporting.val());
                },
                queue -> {
                    if (interacted[0] && callback != null) callback.accept(queue);
                });
    }

    private InteractRequest directRequest(BlockPos pos, Direction face, Function<ItemStack, Double> itemScore) {
        return directRequest(pos, face, itemScore, null);
    }

    private InteractRequest directRequest(
            BlockPos pos, Direction face, Function<ItemStack, Double> itemScore, Consumer<InteractionQueue> callback) {
        return request(
                itemScore,
                (queue, matched) ->
                        matched ? queue.success(InteractionTasks.createHitResult(pos, face)) : queue.abort(),
                callback);
    }

    private InteractRequest directRequest(BlockPos pos, Function<ItemStack, Double> itemScore) {
        return directRequest(pos, itemScore, null);
    }

    private InteractRequest directRequest(
            BlockPos pos, Function<ItemStack, Double> itemScore, Consumer<InteractionQueue> callback) {
        return request(
                itemScore,
                (queue, matched) -> matched
                        ? queue.success(InteractionTasks.createHitResult(pos, mc.player.getPos()))
                        : queue.abort(),
                callback);
    }

    private boolean canPlaceBlock(InteractionQueue queue, BlockPos pos) {
        return InteractUtils.canInteractAndPlace(
                mc.player,
                InteractionTasks.getPlaceSupportingResult(pos, queue.enableAirPlace(), queue.enablePositionPlace()));
    }

    private int findSlot(Predicate<ItemStack> predicate) {
        var item = InventoryUtils.findPlayerItem(predicate, true, false);
        return item == null ? -1 : item.index();
    }

    private int findBestSlot() {
        var item = InventoryUtils.findBestPlayerItem(this::scoreItem, true, false);
        return item == null ? -1 : item.index();
    }

    private boolean flushTemporarySwap(IntSupplier itemSlot) {
        int slot = itemSlot.getAsInt();
        if (slot < 0) return false;
        DisablerManager.INSTANCE.setACPlaceQueue(false);
        Runnable restore;
        try {
            restore = InvExtra.INSTANCE.swapItemToHand(slot, false, GhostHandMode.INV_SWAP);
        } finally {
            DisablerManager.INSTANCE.setACPlaceQueue(true);
            DisablerManager.INSTANCE.flushACPlaceQueue();
        }
        if (restore == null) return false;
        restore.run();
        return true;
    }

    private void flushGhostSwap(boolean high) {
        DisablerManager.INSTANCE.setACPlaceQueue(false);
        Runnable restoreBase = null;
        Runnable restoreBug = null;
        try {
            int baseSlot = findBestSlot();
            if (baseSlot >= 0) {
                restoreBase = InvExtra.INSTANCE.swapItemToHand(baseSlot, false, GhostHandMode.INV_SWAP);
            }
            if (!high) {
                int bugSlot = findSlot(this::isHalfBlock);
                if (bugSlot >= 0) {
                    restoreBug = InvExtra.INSTANCE.swapItemToHand(bugSlot, false, GhostHandMode.INV_SWAP);
                }
            }
        } finally {
            DisablerManager.INSTANCE.setACPlaceQueue(true);
            DisablerManager.INSTANCE.flushACPlaceQueue();
        }
        try {
            if (restoreBug != null) restoreBug.run();
        } finally {
            if (restoreBase != null) restoreBase.run();
        }
    }

    private void enqueueTStructure(
            BlockPos pos,
            Direction dir,
            Predicate<ItemStack> tStruct,
            Predicate<ItemStack> aboveStruct,
            boolean golem) {
        Function<ItemStack, Double> baseScore = stack -> score(tStruct, stack);
        Function<ItemStack, Double> aboveScore = stack -> score(aboveStruct, stack);
        int[] startTick = {0};
        boolean[] bugPath = {false};
        boolean[] hasBugBlock = {false};
        placementQueue.enqueueAll(List.of(request(
                stack -> 1.0,
                (queue, matched) -> {
                    if (!hasTStructureItems(tStruct, aboveStruct)) return queue.abort();
                    var bug = InventoryUtils.findPlayerItem(this::isBugBlock, true, false);
                    hasBugBlock[0] = bug != null;
                    bugPath[0] = bug != null && bug.val().getCount() >= 2;
                    startTick[0] = Tasks.getTick();
                    return queue.success();
                },
                queue -> {
                    List<InteractRequest> steps = new ArrayList<>();
                    Consumer<InteractionQueue> bugBlockFlush =
                            hasBugBlock[0] ? ignored -> flushTemporarySwap(() -> findSlot(this::isBugBlock)) : null;
                    steps.add(placeBlockRequest(pos, baseScore));
                    steps.add(placeBlockRequest(pos.up(), baseScore, bugBlockFlush));
                    if (bugPath[0]) {
                        Vec3d eyeDirection = mc.player.getEyePos().subtract(pos.toCenterPos());
                        Direction turn = dir.rotateClockwise(Direction.Axis.Y);
                        if (eyeDirection.dotProduct(Vec3d.of(turn.getVector())) > 0) turn = turn.getOpposite();
                        steps.add(directRequest(pos.up(), turn, baseScore, bugBlockFlush));
                        steps.add(directRequest(pos.up(), turn.getOpposite(), baseScore, bugBlockFlush));
                    } else {
                        Direction turn = dir.rotateClockwise(Direction.Axis.Y);
                        steps.add(placeTArmRequest(pos, turn, baseScore, tStruct, bugBlockFlush));
                        steps.add(placeTArmRequest(pos, turn.getOpposite(), baseScore, tStruct, bugBlockFlush));
                        steps.add((queue1, matched) -> {
                            if (!hasTStructureItems(tStruct, aboveStruct)) return queue1.abort();
                            if (Tasks.getTick() < startTick[0] + 5) return queue1.pause();
                            BlockPos abovePos = pos.up().up();
                            if (!golem) abovePos = abovePos.offset(dir.rotateClockwise(Direction.Axis.Y));
                            if (canPlaceBlock(queue1, abovePos)) return queue1.success();
                            PlayerInputManager.INSTANCE.addJumpModifier(0, true, 0);
                            return queue1.pause();
                        });
                        steps.add((queue1, matched) -> {
                            if (!hasTStructureItems(tStruct, aboveStruct)) return queue1.abort();
                            return Tasks.getTick() <= startTick[0] + 6 ? queue1.pause() : queue1.success();
                        });
                    }
                    addAboveRequests(steps, pos, dir, aboveScore, golem);
                    queue.enqueueNext(steps);
                })));
    }

    private InteractRequest placeTArmRequest(
            BlockPos pos,
            Direction turn,
            Function<ItemStack, Double> baseScore,
            Predicate<ItemStack> tStruct,
            Consumer<InteractionQueue> afterPlacement) {
        boolean[] fallback = {false};
        return request(
                baseScore,
                (queue, matched) -> {
                    if (!matched) return queue.abort();
                    BlockPos armPos = pos.up().offset(turn);
                    var supporting = InteractionTasks.getPlaceSupportingResult(
                            mc.player.getPos(),
                            armPos,
                            turn.getOpposite(),
                            queue.enableAirPlace(),
                            queue.enablePositionPlace());
                    if (supporting != null
                            && supporting.val().getBlockPos().equals(pos.up())
                            && supporting.val().getSide() == turn
                            && InteractUtils.canInteractAndPlace(mc.player, supporting)) {
                        return queue.success(supporting.val());
                    }
                    fallback[0] = true;
                    return queue.success();
                },
                queue -> {
                    if (!fallback[0]) {
                        if (afterPlacement != null) afterPlacement.accept(queue);
                        return;
                    }
                    Function<ItemStack, Double> empty = stack -> score(ItemStack::isEmpty, stack);
                    queue.enqueueNext(List.of(
                            placeBlockRequest(
                                    pos.offset(turn), empty, ignored -> flushTemporarySwap(() -> findSlot(tStruct))),
                            directRequest(
                                    pos.offset(turn),
                                    Direction.UP,
                                    empty,
                                    ignored -> flushTemporarySwap(() -> findSlot(tStruct))),
                            directRequest(pos.offset(turn).up(), baseScore, afterPlacement)));
                });
    }

    private void addAboveRequests(
            List<InteractRequest> steps,
            BlockPos pos,
            Direction dir,
            Function<ItemStack, Double> aboveScore,
            boolean golem) {
        if (golem) {
            steps.add(directRequest(pos.up(), Direction.UP, aboveScore));
            steps.add(request(
                    stack -> score(s -> s.isOf(Items.SHEARS), stack),
                    (queue, matched) -> {
                        if (mc.world.getBlockState(pos.up().up()).getBlock() != Blocks.PUMPKIN || !matched)
                            return queue.success();
                        return queue.success(
                                InteractionTasks.createHitResult(pos.up().up(), mc.player.getPos()));
                    },
                    null));
        } else {
            Direction turn = dir.rotateClockwise(Direction.Axis.Y);
            steps.add(directRequest(pos.up().offset(turn), Direction.UP, aboveScore));
            steps.add(directRequest(pos.up().offset(turn.getOpposite()), Direction.UP, aboveScore));
            steps.add(directRequest(pos.up(), Direction.UP, aboveScore));
        }
    }

    private boolean hasTStructureItems(Predicate<ItemStack> base, Predicate<ItemStack> above) {
        return InventoryUtils.findPlayerItem(base, true, false) != null
                && InventoryUtils.findPlayerItem(above, true, false) != null
                && InventoryUtils.findPlayerItem(ItemStack::isEmpty, true, true) != null;
    }

    TimerExecutor executor = new TimerExecutor();

    private void tickAutoNameWither() {
        if (autoName.get()) {
            if (executor.canRun(2)) {
                for (var re : mc.world.getEntitiesByType(
                        EntityType.WITHER,
                        mc.player.getBoundingBox().expand(8, 8, 8),
                        entity -> !entity.hasCustomName())) {
                    if (!re.hasCustomName()) {
                        var entry = InventoryUtils.findPlayerItem(
                                stack -> stack.isOf(Items.NAME_TAG) && stack.contains(DataComponentTypes.CUSTOM_NAME),
                                true,
                                false);
                        if (entry != null) {
                            var val = InvExtra.INSTANCE.swapItemToHand(entry.index(), false, GhostHandMode.INV_SWAP);
                            if (val != null) {
                                Interact.INSTANCE.interactEntity(re);
                                val.run();
                                executor.mark();
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private void enqueueDick(BlockPos centerPos, List<BlockPos> placeSequence, Mode mode) {
        if (mode == Mode.NONE) {
            placementQueue.enqueueAll(List.of(
                    directRequest(placeSequence.get(0), this::scoreItem),
                    directRequest(placeSequence.get(1), this::scoreItem),
                    directRequest(centerPos, this::scoreItem),
                    directRequest(placeSequence.get(2), this::scoreItem),
                    directRequest(placeSequence.get(3), this::scoreItem)));
        } else if (mode == Mode.OVER_INTERACT) {
            int[] startTick = {0};
            List<InteractRequest> steps = new ArrayList<>();
            steps.add(placeBlockRequest(placeSequence.get(0), this::scoreItem));
            for (int i = 0; i < 9; i++) {
                steps.add(placeBlockRequest(placeSequence.get(1), this::scoreItem));
            }
            steps.add(placeBlockRequest(centerPos, this::scoreItem));
            steps.add(placeBlockRequest(placeSequence.get(2), this::scoreItem));
            steps.add((queue, matched) -> {
                startTick[0] = Tasks.getTick();
                return queue.success();
            });
            steps.add(request(
                    this::scoreItem,
                    (queue, matched) -> {
                        if (!matched) return queue.abort();
                        BlockPos top = placeSequence.get(3);
                        if (!canPlaceBlock(queue, top)) {
                            PlayerInputManager.INSTANCE.addJumpModifier(0, true, 0);
                            return queue.pause();
                        }
                        if (Tasks.getTick() < startTick[0] + 1) return queue.pause();
                        FlagEntry<BlockHitResult> supporting = InteractionTasks.getPlaceSupportingResult(
                                top, queue.enableAirPlace(), queue.enablePositionPlace());
                        return queue.success(supporting.val());
                    },
                    null));
            steps.add((queue, matched) -> Tasks.getTick() <= Math.max(startTick[0] + 1, startTick[0] + dickDelay.get())
                    ? queue.pause()
                    : queue.success());
            steps.add(directRequest(placeSequence.get(3), this::scoreItem));
            steps.add(directRequest(placeSequence.get(2), this::scoreItem));
            placementQueue.enqueueAll(steps);
        } else if (mode == Mode.GHOST_HAND_AIR) {
            boolean[] high = {false};
            List<InteractRequest> steps = new ArrayList<>();
            steps.add(placeBlockRequest(placeSequence.get(0), this::scoreItem));
            steps.add(placeBlockRequest(placeSequence.get(1), this::scoreItem));
            steps.add(request(
                    this::scoreItem,
                    (queue, matched) -> {
                        if (!matched) return queue.abort();
                        FlagEntry<BlockHitResult> supporting =
                                InteractionTasks.getPlaceSupportingResult(centerPos, false, false);
                        high[0] = mc.player.getEyeY() > centerPos.getY() + 2;
                        if (!InteractUtils.canInteractAndPlace(mc.player, supporting)
                                || (!high[0]
                                        && InventoryUtils.findPlayerItem(this::isHalfBlock, true, false) == null)) {
                            PlayerInputManager.INSTANCE.addJumpModifier(0, true, 0);
                            return queue.pause();
                        }
                        return queue.success();
                    },
                    null));
            steps.addAll(ghostDickRequests(centerPos, high));
            placementQueue.enqueueAll(steps);
        }
    }

    private List<InteractRequest> ghostDickRequests(BlockPos centerPos, boolean[] high) {
        Function<ItemStack, Double> empty = stack -> score(ItemStack::isEmpty, stack);
        List<InteractRequest> steps = new ArrayList<>();
        steps.add(request(
                empty,
                (queue, matched) -> {
                    if (!matched) return queue.abort();
                    FlagEntry<BlockHitResult> supporting =
                            InteractionTasks.getPlaceSupportingResult(centerPos, false, false);
                    if (!InteractUtils.canInteractAndPlace(mc.player, supporting)) {
                        PlayerInputManager.INSTANCE.addJumpModifier(0, true, 0);
                        return queue.pause();
                    }
                    return queue.success(supporting.val());
                },
                ignored -> flushTemporarySwap(this::findBestSlot)));
        steps.add(directRequest(centerPos, Direction.UP, empty, ignored -> flushGhostSwap(high[0])));
        steps.add(directRequest(centerPos.up(), Direction.UP, this::scoreItem));
        steps.add(directRequest(centerPos.up(), Direction.UP, this::scoreItem));
        return steps;
    }

    private void enqueueAirMine(BlockPos pos) {
        boolean[] interacted = {false};
        Function<ItemStack, Double> empty = stack -> score(ItemStack::isEmpty, stack);
        placementQueue.enqueueAll(List.of(request(
                empty,
                (queue, matched) -> {
                    interacted[0] = false;
                    FlagEntry<BlockHitResult> supporting = InteractionTasks.getPlaceSupportingResult(pos, false, false);
                    if (!matched || !InteractUtils.canInteractAndPlace(mc.player, supporting)) {
                        return queue.success();
                    }
                    interacted[0] = true;
                    return queue.success(supporting.val());
                },
                queue -> {
                    if (!interacted[0]) return;
                    if (flushTemporarySwap(this::findBestSlot)) {
                        PlayerInteractionAccess.of(mc.interactionManager).sendStartBreakPacket(pos);
                    }
                })));
    }

    private void markAirMine() {
        if (checkNull()) return;
        if (mc.crosshairTarget != null
                && mc.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos().offset(hit.getSide());
            enqueueAirMine(pos);
        }
    }

    private boolean isHalfBlock(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi) {
            if (InteractionTasks.isSpecialPositionPlaceState(bi.getBlock().getDefaultState())) {
                return true;
            }
            try {
                Box shape = InteractionTasks.getPositionPlaceBoundary(
                        new BlockPos(0, 0, 0), bi.getBlock().getDefaultState());
                return shape.maxY < 0.6;
            } catch (Throwable e) {
            }
        }
        return false;
    }

    private boolean isBugBlock(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi) {
            if (InteractionTasks.isSpecialPositionPlaceState(bi.getBlock().getDefaultState())) {
                return true;
            }
        }
        return false;
    }

    private void onPreInputEvent() {
        if (checkNull()) {
            return;
        }
        placementQueue.tick();
        tickAutoNameWither();
    }

    public enum Mode implements ConfigEnum {
        NONE,
        OVER_INTERACT,
        GHOST_HAND_AIR,
        GHOST_SWAP_AIR;

        @Override
        public String getConfigEnumType() {
            return "auto_dick_mode";
        }
    }

    public enum BypassMode implements ConfigEnum {
        NONE,
        GRIM;

        @Override
        public String getConfigEnumType() {
            return "grim_idiots_bypass_mode";
        }
    }
}
