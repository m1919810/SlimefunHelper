package me.matl114.hacks.modules.interact;

import java.util.*;
import java.util.stream.Collectors;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.BlockBreak;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.*;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.combat.CombatManager;
import me.matl114.hacks.modules.combat.TargetSelector;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.mine.MiningProgressManager;
import me.matl114.hacks.modules.mine.PacketMine;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.modules.move.RotationManager;
import me.matl114.hacks.modules.task.DisablerManager;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.InteractUtils;
import me.matl114.hacks.utils.PlayerInputUtils;
import me.matl114.hacks.utils.config.*;
import me.matl114.hacks.utils.enums.GhostHandMode;
import me.matl114.hacks.utils.enums.LegalInteractMode;
import me.matl114.hacks.utils.tasks.StateExecutor;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.IndexEntry;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.decoration.EndCrystalEntity;
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
import net.minecraft.util.shape.VoxelShape;

public class AutoSurround extends BaseModule {
    public static AutoSurround INSTANCE;

    public AutoSurround() {
        super("AutoSurround");
        INSTANCE = this;
        bindFlag(enable);
    }

    public final ModulePath autoSurround = makePath(Configs.INTERACT_CONFIG, "place-utils.auto-surround");

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

    public final EnumRef<LegalInteractMode> mode = builder(autoSurround.add("mode"), LegalInteractMode.class)
            .defaultValue(LegalInteractMode.DELAY_MOVEMENT)
            .build();

    public final FlagRef airplace = flagBuilder(autoSurround.add("air-place")).build();

    public final NBTRef<OptionalPrimitive<Double>> onlyPlayerNear = builder(
                    autoSurround.add("only-player-near"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 10.0D))
            .build();

    public final NBTRef<OptionalPrimitive<Integer>> scaffold = builder(
                    autoSurround.add("lower"), OptionalPrimitive.INT_TYPE)
            .defaultValue(new OptionalPrimitive<>(true, NBTTypes.INT_TYPE, 1))
            .build();

    public final FlagRef placeUpper = flagBuilder(autoSurround.add("upper")).build();

    public final FlagRef placeLower =
            flagBuilder(autoSurround.add("place-lower")).build();

    public final FlagRef onlyGround =
            flagBuilder(autoSurround.add("only-ground")).build();

    public final FlagRef autoAttackCrystals =
            flagBuilder(autoSurround.add("auto-attack-crystal")).build();

    public final FlagRef antiPacketMine =
            flagBuilder(autoSurround.add("anti-packet-mine")).build();

    public final FlagRef autoFillMinePos =
            flagBuilder(autoSurround.add("auto-fill-mine-pos")).build();

    public final FlagRef autoCenter =
            flagBuilder(autoSurround.add("auto-center")).build();
    public final FlagRef autoSneak = flagBuilder(autoSurround.add("auto-sneak")).build();

    public final FlagRef useWhiteList =
            flagBuilder(autoSurround.add("use-white-list")).build();

    public final NBTRef<EntrySet<Item>> whiteList = builder(autoSurround.add("white-list"), EntrySet.<Item>parameter())
            .defaultValue(new EntrySet<>(Registries.ITEM, List.of(Items.OBSIDIAN)))
            .build();

    public final FlagRef onlyBlastResistance = builder(autoSurround.add("only-blast-resistance"), Boolean.class)
            .defaultValue(true)
            .build();

    public final FlagRef eatingAbort = builder(autoSurround.add("using-item-abort"), Boolean.class)
            .defaultValue(false)
            .build();

    public final EnumRef<GhostHandMode> ghostHand = builder(autoSurround.add("ghost-hand-mode"), GhostHandMode.class)
            .defaultValue(GhostHandMode.INV_SWAP)
            .build();

