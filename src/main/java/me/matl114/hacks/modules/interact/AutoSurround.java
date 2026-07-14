package me.matl114.hacks.modules.interact;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.*;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.ac.DisablerManager;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.IntRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.entity.LegalMovementManager;
import me.matl114.utils.entity.PlayerInputUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AutoSurround extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;

    public AutoSurround() {
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
        bindFlag(enable);
    }

    public final ModulePath autoSurround = makePath(Configs.INTERACT_CONFIG, "place-utils.auto-surround");

    @Override
    public int priority() {
        return PRIORITY_MONITOR;
    }

    public final FlagRef enable = flagBuilder(autoSurround.addEnable()).build();

    public final KeyBindRef hotkey = moduleEntry(autoSurround.addHotkey(), new MultiKeyBind(), autoSurround.addEnable())
            .build();

    public final FlagRef offhand =
            flagBuilder(autoSurround.add("offhand-enable")).build();

    public final IntRef delay = builder(autoSurround.add("delay"), IntRef.TYPE)
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final IntRef multiply = builder(autoSurround.add("multiply"), IntRef.TYPE)
            .defaultValue(1)
            .validator(Configs.INT_POSITIVE)
            .build();

    public final EnumRef<Configs.LegalInteractMode> mode = builder(
                    autoSurround.add("mode"), Configs.LegalInteractMode.class)
            .defaultValue(Configs.LegalInteractMode.DELAY_MOVEMENT)
            .build();

    public final FlagRef placeUpper = flagBuilder(autoSurround.add("upper")).build();

    public final FlagRef autoAttackCrystals =
            flagBuilder(autoSurround.add("auto-attack-crystal")).build();

    public final FlagRef onlyGround =
            flagBuilder(autoSurround.add("only-ground")).build();

    public final FlagRef autoCenter =
            flagBuilder(autoSurround.add("auto-center")).build();
    public final FlagRef autoSneak = flagBuilder(autoSurround.add("auto-sneak")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onInput);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
    }

    @Override
    public void onEnableModule() {
        super.onEnableModule();
        triggerCenterFix = autoCenter.get() && mc.player != null && mc.player.getPose() != EntityPose.SWIMMING;
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
        triggerCenterFix = false;
    }

    int delayTicks;
    boolean needSneak = false;

    public void onInput(Event<Void> inputEvent) {
        if (enable.get()) {
            boolean bl = mc.player.isSneaking();
            if (++delayTicks >= delay.get()) {
                if (checkSurround()) {
                    if (autoCenter.get() && mc.player.getPose() != EntityPose.SWIMMING) {
                        triggerCenterFix = true;
                    }
                    delayTicks = 0;
                }
            }
            if (needSneak) {
                needSneak = false;
                if (autoSneak.get()) {
                    if (ViaFabricPlusHooks.isSupportInstaSneak()) {
                        if (mc.player.isSneaking() != bl) {
                            PlayerInputUtils.of(mc.player)
                                    .sneak(bl)
                                    .sendPlayerSneakUpdatePacket()
                                    .applyInput(mc.player);
                        }
                    } else {
                        PlayerInputManager.INSTANCE.addSneakModifier(0, true, Math.max(delay.get() - 1, 0), 1);
                    }
                }
            }
        }
    }

    int[] dx = {0, 0, -1, 1};

    int[] dz = {-1, 1, 0, 0};
    Direction[] dd = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN};
    BlockPos lastSurround;

    public boolean checkSurround() {
        BlockPos vcPos = PlayerStateManager.INSTANCE.lastVelocityAffectingPos;
        lastSurround = vcPos;
        if (onlyGround.get() && !mc.player.isOnGround() && !CollisionUtil.isEntitySupported(mc.player, 1.5D)) {
            return false;
        }
        boolean legal = mode.get().isLegal();
        Box playerBox = mc.player.getBoundingBox();
        int mul = (mode.get().canMultiRotPlace() || (DisablerManager.INSTANCE.isMultiRotPlaceCheckDisabled()))
                ? multiply.get()
                : 1;
        int minY = ((int) playerBox.getMin(Direction.Axis.Y)) - 1;
        int maxY = ((int) playerBox.getMax(Direction.Axis.Y)) + 1;
        var occupiedPoses = new LinkedHashSet<>(MathUtils.getOccupiedBlockPositions(playerBox));
        var occupiedBasePoses = new LinkedHashSet<BlockPos>();
        for (BlockPos occupiedPos : occupiedPoses) {
            occupiedBasePoses.add(new BlockPos(occupiedPos.getX(), vcPos.getY(), occupiedPos.getZ()));
        }
        int placeCnt = 0;
        Runnable invCallback = null;
        List<Entity> entities = new ArrayList<>();
        boolean offhandOk = offhand.get();
        place:
        for (int direction = 0; direction < 4 + (placeUpper.get() ? 1 : 0); ++direction) {
            Direction dir = dd[direction];
            var directionTestPoses = new LinkedHashSet<BlockPos>();
            for (BlockPos occupiedPos : occupiedBasePoses) {
                BlockPos expandedPos = occupiedPos.offset(dir);
                if (!occupiedBasePoses.contains(expandedPos)) {
                    directionTestPoses.add(expandedPos);
                }
            }
            // do not place under me
            int coordYMax = dir == Direction.DOWN ? maxY - 2 : maxY;
            for (BlockPos testPos : directionTestPoses) {
                for (int y = minY; y <= coordYMax; ++y) {
                    BlockPos test = testPos.withY(y);
                    if (occupiedPoses.contains(test)) {
                        continue;
                    }
                    BlockState state = mc.world.getBlockState(test);
                    if ((state.isAir() || state.isReplaceable())) {
                        var hitResult = InteractionTasks.getPlaceSupportingResult(test, !legal, !legal);
                        if (hitResult != null && hitResult.flag()) {
                            if (!needSneak
                                    && autoSneak.get()
                                    && ViaFabricPlusHooks.isSupportInstaSneak()
                                    && !mc.player.isSneaking()) {
                                PlayerInputUtils.of(mc.player)
                                        .sneak(true)
                                        .sendPlayerSneakUpdatePacket()
                                        .applyInput(mc.player);
                            }
                            needSneak = true;
                        }
                        boolean canPlace = hitResult != null && InteractUtils.canInteractAndPlace(mc.player, hitResult);
                        if (canPlace) {
                            if (InteractUtils.canCubePlace(mc.player, test)) {
                                if (placeCnt == 0) {
                                    var supply = supplyBlocks();
                                    if (supply == null) {
                                        break place;
                                    }
                                    mul = Math.min(mul, supply.val().getCount());
                                    offhandOk |= supply.index() == 40;
                                    invCallback = offhandOk
                                            ? InvExtra.INSTANCE.swapInventoryIndexToOffhand(supply.index())
                                            : InvExtra.INSTANCE.swapInventoryIndexToHand(supply.index());
                                } else {
                                    InteractionTasks.flushACPlaceQueue();
                                }
                                InteractionTasks.handlePlaceMode(
                                        mode.get(), hitResult.val(), offhandOk ? Hand.OFF_HAND : Hand.MAIN_HAND);
                                placeCnt += 1;
                                if (placeCnt >= mul) {
                                    break place;
                                }
                            } else {
                                // can not place
                                entities.addAll(mc.world.getOtherEntities(
                                        null, MathUtils.getBlockBox(test), (e) -> e instanceof EndCrystalEntity));
                            }
                        }
                    }
                }
            }
        }
        if (invCallback != null) {
            invCallback.run();
        }
        if (placeCnt == 0 && !entities.isEmpty() && autoAttackCrystals.get()) {
            for (var re : entities) {
                CombatTasks.getAttack().attackEntity(re);
            }
        }
        return placeCnt > 0;
    }

    public IndexEntry<ItemStack> supplyBlocks() {
        return InventoryUtils.findBestPlayerItem(
                item -> {
                    if (item.getItem() instanceof BlockItem blockItem) {
                        return (double) (blockItem.getBlock().getBlastResistance())
                                + ((blockItem == Items.OBSIDIAN) ? 1E8 : 0)
                                + (blockItem.getBlock() instanceof BlockWithEntity ? -1E8 : 0);
                    }
                    return null;
                },
                true,
                false);
    }

    boolean triggerCenterFix = false;
    boolean rotateSuccess = false;
    BlockPos lastCenter;

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {

        if (triggerCenterFix && mc.player.isOnGround()) {
            if (lastCenter == null || !MathUtils.isInBox(lastCenter.toCenterPos(), mc.player.getPos(), 1.0)) {
                lastCenter = lastSurround == null ? PlayerStateManager.INSTANCE.lastVelocityAffectingPos : lastSurround;
            }
            var blockPos = lastCenter;
            boolean fixed = mc.player.getX() - blockPos.getX() - 0.5 <= 0.2
                    && mc.player.getX() - blockPos.getX() - 0.5 >= -0.2
                    && mc.player.getZ() - blockPos.getZ() - 0.5 <= 0.2
                    && mc.player.getZ() - 0.5 - blockPos.getZ() >= -0.2;
            if (!fixed) {
                PlayerInputUtils.Input inputUtils = PlayerInputUtils.of(mc.options);
                if (!inputUtils.hasMovement() && !movementManagerEvent.context.hasImportantRotation()) {
                    rotateSuccess = true;
                    Vec3d lookHorizontal = blockPos.toCenterPos().subtract(mc.player.getPos());
                    EntityUtils.setEntityYawSafe(mc.player, EntityUtils.rotationToYaw(lookHorizontal.normalize()));
                    movementManagerEvent.context.markForResetRot();
                }
            } else {
                triggerCenterFix = false;
                lastCenter = null;
            }
        }
    }
    // todo: try check block position, sneak-related
    @Override
    public void applyAfterInputTick(Event<LegalMovementManager> movementManagerEvent) {
        if (triggerCenterFix && mc.player.isOnGround() && rotateSuccess) {
            rotateSuccess = false;
            var input = PlayerInputUtils.of(mc.player);
            if (!input.hasWASDMovement()) {
                input.forward(true).applyInput(mc.player);
            }
        }
    }

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        return true;
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        this.mode.set(Configs.LegalInteractMode.getFromPreset(event.context.getValue()));
    }
}
