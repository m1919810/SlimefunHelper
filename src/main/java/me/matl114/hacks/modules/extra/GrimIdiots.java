package me.matl114.hacks.modules.extra;

import java.util.List;
import java.util.function.Predicate;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.ac.DisablerManager;
import me.matl114.hacks.modules.interact.Interact;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.utils.HotKeyUtils;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.tasks.TimerExecutor;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.InteractUtils;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
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

    public final KeyBindRef witherHotkey = hotkey(autoWither.add("place-wither"), new MultiKeyBind())
            .registerHotkey(HotKeyUtils.wrapAsHandler(this::markWither))
            .build();

    public final FlagRef autoName =
            flagBuilder(autoWither.add("auto-name-wither")).build();

    public final ModulePath autoGolem = autoWither.add("auto-golem");

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
    // dick fields
    BlockPos centerPos;
    Direction facingDirection;
    List<BlockPos> placeSequence;
    int placeProgress = 0;
    int startTick;

    // Auto-wither draft state. The exact fake-slab interaction sequence can be refined separately.
    BlockPos witherCenter;
    Direction witherFacingDirection;

    BlockPos golemCenter;
    Direction golemDirection;

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
            centerPos = hit.getBlockPos().offset(hit.getSide());
            facingDirection = mc.player.getHorizontalFacing();
            Direction hor = facingDirection.rotateClockwise(Direction.Axis.Y);
            placeSequence = List.of(
                    centerPos.offset(hor),
                    centerPos.offset(hor.getOpposite()),
                    centerPos.up(),
                    centerPos.up().up());
            placeProgress = 0;
            startTick = 0;
        }
    }

    private void markWither() {
        if (checkNull()) return;
        Direction facing = mc.player.getHorizontalFacing();
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos center = findFloor(playerPos, facing);
        if (center == null) return;
        witherCenter = center;
        witherFacingDirection = facing;
        startTick = 0;
    }

    private void markGolem() {
        if (checkNull()) return;
        Direction facing = mc.player.getHorizontalFacing();
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos center = findFloor(playerPos, facing);
        if (center == null) return;
        golemCenter = center;
        golemDirection = facing;
        startTick = 0;
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

    private void tickAutoWither() {
        if (witherCenter == null || witherFacingDirection == null) return;
        Direction direction = witherFacingDirection.rotateClockwise(Direction.Axis.Y);
        placeTStructure(
                witherCenter,
                witherFacingDirection,
                this::isWitherBase,
                (stack) -> stack.isOf(Items.WITHER_SKELETON_SKULL),
                () -> {
                    Interact.INSTANCE.interactBlock(
                            InteractionTasks.createHitResult(witherCenter.up().offset(direction), Direction.UP));
                    Interact.INSTANCE.interactBlock(InteractionTasks.createHitResult(
                            witherCenter.up().offset(direction.getOpposite()), Direction.UP));
                    Interact.INSTANCE.interactBlock(InteractionTasks.createHitResult(witherCenter.up(), Direction.UP));
                },
                () -> {
                    witherCenter = null;
                    witherFacingDirection = null;
                });
    }

    private void tickAutoGolem() {
        if (golemCenter == null || golemDirection == null) return;
        placeTStructure(
                golemCenter,
                golemDirection,
                this::isGolemBase,
                (stack) -> stack.isOf(Items.CARVED_PUMPKIN)
                        || stack.isOf(Items.JACK_O_LANTERN)
                        || stack.isOf(Items.PUMPKIN),
                () -> {
                    Interact.INSTANCE.interactBlock(InteractionTasks.createHitResult(golemCenter.up(), Direction.UP));
                    if (mc.world.getBlockState(golemCenter.up().up()).getBlock() == Blocks.PUMPKIN) {
                        var entry = InventoryUtils.findPlayerItem(s -> s.isOf(Items.SHEARS), true, false);
                        if (entry != null) {
                            var runnable =
                                    InvExtra.INSTANCE.swapItemToHand(entry.index(), false, GhostHandMode.INV_SWAP);
                            if (runnable != null) {
                                Interact.INSTANCE.interactBlock(golemCenter.up().up());
                                runnable.run();
                            }
                        }
                    }
                },
                () -> {
                    golemCenter = null;
                    golemDirection = null;
                });
    }

    private void placeTStructure(
            BlockPos pos,
            Direction dir,
            Predicate<ItemStack> tStruct,
            Predicate<ItemStack> aboveStruct,
            Runnable aboveCallback,
            Runnable finishCallback) {
        var witherBlock = InventoryUtils.findPlayerItem(aboveStruct, true, false);
        var soulBlock = InventoryUtils.findPlayerItem(tStruct, true, false);
        var airBlock = InventoryUtils.findPlayerItem(ItemStack::isEmpty, true, true);
        boolean finish = false;
        prgress:
        {
            if (witherBlock == null || soulBlock == null || airBlock == null) {
                finish = true;
                break prgress;
            }
            var bugBlock = InventoryUtils.findPlayerItem((this::isBugBlock), true, false);
            if (bugBlock != null && bugBlock.val().getCount() >= 2 && startTick == 0) {
                var wither = soulBlock;
                Runnable run1 = InvExtra.INSTANCE.swapItemToHand(wither.index(), false, GhostHandMode.INV_SWAP);
                Interact.INSTANCE.placeBlock(pos);

                Interact.INSTANCE.placeBlock(pos.up());
                var bug = InventoryUtils.findPlayerItem(this::isBugBlock, true, false);
                DisablerManager.INSTANCE.setACPlaceQueue(false);
                Runnable run3 = InvExtra.INSTANCE.swapItemToHand(bug.index(), false, GhostHandMode.INV_SWAP);
                DisablerManager.INSTANCE.setACPlaceQueue(true);
                DisablerManager.INSTANCE.flushACPlaceQueue();
                run3.run();
                Vec3d direction = mc.player.getEyePos().subtract(pos.toCenterPos());
                Direction turn = dir.rotateClockwise(Direction.Axis.Y);
                if (direction.dotProduct(Vec3d.of(turn.getVector())) > 0) {
                    turn = turn.getOpposite();
                }
                Interact.INSTANCE.interactBlock(InteractionTasks.createHitResult(pos.up(), turn));
                DisablerManager.INSTANCE.setACPlaceQueue(false);
                Runnable run4 = InvExtra.INSTANCE.swapItemToHand(bug.index(), false, GhostHandMode.INV_SWAP);
                DisablerManager.INSTANCE.setACPlaceQueue(true);
                DisablerManager.INSTANCE.flushACPlaceQueue();
                run4.run();
                Interact.INSTANCE.interactBlock(InteractionTasks.createHitResult(pos.up(), turn.getOpposite()));
                DisablerManager.INSTANCE.setACPlaceQueue(false);
                Runnable run5 = InvExtra.INSTANCE.swapItemToHand(bug.index(), false, GhostHandMode.INV_SWAP);
                DisablerManager.INSTANCE.setACPlaceQueue(true);
                DisablerManager.INSTANCE.flushACPlaceQueue();
                run5.run();
                run1.run();
                Runnable run6 = InvExtra.INSTANCE.swapItemToHand(witherBlock.index(), false, GhostHandMode.INV_SWAP);
                aboveCallback.run();
                run6.run();
                finish = true;
            } else {
                // zero tick
                if (startTick == 0) {
                    startTick = Tasks.getTick();
                    Runnable run1 = InvExtra.INSTANCE.swapItemToHand(soulBlock.index(), false, GhostHandMode.INV_SWAP);
                    var air = InventoryUtils.findPlayerItem(ItemStack::isEmpty, true, true);
                    Interact.INSTANCE.placeBlock(pos);
                    Interact.INSTANCE.placeBlock(pos.up());
                    Direction turn = dir.rotateClockwise(Direction.Axis.Y);
                    var placement = Interact.INSTANCE.getPlaceSupportingResult(
                            mc.player.getPos(), pos.up().offset(turn), dir);
                    if (placement != null) {
                        Interact.INSTANCE.interactBlock(placement.val());
                    } else {
                        Runnable run2 = InvExtra.INSTANCE.swapItemToHand(air.index(), false, GhostHandMode.INV_SWAP);
                        Interact.INSTANCE.placeBlock(pos.offset(turn));
                        DisablerManager.INSTANCE.setACPlaceQueue(false);
                        run2.run();
                        DisablerManager.INSTANCE.setACPlaceQueue(true);
                        DisablerManager.INSTANCE.flushACPlaceQueue();
                        Runnable run3 = InvExtra.INSTANCE.swapItemToHand(air.index(), false, GhostHandMode.INV_SWAP);
                        Interact.INSTANCE.interactBlock(
                                InteractionTasks.createHitResult(pos.offset(turn), Direction.UP));
                        DisablerManager.INSTANCE.setACPlaceQueue(false);
                        run3.run();
                        DisablerManager.INSTANCE.setACPlaceQueue(true);
                        DisablerManager.INSTANCE.flushACPlaceQueue();
                        Interact.INSTANCE.interactBlock(pos.offset(turn).up());
                    }
                    turn = turn.getOpposite();
                    var placement2 = Interact.INSTANCE.getPlaceSupportingResult(
                            mc.player.getPos(), pos.up().offset(turn), dir);
                    if (placement2 != null) {
                        Interact.INSTANCE.interactBlock(placement2.val());
                    } else {
                        Runnable run2 = InvExtra.INSTANCE.swapItemToHand(air.index(), false, GhostHandMode.INV_SWAP);
                        Interact.INSTANCE.placeBlock(pos.offset(turn));
                        DisablerManager.INSTANCE.setACPlaceQueue(false);
                        run2.run();
                        DisablerManager.INSTANCE.setACPlaceQueue(true);
                        DisablerManager.INSTANCE.flushACPlaceQueue();
                        Runnable run3 = InvExtra.INSTANCE.swapItemToHand(air.index(), false, GhostHandMode.INV_SWAP);
                        Interact.INSTANCE.interactBlock(
                                InteractionTasks.createHitResult(pos.offset(turn), Direction.UP));
                        DisablerManager.INSTANCE.setACPlaceQueue(false);
                        run3.run();
                        DisablerManager.INSTANCE.setACPlaceQueue(true);
                        DisablerManager.INSTANCE.flushACPlaceQueue();
                        Interact.INSTANCE.interactBlock(pos.offset(turn).up());
                    }
                    run1.run();
                } else if (startTick + 5 == Tasks.getTick()) {
                    PlayerInputManager.INSTANCE.addJumpModifier(0, true, 0);
                } else if (startTick + 6 < Tasks.getTick()) {
                    Runnable run6 =
                            InvExtra.INSTANCE.swapItemToHand(witherBlock.index(), false, GhostHandMode.INV_SWAP);
                    aboveCallback.run();
                    run6.run();
                    finish = true;
                }
            }
        }
        if (finish) {
            finishCallback.run();
            startTick = 0;
        }
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

    private void tickAutoDick() {
        if (centerPos != null && facingDirection != null && placeSequence != null && placeSequence.size() >= 4) {
            IndexEntry<ItemStack> findStack = InventoryUtils.findBestPlayerItem(this::scoreItem, true, false);
            if (findStack == null) {
                centerPos = null;
                facingDirection = null;
                placeProgress = 0;
                placeSequence = null;
            }
            boolean finish = false;
            progress:
            switch (dickMode.get()) {
                case OVER_INTERACT -> {
                    if (startTick == 0) {
                        Runnable callback =
                                InvExtra.INSTANCE.swapItemToHand(findStack.index(), false, GhostHandMode.INV_SWAP);
                        if (callback != null) {

                            Interact.INSTANCE.placeBlock(placeSequence.get(0));
                            for (var i = 0; i < 9; ++i) {
                                Interact.INSTANCE.placeBlock(placeSequence.get(1));
                            }
                            Interact.INSTANCE.placeBlock(centerPos);
                            Interact.INSTANCE.placeBlock(placeSequence.get(2));
                            startTick = Tasks.getTick();
                            PlayerInputManager.INSTANCE.addJumpModifier(0, true, 0);
                            callback.run();
                        }

                    } else if (startTick + 1 == Tasks.getTick()) {
                        Interact.INSTANCE.placeBlock(placeSequence.get(3));
                    } else if (startTick + dickDelay.get() < Tasks.getTick()) {
                        Runnable callback =
                                InvExtra.INSTANCE.swapItemToHand(findStack.index(), false, GhostHandMode.INV_SWAP);
                        if (callback != null) {
                            Interact.INSTANCE.interactBlock(placeSequence.get(3));
                            Interact.INSTANCE.interactBlock(placeSequence.get(2));
                            callback.run();
                            finish = true;
                            break progress;
                        }
                    }
                }
                case GHOST_HAND_AIR -> {
                    Runnable callback =
                            InvExtra.INSTANCE.swapItemToHand(findStack.index(), false, GhostHandMode.INV_SWAP);
                    if (callback != null) {
                        while (placeProgress < 2) {
                            Interact.INSTANCE.placeBlock(placeSequence.get(placeProgress));
                            placeProgress += 1;
                        }
                        // use bug block to prevent jump
                        FlagEntry<BlockHitResult> placeSupporting =
                                InteractionTasks.getPlaceSupportingResult(centerPos, false, false);
                        if (InteractUtils.canInteractAndPlace(mc.player, placeSupporting)) {
                            var bugBlock = InventoryUtils.findPlayerItem(this::isHalfBlock, true, false);
                            if (mc.player.getEyeY() > centerPos.getY() + 2) {
                                var entry = InventoryUtils.findPlayerItem(ItemStack::isEmpty, true, true);
                                if (entry != null) {
                                    Runnable runnable = InvExtra.INSTANCE.swapItemToHand(
                                            entry.index(), false, GhostHandMode.INV_SWAP);
                                    Interact.INSTANCE.interactBlock(placeSupporting.val());
                                    DisablerManager.INSTANCE.setACPlaceQueue(false);
                                    runnable.run();
                                    DisablerManager.INSTANCE.setACPlaceQueue(true);
                                    DisablerManager.INSTANCE.flushACPlaceQueue();
                                    runnable = InvExtra.INSTANCE.swapItemToHand(
                                            entry.index(), false, GhostHandMode.INV_SWAP);
                                    Interact.INSTANCE.interactBlock(
                                            InteractionTasks.createHitResult(centerPos, Direction.UP));
                                    DisablerManager.INSTANCE.setACPlaceQueue(false);
                                    runnable.run();
                                    DisablerManager.INSTANCE.setACPlaceQueue(true);
                                    DisablerManager.INSTANCE.flushACPlaceQueue();
                                    Interact.INSTANCE.interactBlock(
                                            InteractionTasks.createHitResult(centerPos.up(), Direction.UP));
                                    Interact.INSTANCE.interactBlock(
                                            InteractionTasks.createHitResult(centerPos.up(), Direction.UP));
                                    callback.run();
                                    finish = true;
                                    break progress;
                                }
                            } else if (bugBlock != null) {
                                var entry = InventoryUtils.findPlayerItem(ItemStack::isEmpty, true, true);
                                if (entry != null) {
                                    Runnable runnable = InvExtra.INSTANCE.swapItemToHand(
                                            entry.index(), false, GhostHandMode.INV_SWAP);
                                    Interact.INSTANCE.interactBlock(placeSupporting.val());
                                    DisablerManager.INSTANCE.setACPlaceQueue(false);
                                    runnable.run();
                                    DisablerManager.INSTANCE.setACPlaceQueue(true);
                                    DisablerManager.INSTANCE.flushACPlaceQueue();
                                    runnable = InvExtra.INSTANCE.swapItemToHand(
                                            entry.index(), false, GhostHandMode.INV_SWAP);
                                    Interact.INSTANCE.interactBlock(
                                            InteractionTasks.createHitResult(centerPos, Direction.UP));
                                    DisablerManager.INSTANCE.setACPlaceQueue(false);
                                    runnable.run();
                                    bugBlock = InventoryUtils.findPlayerItem(this::isHalfBlock, true, false);
                                    Runnable runnable1 = InvExtra.INSTANCE.swapItemToHand(
                                            bugBlock.index(), false, GhostHandMode.INV_SWAP);
                                    DisablerManager.INSTANCE.setACPlaceQueue(true);
                                    DisablerManager.INSTANCE.flushACPlaceQueue();
                                    runnable1.run();
                                    Interact.INSTANCE.interactBlock(
                                            InteractionTasks.createHitResult(centerPos.up(), Direction.UP));
                                    Interact.INSTANCE.interactBlock(
                                            InteractionTasks.createHitResult(centerPos.up(), Direction.UP));
                                    callback.run();
                                    finish = true;
                                    break progress;
                                }
                            } else {
                                PlayerInputManager.INSTANCE.addJumpModifier(0, true, 0);
                            }
                            callback.run();

                        } else {
                            callback.run();
                            finish = true;
                            break progress;
                        }
                    }
                }
                case GHOST_SWAP_AIR -> {}
            }
            if (finish) {
                centerPos = null;
                facingDirection = null;
                placeProgress = 0;
                placeSequence = null;
            }
        }
    }

    private void tickAirMine() {
        if (airMinePos != null) {
            BlockPos pos = airMinePos;
            airMinePos = null;
            FlagEntry<BlockHitResult> placeSupporting = InteractionTasks.getPlaceSupportingResult(pos, false, false);
            if (InteractUtils.canInteractAndPlace(mc.player, placeSupporting)) {
                IndexEntry<ItemStack> findStack = InventoryUtils.findBestPlayerItem(this::scoreItem, true, false);
                var entry = InventoryUtils.findPlayerItem(ItemStack::isEmpty, true, true);
                if (findStack != null && entry != null) {
                    Runnable callback1 = InvExtra.INSTANCE.swapItemToHand(entry.index(), false, GhostHandMode.INV_SWAP);
                    Interact.INSTANCE.interactBlock(placeSupporting.val());
                    DisablerManager.INSTANCE.setACPlaceQueue(false);
                    // refresh backpack
                    findStack = InventoryUtils.findBestPlayerItem(this::scoreItem, true, false);
                    Runnable callback2 =
                            InvExtra.INSTANCE.swapItemToHand(findStack.index(), false, GhostHandMode.INV_SWAP);
                    DisablerManager.INSTANCE.setACPlaceQueue(true);
                    DisablerManager.INSTANCE.flushACPlaceQueue();
                    callback2.run();
                    PlayerInteractionAccess.of(mc.interactionManager).sendStartBreakPacket(pos);
                    callback1.run();
                }
            }
        }
    }

    BlockPos airMinePos;

    private void markAirMine() {
        if (checkNull()) return;
        if (mc.crosshairTarget != null
                && mc.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            airMinePos = hit.getBlockPos().offset(hit.getSide());
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

    private void onPreInputEvent(Event<Void> event) {
        if (checkNull()) {
            return;
        }
        tickAutoDick();
        tickAirMine();
        tickAutoWither();
        tickAutoNameWither();
        tickAutoGolem();
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
}
