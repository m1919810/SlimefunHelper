package me.matl114.hacks;

import com.google.common.util.concurrent.Runnables;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import lombok.Getter;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.catchers.PacketCatcherImpl;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.ac.DisablerManager;
import me.matl114.hacks.modules.interact.*;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.entity.LegalMovementManager;
import me.matl114.managers.Configs;
import me.matl114.managers.Tasks;
import me.matl114.utils.*;
import me.matl114.utils.collections.FlagEntry;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.apache.commons.lang3.mutable.MutableObject;

public class InteractionTasks {
    public static void init() {}

    private static MinecraftClient mc = MinecraftClient.getInstance();
    //
    //    public static void placeBlock(int idx, BlockHitResult result){
    //
    //    }

    public static void interactBlock(Hand hand, BlockHitResult result, boolean swing) {
        Vec2f storePY = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
        // use true fucking rotation .
        mc.player.setPitch(PlayerStateManager.INSTANCE.lastPitch);
        mc.player.setYaw(PlayerStateManager.INSTANCE.lastYaw);
        ActionResult actionResult2 = mc.interactionManager.interactBlock(mc.player, hand, result);
        mc.player.setPitch(storePY.x);
        mc.player.setYaw(storePY.y);
        if (swing) {
            InteractUtils.swingHandIfSuccess(actionResult2, hand);
            return;
        }
    }

    public static void interactEntity(PlayerEntity player, Entity entity, Hand hand, boolean swing) {
        ActionResult result = mc.interactionManager.interactEntityAtLocation(
                mc.player, entity, RaycastUtils.createRealHitResult(entity, player.getEyePos()), hand);
        if (!result.isAccepted()) {
            result = mc.interactionManager.interactEntity(player, entity, hand);
        }
        if (swing) {
            InteractUtils.swingHandIfSuccess(result, hand);
        }
    }

