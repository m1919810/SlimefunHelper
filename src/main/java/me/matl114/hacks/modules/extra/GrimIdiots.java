package me.matl114.hacks.modules.extra;

import java.util.List;
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
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.ConfigEnum;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.InteractUtils;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

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

    private void onPreInputEvent(Event<Void> event) {
        if (checkNull()) {
            return;
        }
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
