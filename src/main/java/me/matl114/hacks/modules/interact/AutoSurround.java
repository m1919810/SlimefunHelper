package me.matl114.hacks.modules.interact;

import java.util.*;
import java.util.stream.Collectors;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.*;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.ac.DisablerManager;
import me.matl114.hacks.modules.combat.Attack;
import me.matl114.hacks.modules.combat.TargetSelector;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.mine.MiningProgressManager;
import me.matl114.hacks.modules.mine.PacketMine;
import me.matl114.hacks.modules.move.PlayerInputManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.config.*;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.*;
import me.matl114.managers.input.MultiKeyBind;
import me.matl114.utils.*;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.utils.entity.PlayerInputUtils;
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

public class AutoSurround extends BaseModule implements LegalMovementManager.MovementModifier {
    static LegalMovementManager.DelegateMovementModifier instance;

    public AutoSurround() {
        super("AutoSurround");
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

    public final FlagRef airplace = flagBuilder(autoSurround.add("air-place")).build();

    public final NBTRef<OptionalPrimitive<Double>> onlyPlayerNear = builder(
                    autoSurround.add("only-player-near"), OptionalPrimitive.DOUBLE_TYPE)
            .defaultValue(new OptionalPrimitive<>(false, NBTTypes.DOUBLE_TYPE, 10.0D))
            .build();

    public final FlagRef placeUpper = flagBuilder(autoSurround.add("upper")).build();

    public final FlagRef autoAttackCrystals =
            flagBuilder(autoSurround.add("auto-attack-crystal")).build();

    public final FlagRef antiPacketMine =
            flagBuilder(autoSurround.add("anti-packet-mine")).build();

    public final FlagRef onlyGround =
            flagBuilder(autoSurround.add("only-ground")).build();

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

    public final FlagRef swingHand = builder(autoSurround.add("swing-hand"), Boolean.class)
            .defaultValue(true)
            .build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getPreHandleInputEvents(), this::onInput);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onModulePreset);
        registerListener(PacketMine.getPrePacketMine(), this::onPrePacketMine);
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
                } else {
                    triggerCenterFix = false;
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

    public void onPrePacketMine(Event<PacketMine.Pre> eventPre) {
        if (enable.get() && !eventPre.isCancelled()) {
            BlockPos pos = eventPre.getArgs(0);
            if (getTargetingPos().contains(pos)) {
                eventPre.cancel();
            }
        }
    }

    int[] dx = {0, 0, -1, 1};

    int[] dz = {-1, 1, 0, 0};
    Direction[] dd = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN};
    BlockPos lastSurround;

    public Set<BlockPos> getTargetingPos() {
        BlockPos vcPos = PlayerStateManager.INSTANCE.lastVelocityAffectingPos;
        lastSurround = vcPos;
        Box playerBox = mc.player.getBoundingBox();
        playerBox = playerBox.withMaxY(Math.max(
                playerBox.minY
                        + InteractExtra.INSTANCE.getPotentialEyeHeights().max().orElse(0),
                playerBox.maxY));
        var occupiedPoses = new LinkedHashSet<>(MathUtils.getOccupiedBlockPositions(playerBox));
        var occupiedBasePoses = new LinkedHashSet<BlockPos>();
        for (BlockPos occupiedPos : occupiedPoses) {
            occupiedBasePoses.add(new BlockPos(occupiedPos.getX(), vcPos.getY(), occupiedPos.getZ()));
        }
        int minY = ((int) playerBox.minY) - 1;
        int maxY = ((int) playerBox.maxY) + 1;
        Set<BlockPos> result = new LinkedHashSet<>();
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
                    result.add(test);
                }
            }
        }
        return result;
    }

    private boolean canCubePlace(ClientPlayerEntity player, BlockPos pos, Set<EndCrystalEntity> pendingRemove) {
        BlockState state = Blocks.OBSIDIAN.getDefaultState();
        VoxelShape shape = state.getCollisionShape(mc.world, pos, ShapeContext.of(mc.player))
                .offset(pos);

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
        if (antiPacketMine.get()) {
            bbs = MiningProgressManager.INSTANCE.getBreakingMap().values().stream()
                    .map(MiningProgressManager.BlockBreakTracker::getBlockPos)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }
        Set<BlockPos> pendingMine = new HashSet<>();
        for (var test : resultPoses) {
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
                                            && !Attack.INSTANCE.attackEntity(endCrystalEntity)) {
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
                            mul = Math.min(mul, supply.val().getCount());
                            offhandOk |= supply.index() == 40;
                            invCallback = offhandOk
                                    ? InvExtra.INSTANCE.swapInventoryIndexToOffhand(supply.index())
                                    : InvExtra.INSTANCE.swapInventoryIndexToHand(supply.index());
                        }
                        InteractionTasks.handlePlaceMode(
                                mode.get(),
                                hitResult.val(),
                                offhandOk ? Hand.OFF_HAND : Hand.MAIN_HAND,
                                swingHand.get());
                        placeCnt += 1;
                        if (placeCnt >= mul) {
                            break;
                        }
                    }
                }
            } else if (antiPacketMine.get() && bbs.contains(test)) {
                pendingMine.add(test);
            }
        }
        if (placeCnt < mul) {
            for (var test : pendingMine) {
                BlockHitResult selfHitResult = RaycastUtils.createHitResult(test, mc.player.getEyePos());
                if (placeCnt == 0) {
                    var supply = supplyBlocks();
                    if (supply == null) {
                        break;
                    }
                    mul = Math.min(mul, supply.val().getCount());
                    offhandOk |= supply.index() == 40;
                    invCallback = offhandOk
                            ? InvExtra.INSTANCE.swapInventoryIndexToOffhand(supply.index())
                            : InvExtra.INSTANCE.swapInventoryIndexToHand(supply.index());
                }
                InteractionTasks.handlePlaceMode(
                        mode.get(), selfHitResult, offhandOk ? Hand.OFF_HAND : Hand.MAIN_HAND, swingHand.get());
                placeCnt += 1;
                if (placeCnt >= mul) {
                    break;
                }
            }
        }
        if (invCallback != null) {
            invCallback.run();
        }
        return placeCnt > 0;
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
                    PlayerStateManager.setPlayerYawSafe(
                            mc.player, EntityUtils.rotationToYaw(lookHorizontal.normalize()));
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
        this.airplace.set(!event.context.getValue().hasAC());
    }
}