    public final FlagRef swingHand = builder(autoSurround.add("swing-hand"), Boolean.class)
            .defaultValue(true)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onInput);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
        registerListener(PacketMine.getPacketMineAction().getChannel(BlockBreak.Stage.PRE), this::onPrePacketMine);
    }

    @Override
    public void onEnableModule() {
        super.onEnableModule();
    }

    @Override
    public void onDisableModule() {
        super.onDisableModule();
    }

    int delayTicks;
    boolean needSneak = false;
    StateExecutor needPlaceState = new StateExecutor();
    BlockPos lastCenter;

    public void onInput() {
        boolean triggerCenterFix = lastCenter != null;
        if (enable.get()) {
            boolean bl = mc.player.isSneaking();
            if (++delayTicks >= delay.get()) {
                if (!eatingAbort.get() || !mc.player.isUsingItem()) {
                    if (checkSurround()) {
                        if (autoCenter.get() && mc.player.getPose() != EntityPose.SWIMMING) {
                            triggerCenterFix = true;
                        }
                        delayTicks = 0;
                        needPlaceState.state(true);
                    } else {
                        needPlaceState.state(false);
                    }
                } else {
                    needPlaceState.state(false);
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
        if (enable.get()
                && autoCenter.get()
                && triggerCenterFix
                && mc.player.isOnGround()
                && !mc.player.isFallFlying()) {
            if (lastCenter == null || !MathUtils.isInBox(lastCenter.toCenterPos(), mc.player.getPos(), 0.75)) {
                lastCenter = lastSurround == null ? PlayerStateManager.INSTANCE.lastVelocityAffectingPos : lastSurround;
            }
            var blockPos = lastCenter;
            boolean fixed = mc.player.getX() - blockPos.getX() <= 0.7
                    && mc.player.getX() - blockPos.getX() >= 0.3
                    && mc.player.getZ() - blockPos.getZ() <= 0.7
                    && mc.player.getZ() - blockPos.getZ() >= 0.3;
            if (!fixed) {
                PlayerInputUtils.Input inputUtils = PlayerInputUtils.of(mc.options);
                if (!inputUtils.hasMovement()) {
                    Vec3d lookHorizontal = blockPos.toCenterPos().subtract(mc.player.getPos());
                    float yaw = EntityUtils.rotationToYaw(lookHorizontal);
                    RotationManager.INSTANCE.push(RotationManager.ofDirection(Optional.empty(), Optional.of(yaw))
                            .preTick((rotation, player, matchRaycast) -> {
                                PlayerInputUtils.Input movement = PlayerInputUtils.tryCorrectMovementInput(
                                        new PlayerInputUtils.Input(true, false, false, false), yaw, player.getYaw());
                                PlayerInputManager.INSTANCE.addInputModifier(
                                        PlayerInputManager.Modifier.empty(0)
                                                .forward(movement.forward())
                                                .backward(movement.backward())
                                                .left(movement.left())
                                                .right(movement.right()),
                                        0);
                            })
                            .applyMoveFix(false)
                            .priority(0));
                }
            } else {
                lastCenter = null;
            }
        }
    }

    public void onPrePacketMine(Event<BlockBreak> eventPre) {
        if (enable.get()
                && !eventPre.isCancelled()
                && eventPre.canCancel()
                && eventPre.context().stage() == BlockBreak.Stage.PRE) {
            BlockPos pos = eventPre.context().blockPos();
            if (getTargetingPos().contains(pos)) {
                eventPre.cancel();
            }
        }
    }

    Direction[] dd = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN};
    BlockPos lastSurround;

    public Set<BlockPos> getTargetingPos() {
        BlockPos vcPos = PlayerStateManager.INSTANCE.lastVelocityAffectingPos;
        Box playerBox = mc.player.getBoundingBox();
        int minY = (int) Math.floor(playerBox.minY);
        int maxY = (int) Math.ceil(playerBox.maxY);
        playerBox = playerBox.withMaxY(Math.max(
                playerBox.minY
                        + InteractExtra.INSTANCE.getPotentialEyeHeights().max().orElse(0),
                playerBox.maxY));
        var occupiedPoses = new LinkedHashSet<>(MathUtils.getOccupiedBlockPositions(playerBox));
        var occupiedBasePoses = new LinkedHashSet<BlockPos>();
        for (BlockPos occupiedPos : occupiedPoses) {
            occupiedBasePoses.add(new BlockPos(occupiedPos.getX(), vcPos.getY(), occupiedPos.getZ()));
        }
        Set<BlockPos> result = new LinkedHashSet<>();

        var directionTestPoses = new LinkedHashSet<BlockPos>();
        if (placeLower.get()) {
            for (var re : occupiedPoses) {
                BlockPos test = re.withY(minY - 1);
                if (occupiedPoses.contains(test)) {
                    continue;
                }
                result.add(test);
            }
        }
        for (int direction = 0; direction < 4; ++direction) {
            Direction dir = dd[direction];
            for (BlockPos occupiedPos : occupiedBasePoses) {
                BlockPos expandedPos = occupiedPos.offset(dir);
                if (!occupiedBasePoses.contains(expandedPos)) {
                    directionTestPoses.add(expandedPos);
                }
            }
        }
        // do not place under me
        for (int y = minY; y < maxY; ++y) {
            for (BlockPos testPos : directionTestPoses) {
                BlockPos test = testPos.withY(y);
                if (occupiedPoses.contains(test)) {
                    continue;
                }
                result.add(test);
            }
        }
        if (placeUpper.get()) {
            for (var re : occupiedPoses) {
                BlockPos test = re.withY(maxY);
                if (occupiedPoses.contains(test)) {
                    continue;
                }
                result.add(test);
            }
        }

        Set<BlockPos> scaffoldPoses = new LinkedHashSet<>();
        if (scaffold.get().isPresent()) {
            for (var re : directionTestPoses) {
                BlockState stateUp = mc.world.getBlockState(re.withY(minY));
                if (stateUp.isAir() || stateUp.isLiquid()) {
                    for (var i = 1; i <= scaffold.get().getValue(); ++i) {
                        BlockPos test = re.withY(minY - i);
                        BlockState currentState = mc.world.getBlockState(test);
                        if (currentState.isAir() || currentState.isLiquid()) {
                            if (occupiedPoses.contains(test)) {
                                continue;
                            }
                            scaffoldPoses.add(test);
                        } else {
                            break;
                        }
                    }
                }
            }
            for (BlockPos occupiedPos : directionTestPoses) {
                BlockState stateUp = mc.world.getBlockState(occupiedPos.withY(maxY - 1));
                if (stateUp.isAir() || stateUp.isLiquid()) {
                    for (var i = 0; i < scaffold.get().getValue(); ++i) {
                        BlockPos upperPos = occupiedPos.withY(maxY + i);
                        BlockState currentState = mc.world.getBlockState(upperPos);
                        if (currentState.isAir() || currentState.isLiquid()) {
                            if (occupiedPoses.contains(upperPos)) {
                                continue;
                            }
                            scaffoldPoses.add(upperPos);
                        } else {
                            break;
                        }
                    }
                }
            }
        }
        result.addAll(scaffoldPoses);

        return result;
    }

    private boolean canCubePlace(ClientPlayerEntity player, BlockPos pos, Set<EndCrystalEntity> pendingRemove) {
        BlockState state = Blocks.OBSIDIAN.getDefaultState();
        VoxelShape shape = state.getCollisionShape(mc.world, pos, ShapeContext.of(mc.player))
                .offset(pos.getX(), pos.getY(), pos.getZ());

        return !CollisionUtil.hasAnyIntersects(
                mc.world, (entity) -> entity instanceof EndCrystalEntity end && pendingRemove.contains(end), shape);
    }

    public boolean checkSurround() {
        if (onlyGround.get() && !mc.player.isOnGround() && !CollisionUtil.isEntitySupported(mc.player, 1.5D)) {
            return false;
        }
        if (onlyPlayerNear.get().isPresent()) {
            var emeries = TargetSelector.INSTANCE.getAttackableEntities(
                    onlyPlayerNear.get().getValue());
            if (emeries.isEmpty()) {
                return false;
            }
        }
        lastSurround = PlayerStateManager.INSTANCE.lastVelocityAffectingPos;

        boolean legal = mode.get().isLegal();

        int mul = ((DisablerManager.INSTANCE.isMultiRotPlaceCheckDisabled(
                        mode.get().canMultiRotPlace())))
                ? multiply.get()
                : 1;

        int placeCnt = 0;
        Runnable invCallback = null;
        Set<EndCrystalEntity> pendingRemoval = new HashSet<>();
        boolean offhandOk = offhand.get();
        var resultPoses = getTargetingPos();
        Set<BlockPos> bbs = Set.of();
        if (antiPacketMine.get() || autoFillMinePos.get()) {
            bbs = MiningProgressManager.INSTANCE.getBreakingMap().values().stream()
                    .filter(s -> TargetSelector.INSTANCE.canAttack(s.player))
                    .filter(MiningProgressManager.BlockBreakTracker::canMine)
                    .filter(s -> s.predictBreakingProgress() > 0.5)
                    .map(MiningProgressManager.BlockBreakTracker::getBlockPos)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }
        Set<BlockPos> fillMinePoses = new LinkedHashSet<>();
        if (autoFillMinePos.get()) {
            BlockPos currentMinePos =
                    PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos();
            for (BlockPos minePos : bbs) {
                for (int i = 0; i < 4; i++) {
                    BlockPos neighbor = minePos.offset(dd[i]);
                    if (Objects.equals(neighbor, currentMinePos)) {
                        continue;
                    }
                    BlockState neighbourState = mc.world.getBlockState(neighbor);
                    if (neighbourState.isAir() || neighbourState.isLiquid() || neighbourState.isReplaceable()) {
                        fillMinePoses.add(neighbor);
                    }
                }
            }
        }
        Set<BlockPos> pendingMine = new HashSet<>();
        Set<BlockPos> orderedResultPoses = new LinkedHashSet<>(resultPoses.size() + fillMinePoses.size());
        orderedResultPoses.addAll(resultPoses);
        orderedResultPoses.addAll(fillMinePoses);
        for (var test : orderedResultPoses) {
            BlockState state = mc.world.getBlockState(test);
            if ((state.isAir() || state.isReplaceable())) {
                var hitResult = InteractionTasks.getPlaceSupportingResult(test, airplace.get(), !legal);
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
                boolean canPlace = InteractUtils.canInteractAndPlace(mc.player, hitResult);
                if (canPlace) {
                    if (autoAttackCrystals.get()) {
                        mc.world
                                .getOtherEntities(
                                        null, MathUtils.getBlockBox(test), (e) -> e instanceof EndCrystalEntity)
                                .forEach(endCrystalEntity -> {
                                    if (endCrystalEntity instanceof EndCrystalEntity endCrystal
                                            && CombatManager.INSTANCE.attackCrystal(endCrystalEntity)) {
                                        pendingRemoval.add(endCrystal);
                                    }
                                });
                    }
                    if (canCubePlace(mc.player, test, pendingRemoval)) {
                        if (placeCnt == 0) {
                            var supply = supplyBlocks();
                            if (supply == null) {
                                break;
                            }
                            if (!InteractionTasks.canHandleInteract(mode.get(), hitResult.val())) {
                                break;
                            }
                            mul = Math.min(mul, supply.val().getCount());
                            offhandOk |= supply.index() == 40;
                            invCallback = InvExtra.INSTANCE.swapItemToHand(supply.index(), offhandOk, ghostHand.get());
                        }
                        boolean completed = InteractionTasks.handlePlaceMode(
                                mode.get(),
                                hitResult.val(),
                                offhandOk ? Hand.OFF_HAND : Hand.MAIN_HAND,
                                swingHand.get());
                        placeCnt += 1;
                        if (!completed || placeCnt >= mul) {
                            break;
                        }
                    }
                }
            } else if (antiPacketMine.get()
                    && bbs.contains(test)
                    && mc.world
                            .getOtherEntities(
                                    null, new Box(test), predicate -> !(predicate instanceof EndCrystalEntity))
                            .isEmpty()) {
                pendingMine.add(test);
            }
        }
        if (placeCnt < mul && !mc.player.isUsingItem()) {
            for (var test : pendingMine) {
                BlockHitResult selfHitResult = InteractionTasks.createHitResult(test, mc.player.getPos());
                if (placeCnt == 0) {
                    var supply = supplyBlocks();
                    if (supply == null) {
                        break;
                    }
                    if (!InteractionTasks.canHandleInteract(mode.get(), selfHitResult)) {
                        break;
                    }
                    mul = Math.min(mul, supply.val().getCount());
                    offhandOk |= supply.index() == 40;
                    invCallback = InvExtra.INSTANCE.swapItemToHand(supply.index(), offhandOk, ghostHand.get());
                }
                boolean completed = InteractionTasks.handlePlaceMode(
                        mode.get(), selfHitResult, offhandOk ? Hand.OFF_HAND : Hand.MAIN_HAND, swingHand.get());
                placeCnt += 1;
                if (!completed || placeCnt >= mul) {
                    break;
                }
            }
        }
        if (invCallback != null) {
            InteractionTasks.finishInteractGhostHand(mode.get(), invCallback);
        }
        return placeCnt > 0 || !pendingRemoval.isEmpty();
    }

    public IndexEntry<ItemStack> supplyBlocks() {
        return InventoryUtils.findBestPlayerItem(
                item -> {
                    if (item.getItem() instanceof BlockItem blockItem) {
                        if (useWhiteList.get()) {
                            if (!whiteList.get().test(blockItem)) {
                                return null;
                            }
                        }
                        if (onlyBlastResistance.get() && blockItem.getBlock().getBlastResistance() < 600) {
                            return null;
                        }
                        return (double) (blockItem.getBlock().getBlastResistance())
                                + ((blockItem == Items.OBSIDIAN) ? 1E8 : 0)
                                + (blockItem.getBlock() instanceof BlockWithEntity ? -1E8 : 0);
                    }
                    return null;
                },
                ghostHand.get().getSearchSize(offhand.get()),
                false,
                false);
    }

    public void onModulePreset(Event<EventContainer<ModulePreset>> event) {
        ModulePreset preset = event.context.getValue();
        ghostHand.set(preset.getGhostHandMode(ghostHand.get()));
        if (preset.isAirplacePreset()) airplace.set(preset.getAirplace(airplace.get()));
        if (preset.isSwingPlacePreset()) swingHand.set(preset.getSwingPlace(swingHand.get()));
        if (preset.isMultitaskPreset()) eatingAbort.set(preset.getEatingAbort(eatingAbort.get()));
        if (!preset.isOptionPreset()) {
            mode.set(LegalInteractMode.getFromPreset(preset));
            airplace.set(!preset.hasAC());
        }
    }
}
