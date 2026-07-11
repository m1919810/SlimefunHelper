package me.matl114.hacks;

import com.google.common.util.concurrent.Runnables;
import java.util.*;
import java.util.List;
import lombok.Getter;
import me.matl114.accessors.access.ClientPlayerAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.catchers.PacketCatcherImpl;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.interact.*;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.managers.Configs;
import me.matl114.utils.*;
import me.matl114.utils.collections.FlagEntry;
import me.matl114.utils.entity.LegalMovementManager;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.ApiStatus;

public class InteractionTasks {
    public static void init() {}

    private static MinecraftClient mc = MinecraftClient.getInstance();
    //
    //    public static void placeBlock(int idx, BlockHitResult result){
    //
    //    }

    public static void placeBlock(Hand hand, BlockHitResult result) {
        ActionResult actionResult2 = mc.interactionManager.interactBlock(mc.player, hand, result);
        if (actionResult2.isAccepted()) {
            if (((ActionResult.Success) actionResult2).swingSource() == ActionResult.SwingSource.CLIENT) {
                mc.player.swingHand(hand);
            }
            return;
        }
    }

    public static void addPostRotationCorrectTask(Vec3d look3d, Runnable callback) {
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

                        Vec2f rotation = EntityUtils.rotationToPitchYaw(
                                look3d.subtract(mc.player.getEyePos().add(mc.player.getVelocity()))
                                        .normalize());
                        movementManagerEvent.context.pushImportantRotation(true, true);
                        EntityUtils.setEntityYawSafe(player, rotation.y);
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
                        //                        if(py != null){
                        //                            Vec3d vec3d1 = mc.player.getEyePos();;
                        //                            Vec3d vec3d2 = EntityUtils.pitchYawToRotation(py.x, py.y);
                        //                            RenderTasks.drawLine(vec3d1, vec3d2, 300, Color.MAGENTA);
                        //
                        //                        }
                        return false;
                    }
                });
    }

    public static void handlePlaceMode(Configs.LegalInteractMode mode, BlockHitResult result, Hand hand) {
        switch (mode) {
            case USEITEM_PACKET -> {
                Vec2f rotation = EntityUtils.rotationToPitchYaw(result.getBlockPos()
                        .toCenterPos()
                        .subtract(mc.player.getEyePos())
                        .normalize());
                mc.interactionManager.sendSequencedPacket(
                        mc.world, (i) -> new PlayerInteractItemC2SPacket(hand, i, rotation.y, rotation.x));
                InteractionTasks.placeBlock(hand, result);
            }
            case DELAY_MOVEMENT -> {
                InteractionTasks.placeBlock(hand, result);
                InteractionTasks.addPostRotationCorrectTask(result.getBlockPos().toCenterPos(), Runnables.doNothing());
            }
            case MOVEMENT_POST -> {
                MutableObject<PlayerInteractBlockC2SPacket> catcher = new MutableObject<>();
                Listener.addPrePacketCatcher(new PacketCatcherImpl<>(PlayerInteractBlockC2SPacket.class, (eve) -> {
                    if (eve.isCancelled()) return true;
                    catcher.setValue(eve.context);
                    eve.cancel();
                    return true;
                }));
                InteractionTasks.placeBlock(hand, result);
                if (catcher.getValue() != null) {
                    var pkt = catcher.getValue();
                    InteractionTasks.addPostRotationCorrectTask(
                            result.getBlockPos().toCenterPos(),
                            () -> mc.getNetworkHandler().sendPacket(pkt));
                }
            }
            case LEGACY_SLIENT_ROT -> {
                Vec2f rotation = EntityUtils.rotationToPitchYaw(result.getBlockPos()
                        .toCenterPos()
                        .subtract(mc.player.getEyePos())
                        .normalize());
                LegacySnapRotManager.INSTANCE.snapAt(rotation.x, rotation.y, false);
                InteractionTasks.placeBlock(hand, result);
            }
            case NONE -> {
                InteractionTasks.placeBlock(hand, result);
            }
        }
    }

    public static void flushACPlaceQueue() {
        // for flush places
        //        ACTasks.getDisablerManager().flushACPlaceQueue();
    }

    public static void handlePlaceModeMulti(
            Configs.LegalInteractMode mode, Vec3d targetCenter, List<Pair<BlockHitResult, Hand>> resultList) {
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
                    InteractionTasks.placeBlock(hand, result);
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
                    InteractionTasks.placeBlock(hand, result);
                }
                InteractionTasks.addPostRotationCorrectTask(targetCenter, Runnables.doNothing());
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
                    InteractionTasks.placeBlock(hand, result);
                }
            }
            case NONE -> {
                for (Pair<BlockHitResult, Hand> pair : resultList) {
                    var hand = pair.getRight();
                    var result = pair.getLeft();
                    InteractionTasks.placeBlock(hand, result);
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
            if (block instanceof EndRodBlock) {
                Direction targetFacing = targetState.get(EndRodBlock.FACING);
                // EndRodBlock: getPlacementState 直接 with(FACING, ctx.getSide())
                availableSides.removeIf(dir -> dir != targetFacing);
            } else if (block instanceof ChestBlock) {
                // ChestBlock: getPlacementState 未直接使用 getSide 设置 FACING（使用了 getHorizontalPlayerFacing）
                // 因此不做任何过滤，保留所有方向
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
                    Direction allowedSide = targetFacing.getOpposite();
                    availableSides.removeIf(dir -> dir != allowedSide);
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
                if (direction != dir && availableSides.contains(dir.getOpposite())) {
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
                        false,
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
            }
        }
        return result;
    }

    @ApiMethod
    @Getter
    public static final ModuleGroup moduleManager = new ModuleGroup("Interaction");

    @Getter
    public static InteractExtra interactExtra;

    @Getter
    public static GuiInteract guiInteract;

    @ApiStatus.Experimental
    public static AutoInteract autoInteract;

    @ApiStatus.Experimental
    public static AutoAttack autoAttack;

    @Getter
    public static Scaffold scaffold;

    @Getter
    public static TpInteract tpInteract;

    @Getter
    public static Airplace airplace;

    @Getter
    public static AutoSurround autoSurround;

    @Getter
    public static BlockRotate blockRotate;

    @Getter
    public static PrinterRewrite printerRewrite;

    @Getter
    public static AutoRide autoRide;

    @Getter
    public static AutoEat autoEat;

    @Getter
    public static AutoUse autoUse;

    @Getter
    public static InteractManager interactManager;

    private static void initModules(ModuleManager m) {
        interactExtra = new InteractExtra().register(m);
        guiInteract = new GuiInteract().register(m);
        scaffold = new Scaffold().register(m);
        tpInteract = new TpInteract().register(m);
        airplace = new Airplace().register(m);
        autoSurround = new AutoSurround().register(m);
        blockRotate = new BlockRotate().register(m);
        printerRewrite = new PrinterRewrite().register(m);
        autoRide = new AutoRide().register(m);
        autoEat = new AutoEat().register(m);
        autoUse = new AutoUse().register(m);
        interactManager = new InteractManager().register(m);
    }

    static {
        moduleManager.registerFactories(InteractionTasks::initModules);
        HackModules.registerModuleGroup(moduleManager);
    }
}