    public static void addPostRotationCorrectTask(Vec3d look3d, Vec3d eyePos, Runnable callback) {
        //        RenderTasks.registerVirtualRenderTask(new RenderTasks.RenderTask(
        //            RenderTasks.DEBUG_TICK, new RenderTasks.BoxObject(look3d.add(-0.1, -0.1, -0.1), look3d.add(0.1,
        // 0.1, 0.1), Color.MAGENTA)));
        ClientPlayerAccess.of(mc.player)
                .getLegalMovementManager()
                .addMovementModifier(new LegalMovementManager.MovementModifier() {
                    @Override
                    public int priority() {
                        return PRIORITY_LOW;
                    }

                    @Override
                    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
                        ClientPlayerEntity player = movementManagerEvent.context.playerStatus.entity;

                        Vec2f rotation =
                                EntityUtils.rotationToPitchYaw(look3d.subtract(eyePos.add(mc.player.getVelocity()))
                                        .normalize());
                        movementManagerEvent.context.pushImportantRotation(true, true);
                        PlayerStateManager.setPlayerYawSafe(player, rotation.y);
                        EntityUtils.setEntityPitchSafe(player, rotation.x);
                        //                        py = rotation;
                        movementManagerEvent.context.tryMarkForMoveFix();
                        movementManagerEvent.context.markForResetRot();
                        // RenderTasks.drawBox(MathUtils.createBox(look3d, 0.2D), 300, Color.MAGENTA);
                    }

                    @Override
                    public boolean postModify(
                            Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
                        callback.run();
                        return false;
                    }
                });
    }

    public static void handlePlaceMode(Configs.LegalInteractMode mode, BlockHitResult result, Hand hand) {
        handlePlaceMode(mode, result, hand, true);
    }

    public static void handlePlaceMode(
            Configs.LegalInteractMode mode, BlockHitResult result, Hand hand, boolean swingHand) {
        Vec3d bestEyePos = InteractExtra.INSTANCE.getBestInteractEyePos(mc.player.getPos(), result.getBlockPos());
        switch (mode) {
            case USEITEM_PACKET -> {
                Vec2f rotation = EntityUtils.rotationToPitchYaw(
                        result.getBlockPos().toCenterPos().subtract(bestEyePos).normalize());
                mc.interactionManager.sendSequencedPacket(
                        mc.world, (i) -> new PlayerInteractItemC2SPacket(hand, i, rotation.y, rotation.x));
                InteractionTasks.interactBlock(hand, result, swingHand);
            }
            case DELAY_MOVEMENT -> {
                InteractionTasks.interactBlock(hand, result, swingHand);
                InteractionTasks.addPostRotationCorrectTask(
                        result.getBlockPos().toCenterPos(), bestEyePos, Runnables.doNothing());
            }
            case MOVEMENT_POST -> {
                MutableObject<PlayerInteractBlockC2SPacket> catcher = new MutableObject<>();
                Listener.addPrePacketCatcher(new PacketCatcherImpl<>(PlayerInteractBlockC2SPacket.class, (eve) -> {
                    if (eve.isCancelled()) return true;
                    catcher.setValue(eve.context);
                    eve.cancel();
                    return true;
                }));
                InteractionTasks.interactBlock(hand, result, swingHand);
                if (catcher.getValue() != null) {
                    var pkt = catcher.getValue();
                    InteractionTasks.addPostRotationCorrectTask(
                            result.getBlockPos().toCenterPos(), bestEyePos, () -> mc.getNetworkHandler()
                                    .sendPacket(pkt));
                }
            }
            case LEGACY_SLIENT_ROT -> {
                Vec2f rotation = EntityUtils.rotationToPitchYaw(
                        result.getBlockPos().toCenterPos().subtract(bestEyePos).normalize());
                LegacySnapRotManager.INSTANCE.snapAt(rotation.x, rotation.y, false);
                InteractionTasks.interactBlock(hand, result, swingHand);
            }
            case NONE -> {
                InteractionTasks.interactBlock(hand, result, swingHand);
            }
        }
    }

    public static void flushACPlaceQueue() {
        DisablerManager.INSTANCE.flushACPlaceQueue();
        // for flush places
        //        ACTasks.getDisablerManager().flushACPlaceQueue();
    }

    public static void handlePlaceModeMulti(
            Configs.LegalInteractMode mode,
            Vec3d targetCenter,
            List<Pair<BlockHitResult, Hand>> resultList,
            boolean swingHand) {
        switch (mode) {
            case USEITEM_PACKET -> {
                Vec2f rotation = EntityUtils.rotationToPitchYaw(
                        targetCenter.subtract(mc.player.getEyePos()).normalize());

                int selectedSlot = -1;
                for (Pair<BlockHitResult, Hand> pair : resultList) {
                    var hand = pair.getRight();
                    var result = pair.getLeft();
                    if (selectedSlot == -1) {
                        selectedSlot = InventoryUtils.getSelectedSlot();
                        mc.interactionManager.sendSequencedPacket(
                                mc.world,
                                (i) -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, i, rotation.y, rotation.x));
                    } else {
                        flushACPlaceQueue();
                    }
                    InteractionTasks.interactBlock(hand, result, swingHand);
                }
            }
            case DELAY_MOVEMENT -> {
                int selectedSlot = -1;
                for (Pair<BlockHitResult, Hand> pair : resultList) {
                    if (selectedSlot == -1) {
                        selectedSlot = InventoryUtils.getSelectedSlot();
                    } else {
                        // for flush places
                        flushACPlaceQueue();
                    }
                    var hand = pair.getRight();
                    var result = pair.getLeft();
                    InteractionTasks.interactBlock(hand, result, swingHand);
                }
                InteractionTasks.addPostRotationCorrectTask(targetCenter, mc.player.getEyePos(), Runnables.doNothing());
            }
            case LEGACY_SLIENT_ROT -> {
                int selectedSlot = -1;
                for (Pair<BlockHitResult, Hand> pair : resultList) {
                    if (selectedSlot == -1) {
                        selectedSlot = InventoryUtils.getSelectedSlot();
                    } else {
                        // for flush places
                        flushACPlaceQueue();
                    }
                    var hand = pair.getRight();
                    var result = pair.getLeft();
                    LegacySnapRotManager.INSTANCE.snapAt(
                            result.getBlockPos()
                                    .toCenterPos()
                                    .subtract(mc.player.getEyePos())
                                    .normalize(),
                            false);
                    InteractionTasks.interactBlock(hand, result, swingHand);
                }
            }
            case NONE -> {
                for (Pair<BlockHitResult, Hand> pair : resultList) {
                    var hand = pair.getRight();
                    var result = pair.getLeft();
                    InteractionTasks.interactBlock(hand, result, swingHand);
                }
            }
        }
    }

    public static FlagEntry<BlockHitResult> getPlaceSupportingResult(
            BlockPos blockPos, boolean enableAirPlace, boolean enablePositionPlace) {
        return getPlaceSupportingResult(
                mc.player.getEyePos(), blockPos, mc.player.getFacing(), enableAirPlace, enablePositionPlace);
    }

    public static FlagEntry<BlockHitResult> getPlaceSupportingResult(
            BlockPos blockPos, Direction preferredDirection, boolean enableAirPlace, boolean enablePositionPlace) {
        return getPlaceSupportingResult(
                mc.player.getEyePos(), blockPos, preferredDirection, enableAirPlace, enablePositionPlace);
    }

    public static FlagEntry<BlockHitResult> getPlaceSupportingResult(
            Vec3d predictEyePos, BlockPos blockPos, boolean enableAirPlace, boolean enablePositionPlace) {
        return getPlaceSupportingResult(
                predictEyePos, blockPos, mc.player.getFacing(), enableAirPlace, enablePositionPlace);
    }

    public static FlagEntry<BlockHitResult> getPlaceSupportingResult(
            Vec3d predictEyePos,
            BlockPos blockPos,
            Direction preferredDirection,
            boolean enableAirPlace,
            boolean enablePositionPlace) {
        Direction dir = preferredDirection;
        List<Direction> order = new ArrayList<>();
        order.add(dir);
        for (var direction : new Direction[] {
            Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
        }) {
            if (direction != dir) {
                order.add(direction);
            }
        }
        Vec3d centerPos = blockPos.toCenterPos();
        if (enableAirPlace) {
            if (order.isEmpty()) {
                return null;
            }
            Direction availableDirection = order.get(0);
            Vec3d plateCenter = centerPos.offset(availableDirection, 0.5);
            return new FlagEntry<>(
                    false, new BlockHitResult(plateCenter, availableDirection.getOpposite(), blockPos, false));
        } else {
            FlagEntry<BlockHitResult> result = null;
            for (var direction : order) {
                Vec3d plateCenter = centerPos.offset(direction, 0.5);
                Vec3d interactBlockCenter = centerPos.offset(direction, 1.0D);
                BlockPos targetPos = BlockPos.ofFloored(interactBlockCenter);
                BlockState interactState = mc.world.getBlockState(targetPos);
                if ((interactState.isAir() || interactState.isLiquid())) {
                    continue;
                }
                boolean mayInteract = InteractUtils.isInteractAcceptable(mc.world, mc.player, targetPos, interactState);
                if (Box.from(Vec3d.of(targetPos)).contains(predictEyePos)) {
                    // ?
                    var re = new FlagEntry<>(
                            mayInteract, new BlockHitResult(plateCenter, direction.getOpposite(), targetPos, true));
                    if (!re.flag()) {
                        return re;
                    } else if (result == null) {
                        result = re;
                    }
                } else {
                    Vec3d iSeeVect = predictEyePos.subtract(plateCenter);
                    Vec3d plateLLL = Vec3d.of(direction.getVector());
                    if (enablePositionPlace || iSeeVect.dotProduct(plateLLL) < 0) {
                        var re = new FlagEntry<>(
                                mayInteract,
                                new BlockHitResult(plateCenter, direction.getOpposite(), targetPos, false));
                        if (!re.flag()) {
                            return re;
                        } else if (result == null) {
                            result = re;
                        }
                    }
                }
            }
            return result;
        }
    }

    @Nonnull
    public static List<FlagEntry<BlockHitResult>> getAllPlaceSupportingResult(
            Vec3d predictEyePos,
            BlockPos blockPos,
            Direction preferredDirection,
            boolean enableAirPlace,
            boolean enablePositionPlace) {
        List<FlagEntry<BlockHitResult>> result = new ArrayList<>();
        Direction dir = preferredDirection;
        List<Direction> order = new ArrayList<>();
        order.add(dir);
        for (var direction : new Direction[] {
            Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
        }) {
            if (direction != dir) {
                order.add(direction);
            }
        }
        Vec3d centerPos = blockPos.toCenterPos();
        if (enableAirPlace) {
            if (!order.isEmpty()) {
                Direction availableDirection = order.get(0);
                Vec3d plateCenter = centerPos.offset(availableDirection, 0.5);
                result.add(new FlagEntry<>(
                        false, new BlockHitResult(plateCenter, availableDirection.getOpposite(), blockPos, false)));
            }
        }
        FlagEntry<BlockHitResult> current = null;
        for (var direction : order) {
            Vec3d plateCenter = centerPos.offset(direction, 0.5);
            Vec3d interactBlockCenter = centerPos.offset(direction, 1.0D);
            BlockPos targetPos = BlockPos.ofFloored(interactBlockCenter);
            BlockState interactState = mc.world.getBlockState(targetPos);
            if ((interactState.isAir() || interactState.isLiquid())) {
                continue;
            }
            boolean mayInteract = InteractUtils.isInteractAcceptable(mc.world, mc.player, targetPos, interactState);
            if (Box.from(Vec3d.of(targetPos)).contains(predictEyePos)) {
                // ?
                result.add(new FlagEntry<>(
                        mayInteract, new BlockHitResult(plateCenter, direction.getOpposite(), targetPos, true)));
            } else {
                Vec3d iSeeVect = predictEyePos.subtract(plateCenter);
                Vec3d plateLLL = Vec3d.of(direction.getVector());
                if (enablePositionPlace || iSeeVect.dotProduct(plateLLL) < 0) {
                    result.add(new FlagEntry<>(
                            mayInteract, new BlockHitResult(plateCenter, direction.getOpposite(), targetPos, false)));
                }
            }
        }
        return result;
    }

    public static FlagEntry<BlockHitResult> createSpecificStateHitResult(
            BlockPos placeTargetBlock, BlockState targetState, boolean enableAirPlace, boolean enablePositionPlace) {
        return createSpecificStateHitResult(
                mc.player.getFacing(), placeTargetBlock, targetState, enableAirPlace, enablePositionPlace);
    }

    public static FlagEntry<BlockHitResult> createSpecificStateHitResult(
            Direction preferredDirection,
            BlockPos placeTargetBlock,
            BlockState targetState,
            boolean enableAirPlace,
            boolean enablePositionPlace) {
        Set<Direction> availableSides = new HashSet<>(List.of(Direction.values()));
        Block block = targetState.getBlock();
        Vec3d centerPos = placeTargetBlock.toCenterPos();
        Vec3d eyePos = mc.player.getEyePos();
        List<Direction> order = new ArrayList<>(6);
        FlagEntry<BlockHitResult> result = null;
        if (block instanceof StairsBlock) {
            BlockHalf half = targetState.get(StairsBlock.HALF);
            order.add(half == BlockHalf.TOP ? Direction.UP : Direction.DOWN);
            order.addAll(
                    Arrays.asList(new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}));
            for (var direction : order) {
                Vec3d plateCenter = centerPos.offset(direction, 0.5);
                Vec3d interactBlockCenter = centerPos.offset(direction, 1.0D);
                BlockPos targetPos = BlockPos.ofFloored(interactBlockCenter);

                Vec3d interactPos = (direction == Direction.DOWN || direction == Direction.UP)
                        ? plateCenter
                        : plateCenter.add(0, 0.25 * (half == BlockHalf.TOP ? 1 : -1), 0);
                if (enableAirPlace) {
                    return new FlagEntry<>(
                            false, new BlockHitResult(interactPos, direction.getOpposite(), placeTargetBlock, false));
                }
                BlockState interactState = mc.world.getBlockState(targetPos);
                if (interactState.isAir() || interactState.isLiquid()) {
                    continue;
                }
                boolean mayInteract = InteractUtils.isInteractAcceptable(mc.world, mc.player, targetPos, interactState);
                if (Box.from(Vec3d.of(targetPos)).contains(eyePos)) {
                    // ?
                    var re = new FlagEntry<>(
                            mayInteract, new BlockHitResult(interactPos, direction.getOpposite(), targetPos, true));
                    if (!re.flag()) {
                        return re;
                    } else if (result == null) {
                        result = re;
                    }
                } else {
                    Vec3d iSeeVect = eyePos.subtract(plateCenter);
                    Vec3d plateLLL = Vec3d.of(direction.getVector());
                    if (enablePositionPlace || iSeeVect.dotProduct(plateLLL) < 0) {
                        var re = new FlagEntry<>(
                                mayInteract,
                                new BlockHitResult(interactPos, direction.getOpposite(), targetPos, false));
                        if (!re.flag()) {
                            return re;
                        } else if (result == null) {
                            result = re;
                        }
                    }
                }
            }
        } else if (block instanceof SlabBlock) {
            SlabType type = targetState.get(SlabBlock.TYPE);
            int sgn;
            if (type == SlabType.DOUBLE) {
                order.add(Direction.UP);
                order.add(Direction.DOWN);
                sgn = 0;
            } else if (type == SlabType.TOP) {
                order.add(Direction.UP);
                sgn = 1;
            } else if (type == SlabType.BOTTOM) {
                order.add(Direction.DOWN);
                sgn = -1;
            } else {
                sgn = 0;
            }
            order.addAll(
                    Arrays.asList(new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}));
            for (var direction : order) {
                Vec3d plateCenter = centerPos.offset(direction, 0.5);
                Vec3d interactBlockCenter = centerPos.offset(direction, 1.0D);
                BlockPos targetPos = BlockPos.ofFloored(interactBlockCenter);
                // check double condition
                BlockState interactState = mc.world.getBlockState(targetPos);
                // this will make the interactState become DOUBLE
                if (interactState.isOf(targetState.getBlock())
                        && interactState.get(SlabBlock.TYPE) != SlabType.DOUBLE
                        && interactState.get(SlabBlock.TYPE) != targetState.get(SlabBlock.TYPE)) {
                    continue;
                }
                Vec3d interactPos = (direction == Direction.DOWN || direction == Direction.UP)
                        ? plateCenter
                        : plateCenter.add(0, 0.25 * (double) sgn, 0);
                if (enableAirPlace) {
                    return new FlagEntry<>(
                            false, new BlockHitResult(interactPos, direction.getOpposite(), placeTargetBlock, false));
                }
                if ((interactState.isAir() || interactState.isLiquid())) {
                    continue;
                }
                boolean mayInteract = InteractUtils.isInteractAcceptable(mc.world, mc.player, targetPos, interactState);
                if (Box.from(Vec3d.of(targetPos)).contains(eyePos)) {
                    // ?
                    var re = new FlagEntry<>(
                            mayInteract, new BlockHitResult(interactPos, direction.getOpposite(), targetPos, true));
                    if (!re.flag()) {
                        return re;
                    } else if (result == null) {
                        result = re;
                    }
                } else {
                    Vec3d iSeeVect = eyePos.subtract(plateCenter);
                    Vec3d plateLLL = Vec3d.of(direction.getVector());
                    if (enablePositionPlace || iSeeVect.dotProduct(plateLLL) < 0) {
                        var re = new FlagEntry<>(
                                mayInteract,
                                new BlockHitResult(interactPos, direction.getOpposite(), targetPos, false));
                        if (!re.flag()) {
                            return re;
                        } else if (result == null) {
                            result = re;
                        }
                    }
                }
            }
            // DOUBLE 类型不修改
        } else if (block instanceof TrapdoorBlock) {
            BlockHalf half = targetState.get(TrapdoorBlock.HALF);
            // 根据 HALF 决定优先的垂直方向
            if (half == BlockHalf.BOTTOM) {
                order.add(Direction.DOWN);
                availableSides.remove(Direction.UP); // 不能从上面点击放置下半活板门
            } else {
                order.add(Direction.UP);
                availableSides.remove(Direction.DOWN); // 不能从下面点击放置上半活板门
            }
            // 添加水平方向
            order.addAll(Arrays.asList(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST));

            for (var direction : order) {
                if (direction.getAxis().isHorizontal()
                        && targetState.get(TrapdoorBlock.FACING) != direction.getOpposite()) {
                    continue;
                }
                Vec3d plateCenter = centerPos.offset(direction, 0.5);
                Vec3d interactBlockCenter = centerPos.offset(direction, 1.0);
                BlockPos targetPos = BlockPos.ofFloored(interactBlockCenter);
                BlockState interactState = mc.world.getBlockState(targetPos);
                // 交互点：对于垂直方向使用 plateCenter，对于水平方向需要根据 HALF 调整 Y 偏移
                Vec3d interactPos;
                if (direction == Direction.DOWN || direction == Direction.UP) {
                    interactPos = plateCenter;
                } else {
                    double yOffset = (half == BlockHalf.TOP) ? 0.25 : -0.25;
                    interactPos = plateCenter.add(0, yOffset, 0);
                }
                if (enableAirPlace) {
                    return new FlagEntry<>(
                            false, new BlockHitResult(interactPos, direction.getOpposite(), placeTargetBlock, false));
                }
                if ((interactState.isAir() || interactState.isLiquid())) {
                    continue;
                }
                boolean mayInteract = InteractUtils.isInteractAcceptable(mc.world, mc.player, targetPos, interactState);
                if (Box.from(Vec3d.of(targetPos)).contains(eyePos)) {
                    // ?
                    var re = new FlagEntry<>(
                            mayInteract, new BlockHitResult(interactPos, direction.getOpposite(), targetPos, true));
                    if (!re.flag()) {
                        return re;
                    } else if (result == null) {
                        result = re;
                    }
                } else {
                    Vec3d iSeeVect = eyePos.subtract(plateCenter);
                    Vec3d plateLLL = Vec3d.of(direction.getVector());
                    if (enablePositionPlace || iSeeVect.dotProduct(plateLLL) < 0) {
                        var re = new FlagEntry<>(
                                mayInteract,
                                new BlockHitResult(interactPos, direction.getOpposite(), targetPos, false));
                        if (!re.flag()) {
                            return re;
                        } else if (result == null) {
                            result = re;
                        }
                    }
                }
            }
        } else {
            // 对特定方块进行方向过滤（仅基于 getSide 的直接使用）
            boolean forceSneak = false;
            if (block instanceof EndRodBlock) {
                Direction targetFacing = targetState.get(EndRodBlock.FACING);
                // EndRodBlock: getPlacementState 直接 with(FACING, ctx.getSide())
                availableSides.removeIf(dir -> dir != targetFacing);
            } else if (block instanceof ChestBlock) {
                ChestType targetChestType = targetState.get(ChestBlock.CHEST_TYPE);
                Direction targetFacing = targetState.get(ChestBlock.FACING);
                BlockPos pos = placeTargetBlock;

                if (mc.player.shouldCancelInteraction()) {

                    // ---- 预检查：双箱能否形成 ----
                    if (targetChestType != ChestType.SINGLE) {
                        Direction left = targetFacing.rotateYCounterclockwise();
                        Direction right = targetFacing.rotateYClockwise();
                        boolean canForm = false;
                        for (Direction d : new Direction[] {left, right}) {
                            BlockPos neighborPos = pos.offset(d);
                            BlockState neighborState = mc.world.getBlockState(neighborPos);
                            if (neighborState.getBlock() instanceof ChestBlock
                                    && neighborState.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE
                                    && neighborState.get(ChestBlock.FACING) == targetFacing) {
                                canForm = true;
                                break;
                            }
                        }
                        if (!canForm) {
                            targetChestType = ChestType.SINGLE; // 降级
                        }
                    }

                    // ---- 根据是否潜行处理 ----
                    if (targetChestType == ChestType.SINGLE) {
                        // 单箱：排除会导致合并的水平面
                        availableSides.removeIf(side -> {
                            if (!side.getAxis().isHorizontal()) return false;
                            Direction opposite = side.getOpposite();
                            BlockPos neighborPos = pos.offset(opposite);
                            BlockState neighborState = mc.world.getBlockState(neighborPos);
                            if (!(neighborState.getBlock() instanceof ChestBlock)) return false;
                            if (neighborState.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) return false;
                            Direction neighborFacing = neighborState.get(ChestBlock.FACING);
                            return neighborFacing.getAxis() != side.getAxis();
                        });
                    } else {
                        // 双箱（此时 canForm 一定为 true）：只保留能精确形成该双箱的水平面
                        final ChestType finalTargetChestType = targetChestType;
                        availableSides.removeIf(side -> {
                            if (!side.getAxis().isHorizontal()) return true;
                            Direction opposite = side.getOpposite();
                            BlockPos neighborPos = pos.offset(opposite);
                            BlockState neighborState = mc.world.getBlockState(neighborPos);
                            if (!(neighborState.getBlock() instanceof ChestBlock)) return true;
                            if (neighborState.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) return true;
                            Direction neighborFacing = neighborState.get(ChestBlock.FACING);
                            if (neighborFacing.getAxis() == side.getAxis()) return true;
                            ChestType finalChestType = (neighborFacing.rotateYCounterclockwise() == side.getOpposite())
                                    ? ChestType.RIGHT
                                    : ChestType.LEFT;
                            Direction finalFacing = neighborFacing;
                            return !(finalFacing == targetFacing && finalChestType == finalTargetChestType);
                        });
                    }
                } else {
                    // 非下蹲：不过滤 availableSides，但单箱时检查是否需 forceSneak
                    if (targetChestType == ChestType.SINGLE) {
                        Direction left = targetFacing.rotateYCounterclockwise();
                        Direction right = targetFacing.rotateYClockwise();
                        boolean canMerge = false;
                        for (BlockPos neighborPos : new BlockPos[] {pos.offset(left), pos.offset(right)}) {
                            BlockState neighborState = mc.world.getBlockState(neighborPos);
                            if (neighborState.getBlock() instanceof ChestBlock
                                    && neighborState.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE
                                    && neighborState.get(ChestBlock.FACING) == targetFacing) {
                                canMerge = true;
                                break;
                            }
                        }
                        if (canMerge) {
                            forceSneak = true;
                        }
                    }
                }
            } else if (block instanceof BellBlock) {
                // BellBlock: 在水平方向时，FACING 设置为 ctx.getSide().getOpposite()
                // 垂直方向时 FACING 使用 getHorizontalPlayerFacing，不依赖 getSide
                Direction targetFacing = targetState.get(BellBlock.FACING);
                if (targetFacing.getAxis().isHorizontal()) {
                    // 只允许与 targetFacing 相反的方向（因为 with(FACING, direction.getOpposite())）
                    Direction allowedSide = targetFacing.getOpposite();
                    availableSides.removeIf(dir -> dir != allowedSide);
                }
                // 如果 targetFacing 垂直，则保留所有方向（因为垂直时 FACING 不由 getSide 决定）
            } else if (block instanceof LightningRodBlock) {
                Direction targetFacing = targetState.get(LightningRodBlock.FACING);
                // LightningRodBlock: 直接 with(FACING, ctx.getSide())
                availableSides.removeIf(dir -> dir != targetFacing);
            } else if (block instanceof ShulkerBoxBlock) {
                Direction targetFacing = targetState.get(ShulkerBoxBlock.FACING);
                // ShulkerBoxBlock: 直接 with(FACING, ctx.getSide())
                availableSides.removeIf(dir -> dir != targetFacing);
            } else if (block instanceof HopperBlock) {
                Direction targetFacing = targetState.get(HopperBlock.FACING);
                // HopperBlock: getPlacementState 逻辑
                //   direction = ctx.getSide().getOpposite()
                //   if direction.getAxis() == Y -> final = DOWN, else final = direction
                // 因此允许的 getSide 需满足：
                //   如果 targetFacing == DOWN，则允许 UP 或 DOWN
                //   如果 targetFacing 水平，则允许 targetFacing.getOpposite()
                if (targetFacing == Direction.DOWN) {
                    availableSides.removeIf(dir -> dir != Direction.UP && dir != Direction.DOWN);
                } else {
                    Direction direction = targetFacing.getOpposite();
                    availableSides.removeIf(dir -> dir != direction);
                }
            } else if (block instanceof RotatedInfestedBlock) {
                Direction.Axis targetAxis = targetState.get(PillarBlock.AXIS);
                // RotatedInfestedBlock: with(PillarBlock.AXIS, ctx.getSide().getAxis())
                // 允许的方向轴必须等于 targetAxis
                availableSides.removeIf(dir -> dir.getAxis() != targetAxis);
            } else if (block instanceof AmethystClusterBlock) {
                Direction targetFacing = targetState.get(AmethystClusterBlock.FACING);
                // AmethystClusterBlock: 直接 with(FACING, ctx.getSide())
                availableSides.removeIf(dir -> dir != targetFacing);
            } else if (block instanceof WallHangingSignBlock) {
                // 注意：WallHangingSignBlock 已经在 else 分支之前单独处理了？这里补充过滤
                // 挂式告示牌不能放在天花板或地板上，且 FACING 由 getSide 的相反方向决定？实际上其 getPlacementState 遍历水平方向
                // 简化：移除垂直方向，水平方向保留所有（因为最终 FACING 由多个因素决定，但 getSide 用于确定方向之一）
                // 由于我们已经在 TrapdoorBlock 之后处理了 WallHangingSignBlock 的过滤（见之前代码），这里不再重复
            }
            // 其他方块不做过滤（保留所有方向）
            Direction dir = preferredDirection;
            if (availableSides.contains(dir.getOpposite())) {
                order.add(dir);
            }
            for (var direction : new Direction[] {
                Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
            }) {
                if (direction != dir && availableSides.contains(direction.getOpposite())) {
                    order.add(direction);
                }
            }
            if (enableAirPlace) {
                if (order.isEmpty()) {
                    return null;
                }
                Direction availableDirection = order.get(0);
                Vec3d plateCenter = centerPos.offset(availableDirection, 0.5);
                return new FlagEntry<>(
                        forceSneak,
                        new BlockHitResult(plateCenter, availableDirection.getOpposite(), placeTargetBlock, false));
            } else {
                for (var direction : order) {
                    Vec3d plateCenter = centerPos.offset(direction, 0.5);
                    Vec3d interactBlockCenter = centerPos.offset(direction, 1.0D);
                    BlockPos targetPos = BlockPos.ofFloored(interactBlockCenter);
                    BlockState interactState = mc.world.getBlockState(targetPos);
                    if ((interactState.isAir() || interactState.isLiquid())) {
                        continue;
                    }
                    boolean mayInteract =
                            InteractUtils.isInteractAcceptable(mc.world, mc.player, targetPos, interactState);
                    if (Box.from(Vec3d.of(targetPos)).contains(eyePos)) {
                        // ?
                        var re = new FlagEntry<>(
                                mayInteract, new BlockHitResult(plateCenter, direction.getOpposite(), targetPos, true));
                        if (!re.flag()) {
                            return re;
                        } else if (result == null) {
                            result = re;
                        }
                    } else {
                        Vec3d iSeeVect = eyePos.subtract(plateCenter);
                        Vec3d plateLLL = Vec3d.of(direction.getVector());
                        if (enablePositionPlace || iSeeVect.dotProduct(plateLLL) < 0) {
                            var re = new FlagEntry<>(
                                    mayInteract || forceSneak,
                                    new BlockHitResult(plateCenter, direction.getOpposite(), targetPos, false));
                            if (!re.flag()) {
                                return re;
                            } else if (result == null) {
                                result = re;
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    public static FlagEntry<Vec2f> createLiquidPlacementRaycast(Vec3d eyePos, BlockPos pos, BlockState targetState) {
        if (mc.world == null || mc.player == null) {
            return null;
        }

        boolean isWaterState =
                targetState.isLiquid() && targetState.getFluidState().isIn(FluidTags.WATER);
        boolean isWaterloggedState =
                !targetState.isLiquid() && targetState.getFluidState().isIn(FluidTags.WATER);
        double interactionRange = AttributeUtils.getPlayerBlockInteractionRange(mc.player);

        Direction preferredDirection = mc.player.getFacing().getOpposite();
        List<Direction> directions = new ArrayList<>();
        directions.add(preferredDirection);
        for (Direction direction : new Direction[] {
            Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
        }) {
            if (direction != preferredDirection) {
                directions.add(direction);
            }
        }
        FlagEntry<Vec2f> result = null;
        for (Direction direction : directions) {
            // definitely can not interact from

            BlockPos interactPos;
            Direction hitSide;
            BlockState hitState;
            boolean sneakFlag;
            if (isWaterloggedState) {
                interactPos = pos;
                hitSide = direction;
                sneakFlag = false;
                hitState = mc.world.getBlockState(interactPos);
            } else if (isWaterState) {
                interactPos = pos.offset(direction.getOpposite());
                hitSide = direction;
                hitState = mc.world.getBlockState(interactPos);
                sneakFlag = hitState.getBlock() instanceof FluidFillable fillable
                        && fillable.canFillWithFluid(
                                mc.player,
                                mc.world,
                                interactPos,
                                hitState,
                                targetState.getFluidState().getFluid());
            } else {
                continue;
            }
            Vec3d facingDirection = interactPos.toCenterPos().subtract(eyePos);

            if (new Vec3d(direction.getVector()).dotProduct(facingDirection) > 0) {
                continue;
            }
            if (hitState.isAir() || hitState.isLiquid()) {
                continue;
            }

            for (Vec3d hitPoint : createLiquidPlacementFacePoints(interactPos, hitState, hitSide)) {
                Vec3d look = hitPoint.subtract(eyePos);
                if (look.lengthSquared() < 1.0E-12 || look.lengthSquared() > interactionRange * interactionRange) {
                    continue;
                }

                Vec2f rotation = EntityUtils.rotationToPitchYaw(look.normalize());
                Vec3d rotationVec = EntityUtils.pitchYawToRotation(rotation.x, rotation.y);
                BlockHitResult raycastResult = mc.world.raycast(new RaycastContext(
                        eyePos,
                        eyePos.add(rotationVec.multiply(interactionRange)),
                        RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE,
                        mc.player));
                if (raycastResult.getType() != HitResult.Type.BLOCK) {
                    continue;
                }
                if (raycastResult.getBlockPos().equals(interactPos) && raycastResult.getSide() == hitSide) {
                    var re = new FlagEntry<>(sneakFlag, rotation);
                    if (re.flag() == mc.player.isSneaking()) {
                        return re;
                    } else if (result == null) {
                        result = re;
                    }
                }
            }
        }
        return result;
    }

    private static List<Vec3d> createLiquidPlacementFacePoints(BlockPos pos, BlockState state, Direction side) {
        List<Vec3d> points = new ArrayList<>();
        for (Box localBox : state.getOutlineShape(mc.world, pos).getBoundingBoxes()) {
            Box box = localBox.offset(pos).expand(-1.0E-7, -1.0E-7, -1.0E-7);
            if (box.getLengthX() <= 0 || box.getLengthY() <= 0 || box.getLengthZ() <= 0) {
                continue;
            }
            addLiquidPlacementFacePoints(points, box, side);
        }
        return points;
    }

    private static void addLiquidPlacementFacePoints(List<Vec3d> points, Box box, Direction side) {
        double minX = box.minX;
        double midX = (box.minX + box.maxX) * 0.5D;
        double maxX = box.maxX;
        double minY = box.minY;
        double midY = (box.minY + box.maxY) * 0.5D;
        double maxY = box.maxY;
        double minZ = box.minZ;
        double midZ = (box.minZ + box.maxZ) * 0.5D;
        double maxZ = box.maxZ;

        switch (side) {
            case DOWN -> addLiquidPlacementGrid(
                    points, box.minY, minX, midX, maxX, minZ, midZ, maxZ, Direction.Axis.Y, true);
            case UP -> addLiquidPlacementGrid(
                    points, box.maxY, minX, midX, maxX, minZ, midZ, maxZ, Direction.Axis.Y, true);
            case NORTH -> addLiquidPlacementGrid(
                    points, box.minZ, minX, midX, maxX, minY, midY, maxY, Direction.Axis.Z, false);
            case SOUTH -> addLiquidPlacementGrid(
                    points, box.maxZ, minX, midX, maxX, minY, midY, maxY, Direction.Axis.Z, false);
            case WEST -> addLiquidPlacementGrid(
                    points, box.minX, minY, midY, maxY, minZ, midZ, maxZ, Direction.Axis.X, false);
            case EAST -> addLiquidPlacementGrid(
                    points, box.maxX, minY, midY, maxY, minZ, midZ, maxZ, Direction.Axis.X, false);
        }
    }

    private static void addLiquidPlacementGrid(
            List<Vec3d> points,
            double fixed,
            double minA,
            double midA,
            double maxA,
            double minB,
            double midB,
            double maxB,
            Direction.Axis axis,
            boolean horizontalPlane) {
        if (horizontalPlane) {
            points.add(new Vec3d(midA, fixed, midB));
            points.add(new Vec3d(minA, fixed, midB));
            points.add(new Vec3d(maxA, fixed, midB));
            points.add(new Vec3d(midA, fixed, minB));
            points.add(new Vec3d(midA, fixed, maxB));
            points.add(new Vec3d(minA, fixed, minB));
            points.add(new Vec3d(minA, fixed, maxB));
            points.add(new Vec3d(maxA, fixed, minB));
            points.add(new Vec3d(maxA, fixed, maxB));
            return;
        }

        switch (axis) {
            case X -> {
                points.add(new Vec3d(fixed, midA, midB));
                points.add(new Vec3d(fixed, minA, midB));
                points.add(new Vec3d(fixed, maxA, midB));
                points.add(new Vec3d(fixed, midA, minB));
                points.add(new Vec3d(fixed, midA, maxB));
                points.add(new Vec3d(fixed, minA, minB));
                points.add(new Vec3d(fixed, minA, maxB));
                points.add(new Vec3d(fixed, maxA, minB));
                points.add(new Vec3d(fixed, maxA, maxB));
            }
            case Z -> {
                points.add(new Vec3d(midA, midB, fixed));
                points.add(new Vec3d(minA, midB, fixed));
                points.add(new Vec3d(maxA, midB, fixed));
                points.add(new Vec3d(midA, minB, fixed));
                points.add(new Vec3d(midA, maxB, fixed));
                points.add(new Vec3d(minA, minB, fixed));
                points.add(new Vec3d(minA, maxB, fixed));
                points.add(new Vec3d(maxA, minB, fixed));
                points.add(new Vec3d(maxA, maxB, fixed));
            }
            default -> {}
        }
    }

    private static Entity lastInteractEntity = null;
    private static int lastInteractTimestamp = -1;

    private static void listenInteractEntityPacket(PlayerInteractEntityC2SPacket packet) {
        if (((Enum) packet.type.getType()).name().equals("INTERACT")) {
            lastInteractEntity = mc.world.getEntityById(packet.entityId);
            lastInteractTimestamp = Tasks.getTick();
        }
    }

    public static Entity predictScreenFrom(Predicate<Entity> targetBlock) {
        int timeStamp = Tasks.getTick();
        // 在一秒内反应的 可以考虑
        if (timeStamp < lastInteractTimestamp + 20
                && lastInteractEntity != null
                && lastInteractEntity.isAlive()
                && targetBlock.test(lastInteractEntity)) {
            return lastInteractEntity;
            // block Type not match,
        }
        return RaycastUtils.rayTraceSpecificEntity(targetBlock).orElse(null);
    }

    @ApiMethod
    @Getter
    public static final ModuleGroup moduleManager = new ModuleGroup("Interaction");

    @Getter
    private static InteractExtra interactExtra;

    @Getter
    private static GuiInteract guiInteract;

    @Getter
    private static AutoClick autoClick;

    @Getter
    private static Interact interact;

    @Getter
    private static Scaffold scaffold;

    @Getter
    private static TpInteract tpInteract;

    @Getter
    private static Airplace airplace;

    @Getter
    private static AutoSurround autoSurround;

    @Getter
    private static BlockRotate blockRotate;

    @Getter
    private static PrinterRewrite printerRewrite;

    @Getter
    private static NoInteract noInteract;

    @Getter
    private static AutoPlate autoPlate;

    @Getter
    private static AutoSlab autoSlab;

    @Getter
    private static AutoRide autoRide;

    @Getter
    private static AutoEat autoEat;

    @Getter
    private static AutoUse autoUse;

    @Getter
    private static InteractManager interactManager;

    private static void initModules(ModuleManager m) {
        interactExtra = new InteractExtra().register(m);
        guiInteract = new GuiInteract().register(m);
        autoClick = new AutoClick().register(m);
        interact = new Interact().register(m);
        scaffold = new Scaffold().register(m);
        tpInteract = new TpInteract().register(m);
        airplace = new Airplace().register(m);
        autoSurround = new AutoSurround().register(m);
        blockRotate = new BlockRotate().register(m);
        printerRewrite = new PrinterRewrite().register(m);
        autoPlate = new AutoPlate().register(m);
        autoSlab = new AutoSlab().register(m);
        noInteract = new NoInteract().register(m);
        autoRide = new AutoRide().register(m);
        autoEat = new AutoEat().register(m);
        autoUse = new AutoUse().register(m);
        interactManager = new InteractManager().register(m);
    }

    static {
        moduleManager.registerFactories(InteractionTasks::initModules);
        HackModules.registerModuleGroup(moduleManager);
        Listener.registerSinglePacketListener(
                PlayerInteractEntityC2SPacket.class, InteractionTasks::listenInteractEntityPacket);
    }
}
