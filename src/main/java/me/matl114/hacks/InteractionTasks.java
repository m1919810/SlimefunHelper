package me.matl114.hacks;

import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import lombok.Getter;
import me.matl114.events.Listener;
import me.matl114.events.impl.UseItemOnBlock;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.api.ModuleManager;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.interact.*;
import me.matl114.hacks.modules.inv.InvExtra;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.modules.move.RotationManager;
import me.matl114.hacks.utils.AttributeUtils;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.InteractUtils;
import me.matl114.hacks.utils.RaycastUtils;
import me.matl114.hacks.utils.enums.LegalInteractMode;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Tasks;
import me.matl114.utils.*;
import me.matl114.utils.collections.FlagEntry;
import net.minecraft.block.*;
import net.minecraft.block.enums.Attachment;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.Orientation;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

public class InteractionTasks {
    public static void init() {}

    private static MinecraftClient mc = MinecraftClient.getInstance();
    private static final Set<Block> weirdBoxes = Set.of(Blocks.LECTERN);
    private static final Set<Block> buggyBoxes = Set.of(
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.CHORUS_PLANT,
            Blocks.KELP,
            Blocks.KELP_PLANT,
            Blocks.TWISTING_VINES,
            Blocks.TWISTING_VINES_PLANT,
            Blocks.WEEPING_VINES,
            Blocks.WEEPING_VINES_PLANT,
            Blocks.REDSTONE_WIRE);
    //
    //    public static void placeBlock(int idx, BlockHitResult result){
    //
    //    }

    public static boolean interactItem(Hand hand, boolean realInteract, boolean swingHand) {
        return interactItem(
                hand,
                PlayerStateManager.INSTANCE.lastServerSidePitch,
                PlayerStateManager.INSTANCE.lastServerSideYaw,
                realInteract,
                swingHand);
    }

    public static boolean interactItem(Hand hand, Vec3d rot, boolean realInteract, boolean swingHand) {
        return interactItem(
                hand, EntityUtils.rotationToPitch(rot), EntityUtils.rotationToYaw(rot), realInteract, swingHand);
    }

    public static boolean interactItem(Hand hand, float pitch, float yaw, boolean realInteract, boolean swingHand) {
        if (realInteract || !ViaFabricPlusHooks.isSupportDupRot()) {
            Vec2f storePY = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
            EntityUtils.setEntityPitchSafe(mc.player, pitch);
            PlayerStateManager.setPlayerYawSafe(mc.player, yaw);
            var actionResult = mc.interactionManager.interactItem(mc.player, hand);
            if (swingHand) {
                InteractUtils.swingHandIfSuccess(actionResult, hand);
            }
            mc.player.setPitch(storePY.x);
            mc.player.setYaw(storePY.y);
            return actionResult.isAccepted();
        } else {
            LegacySnapRotManager.INSTANCE.snapAt(pitch, yaw, false);
            return false;
        }
    }

    public static void interactBlock(Hand hand, BlockHitResult result, boolean swing) {
        Vec2f storePY = new Vec2f(mc.player.getPitch(), mc.player.getYaw());
        // use true fucking rotation .
        mc.player.setPitch(PlayerStateManager.INSTANCE.lastServerSidePitch);
        mc.player.setYaw(PlayerStateManager.INSTANCE.lastServerSideYaw);
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

    public static void addPostRotationCorrectTask(BlockPos pos, Runnable callback) {
        RotationManager.INSTANCE.request(RotationManager.ofBlock(() -> pos).postTick(callback));
    }

    public static Vec3d createBlockRayCastDirection(BlockPos pos) {
        return createBlockRayCastDirection(pos, mc.player.getPos());
    }

    public static Vec3d createBlockRayCastDirection(BlockPos pos, Vec3d playerPos) {
        return InteractExtra.INSTANCE.getRaycast(playerPos, pos);
    }

    public static boolean handlePlaceMode(LegalInteractMode mode, BlockHitResult result, Hand hand) {
        return handlePlaceMode(mode, result, hand, true);
    }

    public static boolean canHandleInteract(LegalInteractMode mode, BlockHitResult result) {
        if (canDirectHit(result)) {
            return true;
        }
        return switch (mode) {
            case LEGACY_SLIENT_ROT, NONE -> true;
            case DELAY_MOVEMENT -> !RotationManager.INSTANCE.isOccupied();
            case MOVEMENT_POST -> false;
        };
    }

    public static boolean canDirectHit(BlockHitResult result) {
        return RaycastUtils.canRaycastHit(
                mc.player,
                PlayerStateManager.INSTANCE.lastPitch,
                PlayerStateManager.INSTANCE.lastYaw,
                result.getBlockPos(),
                InteractExtra.INSTANCE.getBlockReachDistance());
    }

    public static boolean handlePlaceMode(LegalInteractMode mode, BlockHitResult result, Hand hand, boolean swingHand) {
        boolean directlyHit = canDirectHit(result);
        switch (mode) {
            case DELAY_MOVEMENT -> {
                if (!directlyHit) {
                    if (!RotationManager.INSTANCE.request(RotationManager.ofBlock(result::getBlockPos))) {
                        return false;
                    }
                    RotationManager.INSTANCE.startQueuePacket();
                }
                InteractionTasks.interactBlock(hand, result, swingHand);
                return true;
            }
            case MOVEMENT_POST -> {
                if (!directlyHit) {
                    InteractionTasks.addPostRotationCorrectTask(result.getBlockPos(), () -> {
                        InteractionTasks.interactBlock(hand, result, swingHand);
                    });
                    return false;
                } else {
                    InteractionTasks.interactBlock(hand, result, swingHand);
                    return true;
                }
            }
            case LEGACY_SLIENT_ROT -> {
                if (!directlyHit) {
                    Vec2f rotation = EntityUtils.rotationToPitchYaw(createBlockRayCastDirection(result.getBlockPos()));
                    LegacySnapRotManager.INSTANCE.snapAt(rotation.x, rotation.y, false);
                }
                InteractionTasks.interactBlock(hand, result, swingHand);
                return true;
            }
            case NONE -> {
                InteractionTasks.interactBlock(hand, result, swingHand);
                return true;
            }
        }
        throw new IllegalStateException("Unhandled interaction mode: " + mode);
    }

    public static void finishInteractGhostHand(LegalInteractMode mode, Runnable callback) {
        if (callback == null) return;
        if (!mode.canMultiRotPlace()) {
            InvExtra.INSTANCE.pushPostTickGhostHandCallback(callback);
        } else {
            callback.run();
        }
    }

    public static boolean checkInHead(BlockPos targetPos, Vec3d playerPos) {
        BlockState state = mc.world.getBlockState(targetPos);
        if (weirdBoxes.contains(state.getBlock()) || buggyBoxes.contains(state.getBlock())) {
            return false;
        }
        Box boundary = getPositionPlaceBoundary(targetPos, state);
        return interactExtra.getPotentialEyeHeights(playerPos).anyMatch(boundary::contains);
    }

    public static boolean isSpecialPositionPlaceState(BlockState state) {
        return state.isIn(BlockTags.FENCES)
                || state.isIn(BlockTags.WALLS)
                || state.isIn(BlockTags.DOORS)
                || state.isIn(BlockTags.STAIRS)
                || weirdBoxes.contains(state.getBlock())
                || buggyBoxes.contains(state.getBlock());
    }

    public static Box getPositionPlaceBoundary(BlockPos pos, BlockState state) {
        var shape = state.getOutlineShape(mc.world, pos, ShapeContext.of(mc.player));
        if (shape.isEmpty()) {
            shape = state.getCollisionShape(mc.world, pos);
        }
        if (shape.isEmpty()) {
            return new Box(pos);
        }

        List<Box> shapeBoxes = shape.getBoundingBoxes();
        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();
        double maxX = pos.getX() + 1;
        double maxY = pos.getY() + 1;
        double maxZ = pos.getZ() + 1;
        for (Box localBox : shapeBoxes) {
            Box box = localBox.offset(pos);
            minX = Math.max(minX, box.minX);
            minY = Math.max(minY, box.minY);
            minZ = Math.max(minZ, box.minZ);
            maxX = Math.min(maxX, box.maxX);
            maxY = Math.min(maxY, box.maxY);
            maxZ = Math.min(maxZ, box.maxZ);
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static boolean checkPositionPlace(BlockPos pos, Direction face, Vec3d playerPos) {
        Vec3d plateCenter = pos.toCenterPos().offset(face, 0.5d);
        BlockState state = mc.world.getBlockState(pos);
        if (weirdBoxes.contains(state.getBlock()) || buggyBoxes.contains(state.getBlock())) {
            plateCenter = switch (face) {
                case DOWN -> new Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                case UP -> new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                case NORTH -> new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 1);
                case SOUTH -> new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ());
                case WEST -> new Vec3d(pos.getX() + 1, pos.getY() + 0.5, pos.getZ() + 0.5);
                case EAST -> new Vec3d(pos.getX(), pos.getY() + 0.5, pos.getZ() + 0.5);};
        } else if (!state.isFullCube(mc.world, pos) || isSpecialPositionPlaceState(state)) {
            Box boundary = getPositionPlaceBoundary(pos, state);
            plateCenter = switch (face) {
                case DOWN -> new Vec3d(pos.getX() + 0.5, boundary.minY, pos.getZ() + 0.5);
                case UP -> new Vec3d(pos.getX() + 0.5, boundary.maxY, pos.getZ() + 0.5);
                case NORTH -> new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, boundary.minZ);
                case SOUTH -> new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, boundary.maxZ);
                case WEST -> new Vec3d(boundary.minX, pos.getY() + 0.5, pos.getZ() + 0.5);
                case EAST -> new Vec3d(boundary.maxX, pos.getY() + 0.5, pos.getZ() + 0.5);};
        }
        Vec3d directionVec = Vec3d.of(face.getVector());
        Vec3d finalPlaceCenter = plateCenter;
        return interactExtra
                .getPotentialEyeHeights(playerPos)
                .anyMatch(eye -> eye.subtract(finalPlaceCenter).dotProduct(directionVec) > 0);
    }

    public static boolean checkInteractRange(BlockPos interactBlockPos, Vec3d playerPos, double interactRange) {
        return interactExtra.isWithinInteractRange(playerPos, interactBlockPos, interactRange);
    }

    public static boolean checkInteractRange(BlockPos interactBlockPos, Vec3d playerPos) {
        return interactExtra.isWithinInteractRange(playerPos, interactBlockPos);
    }

    public static boolean checkPositionPlace(BlockHitResult hitResult) {
        if (hitResult.isInsideBlock()) {
            return checkInHead(hitResult.getBlockPos(), mc.player.getPos());
        } else {
            return checkPositionPlace(hitResult.getBlockPos(), hitResult.getSide(), mc.player.getPos());
        }
    }

    public static BlockHitResult createHitResult(BlockPos pos, Vec3d playerPos) {
        Direction dir = Direction.getFacing(
                        pos.toCenterPos().subtract(playerPos.add(0, mc.player.dimensions.eyeHeight(), 0)))
                .getOpposite();
        List<Direction> list = new ArrayList<>();
        list.add(dir);
        for (var direction : new Direction[] {
            Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
        }) {
            if (direction != dir) {
                list.add(direction);
            }
        }
        for (var direction : list) {
            boolean checkInHead = checkInHead(pos, playerPos);
            if (checkInHead || checkPositionPlace(pos, dir, playerPos)) {
                return new BlockHitResult(pos.toCenterPos().offset(direction, 0.5), direction, pos, checkInHead);
            }
        }
        return RaycastUtils.createHitResult(pos, dir);
    }

    public static BlockHitResult createHitResult(BlockPos pos, Direction blockFace) {
        Vec3d endVec = pos.toCenterPos().offset(blockFace, 0.5);
        return new BlockHitResult(endVec, blockFace, pos, checkInHead(pos, mc.player.getPos()));
    }

    public static FlagEntry<BlockHitResult> getInteractEntry(BlockHitResult result) {
        boolean mayInteract = InteractUtils.isInteractOnBlockAcceptable(
                mc.world, mc.player, result.getBlockPos(), mc.world.getBlockState(result.getBlockPos()));
        return new FlagEntry<>(mayInteract, result);
    }

    public static FlagEntry<BlockHitResult> getPlaceSupportingResult(
            BlockPos blockPos, boolean enableAirPlace, boolean enablePositionPlace) {
        return getPlaceSupportingResult(
                mc.player.getPos(), blockPos, mc.player.getFacing(), enableAirPlace, enablePositionPlace);
    }

    public static FlagEntry<BlockHitResult> getPlaceSupportingResult(
            BlockPos blockPos, Direction preferredDirection, boolean enableAirPlace, boolean enablePositionPlace) {
        return getPlaceSupportingResult(
                mc.player.getPos(), blockPos, preferredDirection, enableAirPlace, enablePositionPlace);
    }

    public static FlagEntry<BlockHitResult> getPlaceSupportingResult(
            Vec3d playerPos, BlockPos blockPos, boolean enableAirPlace, boolean enablePositionPlace) {
        return getPlaceSupportingResult(
                playerPos, blockPos, mc.player.getFacing(), enableAirPlace, enablePositionPlace);
    }

    public static FlagEntry<BlockHitResult> getPlaceSupportingResult(
            Vec3d playerPos,
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
        FlagEntry<BlockHitResult> result = null;
        BlockState currentState = mc.world.getBlockState(blockPos);
        if (enableAirPlace || (!currentState.isAir() && !currentState.isLiquid() && currentState.isReplaceable())) {
            if (checkInteractRange(blockPos, playerPos)) {
                for (Direction direction : order) {
                    Vec3d plateCenter = centerPos.offset(direction, 0.5);
                    boolean mayInteract =
                            InteractUtils.isInteractOnBlockAcceptable(mc.world, mc.player, blockPos, currentState);
                    if (checkInHead(blockPos, playerPos)) {
                        // ?
                        var re = new FlagEntry<>(
                                mayInteract, new BlockHitResult(plateCenter, direction.getOpposite(), blockPos, true));
                        if (!re.flag()) {
                            return re;
                        } else if (result == null) {
                            result = re;
                        }
                    } else {
                        if (enablePositionPlace || checkPositionPlace(blockPos, direction.getOpposite(), playerPos)) {
                            var re = new FlagEntry<>(
                                    mayInteract,
                                    new BlockHitResult(plateCenter, direction.getOpposite(), blockPos, false));
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
        for (var direction : order) {
            Vec3d plateCenter = centerPos.offset(direction, 0.5);
            Vec3d interactBlockCenter = centerPos.offset(direction, 1.0D);
            BlockPos targetPos = BlockPos.ofFloored(interactBlockCenter);
            if (!checkInteractRange(targetPos, playerPos)) {
                continue;
            }
            BlockState interactState = mc.world.getBlockState(targetPos);
            if ((interactState.isAir() || interactState.isLiquid() || interactState.isReplaceable())) {
                continue;
            }
            boolean mayInteract =
                    InteractUtils.isInteractOnBlockAcceptable(mc.world, mc.player, targetPos, interactState);

            if (checkInHead(targetPos, playerPos)) {
                // ?
                var re = new FlagEntry<>(
                        mayInteract, new BlockHitResult(plateCenter, direction.getOpposite(), targetPos, true));
                if (!re.flag()) {
                    return re;
                } else if (result == null) {
                    result = re;
                }
            } else {
                if (enablePositionPlace || checkPositionPlace(targetPos, direction.getOpposite(), playerPos)) {
                    var re = new FlagEntry<>(
                            mayInteract, new BlockHitResult(plateCenter, direction.getOpposite(), targetPos, false));
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

    @Nonnull
    public static List<FlagEntry<BlockHitResult>> getAllPlaceSupportingResult(
            Vec3d playerPos,
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
        BlockState currentState = mc.world.getBlockState(blockPos);
        if (enableAirPlace || (!currentState.isAir() && !currentState.isLiquid() && currentState.isReplaceable())) {
            if (checkInteractRange(blockPos, playerPos)) {
                for (Direction direction : order) {
                    Vec3d plateCenter = centerPos.offset(direction, 0.5);
                    boolean mayInteract =
                            InteractUtils.isInteractOnBlockAcceptable(mc.world, mc.player, blockPos, currentState);
                    if (checkInHead(blockPos, playerPos)) {
                        // ?
                        var re = new FlagEntry<>(
                                mayInteract, new BlockHitResult(plateCenter, direction.getOpposite(), blockPos, true));
                        if (!re.flag()) {
                            result.add(re);
                        }
                    } else {
                        if (enablePositionPlace || checkPositionPlace(blockPos, direction.getOpposite(), playerPos)) {
                            var re = new FlagEntry<>(
                                    mayInteract,
                                    new BlockHitResult(plateCenter, direction.getOpposite(), blockPos, false));
                            if (!re.flag()) {
                                result.add(re);
                            }
                        }
                    }
                }
            }
        }

        for (var direction : order) {
            Vec3d plateCenter = centerPos.offset(direction, 0.5);
            Vec3d interactBlockCenter = centerPos.offset(direction, 1.0D);
            BlockPos targetPos = BlockPos.ofFloored(interactBlockCenter);
            if (!checkInteractRange(targetPos, playerPos)) {
                continue;
            }
            BlockState interactState = mc.world.getBlockState(targetPos);
            if ((interactState.isAir() || interactState.isLiquid() || interactState.isReplaceable())) {
                continue;
            }
            boolean mayInteract =
                    InteractUtils.isInteractOnBlockAcceptable(mc.world, mc.player, targetPos, interactState);
            if (checkInHead(targetPos, playerPos)) {
                // ?
                result.add(new FlagEntry<>(
                        mayInteract, new BlockHitResult(plateCenter, direction.getOpposite(), targetPos, true)));
            } else {
                if (enablePositionPlace || checkPositionPlace(blockPos, direction.getOpposite(), playerPos)) {
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
        Block block = targetState.getBlock();
        if (block instanceof BedBlock && targetState.get(BedBlock.PART) == BedPart.HEAD) {
            BlockPos footPos =
                    placeTargetBlock.offset(targetState.get(BedBlock.FACING).getOpposite());
            return createSpecificStateHitResult(
                    preferredDirection,
                    footPos,
                    targetState.with(BedBlock.PART, BedPart.FOOT),
                    enableAirPlace,
                    enablePositionPlace);
        }
        if (block instanceof TallPlantBlock && targetState.get(TallPlantBlock.HALF) == DoubleBlockHalf.UPPER) {
            return createSpecificStateHitResult(
                    preferredDirection,
                    placeTargetBlock.down(),
                    targetState.with(TallPlantBlock.HALF, DoubleBlockHalf.LOWER),
                    enableAirPlace,
                    enablePositionPlace);
        }
        if (block instanceof DoorBlock && targetState.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return createSpecificStateHitResult(
                    preferredDirection,
                    placeTargetBlock.down(),
                    targetState.with(DoorBlock.HALF, DoubleBlockHalf.LOWER),
                    enableAirPlace,
                    enablePositionPlace);
        }
        if (block instanceof ScaffoldingBlock) {
            return getScaffoldingPlacement(
                    preferredDirection, placeTargetBlock, targetState, enableAirPlace, enablePositionPlace);
        }
        if (block instanceof SlabBlock) {
            return createSlabHitResult(
                    preferredDirection, placeTargetBlock, targetState, enableAirPlace, enablePositionPlace);
        }
        if (block instanceof StairsBlock) {
            return createStairsHitResult(
                    preferredDirection, placeTargetBlock, targetState, enableAirPlace, enablePositionPlace);
        }
        if (block instanceof TrapdoorBlock) {
            return createTrapdoorHitResult(
                    preferredDirection, placeTargetBlock, targetState, enableAirPlace, enablePositionPlace);
        }
        return createMainHitResult(
                preferredDirection, placeTargetBlock, targetState, enableAirPlace, enablePositionPlace);
    }

    private static FlagEntry<BlockHitResult> createStairsHitResult(
            Direction preferredDirection,
            BlockPos placeTargetBlock,
            BlockState targetState,
            boolean enableAirPlace,
            boolean enablePositionPlace) {

        Function<BlockHitResult, BlockHitResult> specificStateFilter =
                hit -> filterStairsPlacementState(placeTargetBlock, targetState, hit);
        return createPlacementHitResult(
                preferredDirection, targetState, placeTargetBlock, enableAirPlace, enablePositionPlace, (val) -> {
                    var re = specificStateFilter.apply(val);
                    if (re != null) {
                        return new FlagEntry<>(false, re);
                    }
                    return null;
                });
    }

    private static FlagEntry<BlockHitResult> createTrapdoorHitResult(
            Direction preferredDirection,
            BlockPos placeTargetBlock,
            BlockState targetState,
            boolean enableAirPlace,
            boolean enablePositionPlace) {

        Function<BlockHitResult, BlockHitResult> specificStateFilter =
                hit -> filterTrapdoorPlacementState(placeTargetBlock, targetState, hit);
        return createPlacementHitResult(
                preferredDirection, targetState, placeTargetBlock, enableAirPlace, enablePositionPlace, (val) -> {
                    var re = specificStateFilter.apply(val);
                    if (re != null) {
                        return new FlagEntry<>(false, re);
                    }
                    return null;
                });
    }

    private static FlagEntry<BlockHitResult> createSlabHitResult(
            Direction preferredDirection,
            BlockPos placeTargetBlock,
            BlockState targetState,
            boolean enableAirPlace,
            boolean enablePositionPlace) {

        Function<BlockHitResult, BlockHitResult> specificStateFilter =
                hit -> filterSlabPlacementState(placeTargetBlock, targetState, hit);
        return createPlacementHitResult(
                preferredDirection, targetState, placeTargetBlock, enableAirPlace, enablePositionPlace, (val) -> {
                    var re = specificStateFilter.apply(val);
                    if (re != null) {
                        return new FlagEntry<>(false, re);
                    }
                    return null;
                });
    }

    private static Vec3d getSlabPlacementHitPos(
            BlockPos placeTargetBlock, BlockState currentState, BlockState targetState, Direction side, Vec3d hitPos) {
        if (!side.getAxis().isHorizontal()) {
            return hitPos;
        }
        SlabType placementType = targetState.get(SlabBlock.TYPE);
        if (currentState.isOf(targetState.getBlock())) {
            SlabType currentType = currentState.get(SlabBlock.TYPE);
            if (currentType == SlabType.DOUBLE) {
                return hitPos;
            }
            placementType = currentType == SlabType.BOTTOM ? SlabType.TOP : SlabType.BOTTOM;
        }
        double hitY = placeTargetBlock.getY() + (placementType == SlabType.TOP ? 0.75 : 0.25);
        return new Vec3d(hitPos.x, hitY, hitPos.z);
    }

    private static FlagEntry<BlockHitResult> createMainHitResult(
            Direction preferredDirection,
            BlockPos placeTargetBlock,
            BlockState targetState,
            boolean enableAirPlace,
            boolean enablePositionPlace) {
        Block block = targetState.getBlock();

        Predicate<BlockHitResult> stateFilter = hit -> true;
        boolean forceSneak = false;
        if (block instanceof WallRedstoneTorchBlock) {
            Predicate<BlockHitResult> torchFilter = createTorchOrLeverPlacementFilter(placeTargetBlock, targetState);
            stateFilter = hit -> hit.getBlockPos().equals(placeTargetBlock)
                    ? matchesDirectionalPlacement(placeTargetBlock, targetState, hit)
                    : torchFilter.test(hit);
        } else if (block instanceof WallTorchBlock) {
            Predicate<BlockHitResult> torchFilter = createTorchOrLeverPlacementFilter(placeTargetBlock, targetState);
            stateFilter = hit -> hit.getBlockPos().equals(placeTargetBlock)
                    ? matchesDirectionalPlacement(placeTargetBlock, targetState, hit)
                    : torchFilter.test(hit);
        } else if (block instanceof AbstractTorchBlock || block instanceof LeverBlock) {
            stateFilter = createTorchOrLeverPlacementFilter(placeTargetBlock, targetState);
        } else if (block instanceof WallMountedBlock) {
            stateFilter = hit -> matchesDirectionalPlacement(placeTargetBlock, targetState, hit);
        } else if (block instanceof WallSkullBlock) {
            stateFilter = hit -> matchesDirectionalPlacement(placeTargetBlock, targetState, hit);
        } else if (block instanceof WallSignBlock) {
            stateFilter = hit -> matchesDirectionalPlacement(placeTargetBlock, targetState, hit);
        } else if (block instanceof WallHangingSignBlock) {
            stateFilter = hit -> matchesDirectionalPlacement(placeTargetBlock, targetState, hit);
        } else if (block instanceof WallBannerBlock) {
            stateFilter = hit -> matchesDirectionalPlacement(placeTargetBlock, targetState, hit);
        } else if (block instanceof VineBlock) {
            stateFilter = hit -> matchesDirectionalPlacement(placeTargetBlock, targetState, hit);
        } else if (block instanceof TripwireHookBlock) {
            stateFilter = hit -> matchesDirectionalPlacement(placeTargetBlock, targetState, hit);
        } else if (block instanceof DeadCoralWallFanBlock) {
            stateFilter = hit -> matchesDirectionalPlacement(placeTargetBlock, targetState, hit);
        } else if (block instanceof CocoaBlock) {
            stateFilter = hit -> matchesDirectionalPlacement(placeTargetBlock, targetState, hit);
        } else if (block instanceof MultifaceBlock) {
            stateFilter = hit -> matchesDirectionalPlacement(placeTargetBlock, targetState, hit);
        } else if (block instanceof LanternBlock) {
            stateFilter = hit -> matchesDirectionalPlacement(placeTargetBlock, targetState, hit);
        } else if (block instanceof LadderBlock) {
            stateFilter = hit -> matchesDirectionalPlacement(placeTargetBlock, targetState, hit);
        } else if (block instanceof EndRodBlock) {
            Direction targetFacing = targetState.get(EndRodBlock.FACING);
            stateFilter = hit -> hit.getSide() == targetFacing;
        } else if (block instanceof ChestBlock) {
            ChestType targetChestType = targetState.get(ChestBlock.CHEST_TYPE);
            Direction targetFacing = targetState.get(ChestBlock.FACING);
            BlockPos pos = placeTargetBlock;
            if (mc.player.shouldCancelInteraction()) {
                if (targetChestType != ChestType.SINGLE) {
                    Direction left = targetFacing.rotateYCounterclockwise();
                    Direction right = targetFacing.rotateYClockwise();
                    boolean canForm = false;
                    for (Direction direction : new Direction[] {left, right}) {
                        BlockPos neighborPos = pos.offset(direction);
                        BlockState neighborState = mc.world.getBlockState(neighborPos);
                        if (neighborState.getBlock() instanceof ChestBlock
                                && neighborState.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE
                                && neighborState.get(ChestBlock.FACING) == targetFacing) {
                            canForm = true;
                            break;
                        }
                    }
                    if (!canForm) {
                        targetChestType = ChestType.SINGLE;
                    }
                }
                if (targetChestType == ChestType.SINGLE) {
                    stateFilter = hit -> {
                        Direction side = hit.getSide();
                        if (!side.getAxis().isHorizontal()) return true;
                        BlockPos neighborPos = pos.offset(side.getOpposite());
                        BlockState neighborState = mc.world.getBlockState(neighborPos);
                        if (!(neighborState.getBlock() instanceof ChestBlock)) return true;
                        if (neighborState.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) return true;
                        Direction neighborFacing = neighborState.get(ChestBlock.FACING);
                        return neighborFacing.getAxis() == side.getAxis();
                    };
                } else {
                    final ChestType finalTargetChestType = targetChestType;
                    stateFilter = hit -> {
                        Direction side = hit.getSide();
                        if (!side.getAxis().isHorizontal()) return false;
                        BlockPos neighborPos = pos.offset(side.getOpposite());
                        BlockState neighborState = mc.world.getBlockState(neighborPos);
                        if (!(neighborState.getBlock() instanceof ChestBlock)) return false;
                        if (neighborState.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) return false;
                        Direction neighborFacing = neighborState.get(ChestBlock.FACING);
                        if (neighborFacing.getAxis() == side.getAxis()) return false;
                        ChestType finalChestType = (neighborFacing.rotateYCounterclockwise() == side.getOpposite())
                                ? ChestType.RIGHT
                                : ChestType.LEFT;
                        return neighborFacing == targetFacing && finalChestType == finalTargetChestType;
                    };
                }
            } else if (targetChestType == ChestType.SINGLE) {
                Direction left = targetFacing.rotateYCounterclockwise();
                Direction right = targetFacing.rotateYClockwise();
                for (BlockPos neighborPos : new BlockPos[] {pos.offset(left), pos.offset(right)}) {
                    BlockState neighborState = mc.world.getBlockState(neighborPos);
                    if (neighborState.getBlock() instanceof ChestBlock
                            && neighborState.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE
                            && neighborState.get(ChestBlock.FACING) == targetFacing) {
                        forceSneak = true;
                        break;
                    }
                }
            }
        } else if (block instanceof BellBlock) {
            Direction targetFacing = targetState.get(BellBlock.FACING);
            Attachment attachment = targetState.get(BellBlock.ATTACHMENT);
            stateFilter = hit -> {
                if (attachment == Attachment.FLOOR || attachment == Attachment.CEILING) {
                    if (hit.getSide() != (attachment == Attachment.FLOOR ? Direction.UP : Direction.DOWN)) {
                        return false;
                    }
                } else if (!hit.getSide().getAxis().isHorizontal() || hit.getSide() != targetFacing.getOpposite()) {
                    return false;
                }
                BlockState placed = getSpecificPlacementState(placeTargetBlock, (BlockItem) block.asItem(), hit);
                return placed != null && placed.get(BellBlock.ATTACHMENT) == attachment;
            };
        } else if (block instanceof LightningRodBlock) {
            Direction targetFacing = targetState.get(LightningRodBlock.FACING);
            stateFilter = hit -> hit.getSide() == targetFacing;
        } else if (block instanceof ShulkerBoxBlock) {
            Direction targetFacing = targetState.get(ShulkerBoxBlock.FACING);
            stateFilter = hit -> hit.getSide() == targetFacing;
        } else if (block instanceof HopperBlock) {
            Direction targetFacing = targetState.get(HopperBlock.FACING);
            if (targetFacing == Direction.DOWN) {
                stateFilter = hit -> hit.getSide() == Direction.UP || hit.getSide() == Direction.DOWN;
            } else {
                stateFilter = hit -> hit.getSide() == targetFacing.getOpposite();
            }
        } else if (block instanceof RotatedInfestedBlock) {
            Direction.Axis targetAxis = targetState.get(PillarBlock.AXIS);
            stateFilter = hit -> hit.getSide().getAxis() == targetAxis;
        } else if (block instanceof PillarBlock || block instanceof CreakingHeartBlock) {
            Direction.Axis targetAxis = targetState.get(PillarBlock.AXIS);
            stateFilter = hit -> hit.getSide().getAxis() == targetAxis;
        } else if (block instanceof JigsawBlock) {
            Orientation orientation = targetState.get(JigsawBlock.ORIENTATION);
            stateFilter = hit -> hit.getSide() == orientation.getFacing()
                    && (hit.getSide().getAxis() == Direction.Axis.Y || orientation.getRotation() == Direction.UP);
        } else if (block instanceof AmethystClusterBlock) {
            Direction targetFacing = targetState.get(AmethystClusterBlock.FACING);
            stateFilter = hit -> hit.getSide() == targetFacing;
        }
        Predicate<BlockHitResult> filter2 = stateFilter;
        boolean sneak2 = forceSneak;
        return createPlacementHitResult(
                preferredDirection, targetState, placeTargetBlock, enableAirPlace, enablePositionPlace, (fac) -> {
                    if (filter2.test(fac)) {
                        return new FlagEntry<>(sneak2, fac);
                    }
                    return null;
                });
    }

    private static FlagEntry<BlockHitResult> createPlacementHitResult(
            Direction preferredDirection,
            BlockState targetState,
            BlockPos targetPos,
            boolean enableAirPlace,
            boolean enablePositionPlace,
            Function<BlockHitResult, FlagEntry<BlockHitResult>> blockHitResultFactory) {
        Block block = targetState.getBlock();
        BlockState currentState = mc.world.getBlockState(targetPos);
        boolean canUseAirPlace = enableAirPlace && !(block instanceof AbstractTorchBlock);
        if (block instanceof WallRedstoneTorchBlock) {
            canUseAirPlace = enableAirPlace;
        } else if (block instanceof WallTorchBlock) {
            canUseAirPlace = enableAirPlace;
        }
        Vec3d centerPos = targetPos.toCenterPos();
        Vec3d playerFeetPos = mc.player.getPos();
        double interactRange = interactExtra.getBlockReachDistance();
        FlagEntry<BlockHitResult> result = null;
        boolean inside = checkInHead(targetPos, playerFeetPos);
        List<Direction> order = new ArrayList<>(6);
        order.add(preferredDirection);
        for (Direction direction : new Direction[] {
            Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
        }) {
            if (direction != preferredDirection) {
                order.add(direction);
            }
        }
        if (checkInteractRange(targetPos, playerFeetPos, interactRange)) {
            if ((!currentState.isAir() && !currentState.isLiquid()) || canUseAirPlace) {
                for (Direction direction : order) {
                    Direction side = direction.getOpposite();
                    BlockHitResult hitResult = new BlockHitResult(centerPos.offset(side, 0.5), side, targetPos, inside);
                    FlagEntry<BlockHitResult> entry = blockHitResultFactory.apply(hitResult);
                    if (entry == null) {
                        continue;
                    }
                    if (!currentState.isAir() && !currentState.isLiquid()) {
                        if (!currentState.isReplaceable()) {
                            ItemPlacementContext placementContext = new ItemPlacementContext(
                                    mc.player,
                                    Hand.MAIN_HAND,
                                    new ItemStack(targetState.getBlock().asItem()),
                                    entry.val());
                            if (!placementContext.canReplaceExisting()) {
                                continue;
                            }
                        }
                    }
                    hitResult = entry.val();
                    if (!checkInHead(hitResult.getBlockPos(), playerFeetPos)
                            && !enablePositionPlace
                            && !checkPositionPlace(hitResult.getBlockPos(), hitResult.getSide(), playerFeetPos)) {
                        continue;
                    }
                    boolean mayInteract = InteractUtils.isInteractOnBlockAcceptable(
                            mc.world,
                            mc.player,
                            hitResult.getBlockPos(),
                            mc.world.getBlockState(hitResult.getBlockPos()));
                    FlagEntry<BlockHitResult> candidate = new FlagEntry<>(mayInteract || entry.flag(), entry.val());
                    if (InteractUtils.canInteractAndPlace(mc.player, candidate)) {
                        return candidate;
                    }
                    if (result == null) {
                        result = candidate;
                    }
                }
            }
        }

        for (Direction direction : order) {
            Vec3d plateCenter = centerPos.offset(direction, 0.5);
            BlockPos neighbour = BlockPos.ofFloored(centerPos.offset(direction, 1.0D));
            if (!checkInteractRange(neighbour, playerFeetPos, interactRange)) {
                continue;
            }
            BlockState interactState = mc.world.getBlockState(neighbour);
            if (interactState.isAir() || interactState.isLiquid() || interactState.isReplaceable()) {
                continue;
            }
            boolean inHead = checkInHead(neighbour, playerFeetPos);
            BlockHitResult hitResult = new BlockHitResult(plateCenter, direction.getOpposite(), neighbour, inHead);
            FlagEntry<BlockHitResult> entry = blockHitResultFactory.apply(hitResult);
            if (entry == null) continue;
            hitResult = entry.val();
            if (!checkInHead(hitResult.getBlockPos(), playerFeetPos)
                    && !enablePositionPlace
                    && !checkPositionPlace(hitResult.getBlockPos(), hitResult.getSide(), playerFeetPos)) {
                continue;
            }
            boolean mayInteract = InteractUtils.isInteractOnBlockAcceptable(
                    mc.world, mc.player, hitResult.getBlockPos(), mc.world.getBlockState(hitResult.getBlockPos()));
            FlagEntry<BlockHitResult> candidate = new FlagEntry<>(mayInteract || entry.flag(), entry.val());
            if (InteractUtils.canInteractAndPlace(mc.player, candidate)) {
                return candidate;
            }
            if (result == null) {
                result = candidate;
            }
        }
        return result;
    }

    private static BlockHitResult filterStairsPlacementState(
            BlockPos placeTargetBlock, BlockState targetState, BlockHitResult hitResult) {
        if (!(targetState.getBlock().asItem() instanceof BlockItem blockItem)) {
            return null;
        }
        BlockState currentState = mc.world.getBlockState(placeTargetBlock);
        if (hitResult.getSide().getAxis().isHorizontal()
                && (currentState.isAir() || currentState.isLiquid() || currentState.isReplaceable())) {
            boolean topHalf = targetState.get(StairsBlock.HALF) == BlockHalf.TOP;
            double hitY = placeTargetBlock.getY() + (topHalf ? 0.75 : 0.25);
            Vec3d hitPos = hitResult.getPos();
            hitResult = new BlockHitResult(
                    new Vec3d(hitPos.x, hitY, hitPos.z),
                    hitResult.getSide(),
                    hitResult.getBlockPos(),
                    hitResult.isInsideBlock());
        }
        BlockState placementState = getSpecificPlacementState(placeTargetBlock, blockItem, hitResult);
        return placementState != null && placementState.get(StairsBlock.HALF) == targetState.get(StairsBlock.HALF)
                ? hitResult
                : null;
    }

    private static BlockHitResult filterTrapdoorPlacementState(
            BlockPos placeTargetBlock, BlockState targetState, BlockHitResult hitResult) {
        if (!(targetState.getBlock().asItem() instanceof BlockItem blockItem)) {
            return null;
        }
        BlockState currentState = mc.world.getBlockState(placeTargetBlock);
        if (hitResult.getSide().getAxis().isHorizontal()
                && (currentState.isAir() || currentState.isLiquid() || currentState.isReplaceable())) {
            boolean topHalf = targetState.get(TrapdoorBlock.HALF) == BlockHalf.TOP;
            double hitY = placeTargetBlock.getY() + (topHalf ? 0.75 : 0.25);
            Vec3d hitPos = hitResult.getPos();
            hitResult = new BlockHitResult(
                    new Vec3d(hitPos.x, hitY, hitPos.z),
                    hitResult.getSide(),
                    hitResult.getBlockPos(),
                    hitResult.isInsideBlock());
        }
        BlockState placementState = getSpecificPlacementState(placeTargetBlock, blockItem, hitResult);
        return placementState != null
                        && placementState.get(TrapdoorBlock.HALF) == targetState.get(TrapdoorBlock.HALF)
                        && (!hitResult.getSide().getAxis().isHorizontal()
                                || placementState.get(TrapdoorBlock.FACING) == targetState.get(TrapdoorBlock.FACING))
                ? hitResult
                : null;
    }

    private static BlockHitResult filterSlabPlacementState(
            BlockPos placeTargetBlock, BlockState targetState, BlockHitResult hitResult) {

        if (!(targetState.getBlock().asItem() instanceof BlockItem blockItem)) {
            return null;
        }

        BlockState currentState = mc.world.getBlockState(placeTargetBlock);
        SlabType targetType = targetState.get(SlabBlock.TYPE);
        Direction side = hitResult.getSide();
        Vec3d hitPos = hitResult.getPos();

        if (side.getAxis().isHorizontal()) {
            double hitY;

            if (hitResult.getBlockPos().equals(placeTargetBlock)
                    && currentState.isOf(targetState.getBlock())
                    && currentState.get(SlabBlock.TYPE) != SlabType.DOUBLE) {

                // canReplace(): BOTTOM requires y > 0.5,
                // TOP requires y <= 0.5.
                SlabType currentType = currentState.get(SlabBlock.TYPE);
                hitY = placeTargetBlock.getY() + (currentType == SlabType.BOTTOM ? 0.75 : 0.25);

            } else {
                // getPlacementState(): horizontal placement uses hit Y.
                hitY = placeTargetBlock.getY() + (targetType == SlabType.TOP ? 0.75 : 0.25);
            }

            hitPos = new Vec3d(hitPos.x, hitY, hitPos.z);
        }

        BlockHitResult adjusted = new BlockHitResult(hitPos, side, hitResult.getBlockPos(), hitResult.isInsideBlock());

        BlockState placementState = getSpecificPlacementState(placeTargetBlock, blockItem, adjusted);

        return placementState != null && placementState.get(SlabBlock.TYPE) == targetType ? adjusted : null;
    }

    private static BlockState getSpecificPlacementState(
            BlockPos placeTargetBlock, BlockItem blockItem, BlockHitResult hitResult) {
        ItemPlacementContext context =
                new ItemPlacementContext(mc.player, Hand.MAIN_HAND, new ItemStack(blockItem), hitResult);
        context = blockItem.getPlacementContext(context);
        return context != null && context.getBlockPos().equals(placeTargetBlock)
                ? blockItem.getPlacementState(context)
                : null;
    }

    private static FlagEntry<BlockHitResult> getScaffoldingPlacement(
            Direction preferredDirection,
            BlockPos placeTargetBlock,
            BlockState targetState,
            boolean enableAirPlace,
            boolean enablePositionPlace) {
        Block block = targetState.getBlock();
        // Holding scaffolding makes existing scaffolding replaceable, but the item extends it instead.
        if (mc.world.getBlockState(placeTargetBlock).isOf(block) || !mc.world.isInBuildLimit(placeTargetBlock)) {
            return null;
        }
        BlockItem item = (BlockItem) block.asItem();
        ItemStack stack = new ItemStack(item);
        Vec3d playerPos = mc.player.getPos();
        double range = interactExtra.getBlockReachDistance();
        List<Direction> order = new ArrayList<>(6);
        order.add(preferredDirection);
        for (Direction direction : Direction.values()) {
            if (direction != preferredDirection) {
                order.add(direction);
            }
        }
        // Match getPlaceSupportingResult: try every target hit before searching neighbors.
        BlockState currentState = mc.world.getBlockState(placeTargetBlock);
        Vec3d centerPos = placeTargetBlock.toCenterPos();
        if ((enableAirPlace || (!currentState.isAir() && !currentState.isLiquid() && currentState.isReplaceable()))
                && checkInteractRange(placeTargetBlock, playerPos, range)
                && (mc.player.shouldCancelInteraction()
                        || !InteractUtils.isInteractOnBlockAcceptable(
                                mc.world, mc.player, placeTargetBlock, currentState, stack))) {
            for (Direction direction : order) {
                Direction side = direction.getOpposite();
                BlockHitResult hit = new BlockHitResult(
                        centerPos.offset(direction, 0.5),
                        side,
                        placeTargetBlock,
                        checkInHead(placeTargetBlock, playerPos));
                if (!enablePositionPlace && !checkPositionPlace(hit)) {
                    continue;
                }
                ItemPlacementContext context = new ItemPlacementContext(mc.player, Hand.MAIN_HAND, stack, hit);
                if (!context.canReplaceExisting() || !context.canPlace()) {
                    continue;
                }
                ItemPlacementContext placement = item.getPlacementContext(context);
                if (placement != null
                        && placement.getBlockPos().equals(placeTargetBlock)
                        && item.getPlacementState(placement) != null) {
                    return new FlagEntry<>(false, hit);
                }
            }
        }
        for (Direction direction : order) {
            BlockPos interactPos = placeTargetBlock.offset(direction);
            if (!checkInteractRange(interactPos, playerPos, range)) {
                continue;
            }
            BlockState interactState = mc.world.getBlockState(interactPos);
            if (interactState.isAir()
                    || interactState.isLiquid()
                    || interactState.isReplaceable()
                    || interactState.getBlock() instanceof ScaffoldingBlock) {
                continue;
            }
            if (!mc.player.shouldCancelInteraction()
                    && InteractUtils.isInteractOnBlockAcceptable(
                            mc.world, mc.player, interactPos, interactState, stack)) {
                continue;
            }
            BlockHitResult hit = new BlockHitResult(
                    centerPos.offset(direction, 0.5),
                    direction.getOpposite(),
                    interactPos,
                    checkInHead(interactPos, playerPos));
            if (!enablePositionPlace && !checkPositionPlace(hit)) {
                continue;
            }
            ItemPlacementContext context = new ItemPlacementContext(mc.player, Hand.MAIN_HAND, stack, hit);
            if (context.canReplaceExisting()
                    || !context.getBlockPos().equals(placeTargetBlock)
                    || !context.canPlace()) {
                continue;
            }
            ItemPlacementContext placement = item.getPlacementContext(context);
            if (placement != null
                    && placement.getBlockPos().equals(placeTargetBlock)
                    && item.getPlacementState(placement) != null) {
                return new FlagEntry<>(false, hit);
            }
        }
        // Expand only along the six directions, and only through continuous scaffolding.
        for (Direction direction : order) {
            BlockPos.Mutable cursor = placeTargetBlock.mutableCopy().move(direction);
            boolean reachedInteractionRange = false;
            // Horizontal extension reaches at most seven blocks; vertical extension has no such limit.
            searchChain:
            for (int distance = 1; mc.world.isInBuildLimit(cursor); ++distance) {
                boolean withinInteractionRange = false;
                BlockState cursorState = null;
                // Try the scaffold itself, then its six neighbors with their face toward the scaffold.
                for (int hitIndex = 0; hitIndex <= order.size(); ++hitIndex) {
                    Direction neighborDirection = hitIndex == 0 ? null : order.get(hitIndex - 1);
                    BlockPos interactPos =
                            neighborDirection == null ? cursor.toImmutable() : cursor.offset(neighborDirection);
                    if (!checkInteractRange(interactPos, playerPos, range)) {
                        continue;
                    }
                    withinInteractionRange = true;
                    if (cursorState == null) {
                        cursorState = mc.world.getBlockState(cursor);
                        if (!cursorState.isOf(block)) {
                            break searchChain;
                        }
                    }
                    BlockState interactState = mc.world.getBlockState(interactPos);
                    if (neighborDirection != null) {
                        // Neighbors must be real, non-replaceable supports, never another scaffold.
                        if (interactState.isAir()
                                || interactState.isLiquid()
                                || interactState.isReplaceable()
                                || interactState.getBlock() instanceof ScaffoldingBlock) {
                            continue;
                        }
                    }
                    if (!mc.player.shouldCancelInteraction()
                            && InteractUtils.isInteractOnBlockAcceptable(
                                    mc.world, mc.player, interactPos, interactState, stack)) {
                        continue;
                    }
                    boolean inside = checkInHead(interactPos, playerPos);
                    Direction side = neighborDirection == null
                            ? preferredDirection.getOpposite()
                            : neighborDirection.getOpposite();
                    if (neighborDirection == null) {
                        Direction extension = direction.getOpposite();
                        if (mc.player.shouldCancelInteraction()) {
                            side = inside ? extension.getOpposite() : extension;
                        } else if (extension == Direction.UP) {
                            // Any face except UP extends upward; choose one facing the player's eyes.
                            Vec3d view = mc.player.getEyePos().subtract(interactPos.toCenterPos());
                            side = Direction.getFacing(view.x, Math.min(0.0, view.y), view.z);
                        } else if (extension == mc.player.getHorizontalFacing()) {
                            side = Direction.UP;
                        } else {
                            continue;
                        }
                    }
                    BlockHitResult hit =
                            new BlockHitResult(interactPos.toCenterPos().offset(side, 0.5), side, interactPos, inside);
                    if (!enablePositionPlace && !checkPositionPlace(hit)) {
                        continue;
                    }
                    ItemPlacementContext context = new ItemPlacementContext(mc.player, Hand.MAIN_HAND, stack, hit);
                    // Self hits replace the clicked block; neighbor hits must offset into the scaffold.
                    if (context.canReplaceExisting() != (neighborDirection == null)) {
                        continue;
                    }
                    // Neighbor hits must resolve to the same scaffold before the item extends it.
                    if (!context.getBlockPos().equals(cursor) || !context.canPlace()) {
                        continue;
                    }
                    // Use the current sneak state; never request sneaking to change the direction.
                    ItemPlacementContext placement = item.getPlacementContext(context);
                    if (placement == null
                            || !placement.getBlockPos().equals(placeTargetBlock)
                            || item.getPlacementState(placement) == null) {
                        continue;
                    }
                    return new FlagEntry<>(false, hit);
                }
                if (!withinInteractionRange && reachedInteractionRange) {
                    // Early-stop only after checking all seven possible interaction positions.
                    break;
                }
                reachedInteractionRange |= withinInteractionRange;
                if (direction.getAxis().isHorizontal() && distance >= ScaffoldingBlock.MAX_DISTANCE) {
                    break;
                }
                // Before entering range, the chain must still be continuous.
                if (cursorState == null && !mc.world.getBlockState(cursor).isOf(block)) {
                    break;
                }
                cursor.move(direction);
            }
        }
        return null;
    }

    private static Predicate<BlockHitResult> createTorchOrLeverPlacementFilter(
            BlockPos placeTargetBlock, BlockState targetState) {
        Block block = targetState.getBlock();
        Direction targetSide;
        if (block instanceof LeverBlock) {
            BlockFace face = targetState.get(WallMountedBlock.FACE);
            if (face == BlockFace.WALL) {
                targetSide = targetState.get(WallMountedBlock.FACING);
            } else if (face == BlockFace.FLOOR) {
                targetSide = Direction.UP;
            } else {
                targetSide = Direction.DOWN;
            }
        } else if (targetState.contains(HorizontalFacingBlock.FACING)) {
            targetSide = targetState.get(HorizontalFacingBlock.FACING);
        } else {
            targetSide = Direction.UP;
        }
        if (!targetState.canPlaceAt(mc.world, placeTargetBlock)) {
            return hit -> false;
        }
        BlockState standingTorchState =
                block instanceof AbstractTorchBlock && block.asItem() instanceof BlockItem torchItem
                        ? torchItem.getBlock().getDefaultState()
                        : null;
        for (Direction side : Direction.values()) {
            if (side == targetSide) {
                continue;
            }
            boolean canPlace;
            if (block instanceof LeverBlock) {
                BlockState placementState = block.getDefaultState();
                if (side.getAxis() == Direction.Axis.Y) {
                    placementState = placementState
                            .with(WallMountedBlock.FACE, side == Direction.UP ? BlockFace.FLOOR : BlockFace.CEILING)
                            .with(WallMountedBlock.FACING, targetState.get(WallMountedBlock.FACING));
                } else {
                    placementState = placementState
                            .with(WallMountedBlock.FACE, BlockFace.WALL)
                            .with(WallMountedBlock.FACING, side);
                }
                canPlace = placementState.canPlaceAt(mc.world, placeTargetBlock);
            } else if (side == Direction.UP) {
                canPlace = standingTorchState != null && standingTorchState.canPlaceAt(mc.world, placeTargetBlock);
            } else if (side.getAxis().isHorizontal()) {
                canPlace = WallTorchBlock.canPlaceAt(mc.world, placeTargetBlock, side);
            } else {
                canPlace = false;
            }
            if (canPlace) {
                return hit -> !hit.getBlockPos().equals(placeTargetBlock) && hit.getSide() == targetSide;
            }
        }
        return hit -> true;
    }

    private static boolean matchesDirectionalPlacement(
            BlockPos placeTargetBlock, BlockState targetState, BlockHitResult hitResult) {
        Block block = targetState.getBlock();
        ItemPlacementContext context =
                new ItemPlacementContext(mc.player, Hand.MAIN_HAND, new ItemStack(block.asItem()), hitResult);
        if (hitResult.getBlockPos().equals(placeTargetBlock)) {
            if (block instanceof WallHangingSignBlock sign
                    && (hitResult.getSide().getAxis()
                                    == targetState
                                            .get(WallHangingSignBlock.FACING)
                                            .getAxis()
                            || !sign.canAttachAt(targetState, mc.world, placeTargetBlock))) {
                return false;
            }
            return context.canReplaceExisting() && targetState.canPlaceAt(mc.world, placeTargetBlock);
        }
        if (!context.getBlockPos().equals(placeTargetBlock) || !context.canPlace()) {
            return false;
        }
        Direction side = hitResult.getSide();
        Direction direction = side.getOpposite();
        BlockState placementState = block.getDefaultState();
        if (block instanceof WallMountedBlock) {
            if (direction.getAxis() == Direction.Axis.Y) {
                // BlockRotate supplies the target horizontal facing for floor/ceiling placement.
                placementState = placementState
                        .with(WallMountedBlock.FACE, direction == Direction.UP ? BlockFace.CEILING : BlockFace.FLOOR)
                        .with(WallMountedBlock.FACING, targetState.get(WallMountedBlock.FACING));
            } else {
                placementState = placementState
                        .with(WallMountedBlock.FACE, BlockFace.WALL)
                        .with(WallMountedBlock.FACING, direction.getOpposite());
            }
            return placementState.get(WallMountedBlock.FACE) == targetState.get(WallMountedBlock.FACE)
                    && placementState.get(WallMountedBlock.FACING) == targetState.get(WallMountedBlock.FACING)
                    && placementState.canPlaceAt(mc.world, placeTargetBlock);
        }
        if (block instanceof LanternBlock) {
            if (direction.getAxis() != Direction.Axis.Y) {
                return false;
            }
            placementState = placementState.with(LanternBlock.HANGING, direction == Direction.UP);
            return placementState.get(LanternBlock.HANGING).equals(targetState.get(LanternBlock.HANGING))
                    && placementState.canPlaceAt(mc.world, placeTargetBlock);
        }
        if (block instanceof VineBlock) {
            if (direction == Direction.DOWN) {
                return false;
            }
            var property = VineBlock.FACING_PROPERTIES.get(direction);
            BlockState currentState = mc.world.getBlockState(placeTargetBlock);
            if (!targetState.get(property) || (currentState.isOf(block) && currentState.get(property))) {
                return false;
            }
            if (VineBlock.shouldConnectTo(mc.world, placeTargetBlock.offset(direction), direction)) {
                return true;
            }
            if (direction.getAxis() == Direction.Axis.Y) {
                return false;
            }
            BlockState aboveState = mc.world.getBlockState(placeTargetBlock.up());
            return aboveState.isOf(block) && aboveState.get(property);
        }
        if (block instanceof MultifaceGrowthBlock multiface) {
            var property = ConnectingBlock.FACING_PROPERTIES.get(direction);
            BlockState currentState = mc.world.getBlockState(placeTargetBlock);
            return targetState.contains(property)
                    && targetState.get(property)
                    && !(currentState.isOf(block) && currentState.get(property))
                    && multiface.withDirection(currentState, mc.world, placeTargetBlock, direction) != null;
        }
        if (block instanceof WallHangingSignBlock hangingSign) {
            // Its sign plane must be perpendicular to the clicked face's axis.
            Direction facing = targetState.get(WallHangingSignBlock.FACING);
            return side.getAxis().isHorizontal()
                    && facing.getAxis() != side.getAxis()
                    && hangingSign.canAttachTo(mc.world, targetState, hitResult.getBlockPos(), side);
        }
        if (!direction.getAxis().isHorizontal()) {
            return false;
        }
        Direction facing = block instanceof CocoaBlock ? direction : direction.getOpposite();
        if (facing != targetState.get(HorizontalFacingBlock.FACING)) {
            return false;
        }
        placementState = placementState.with(HorizontalFacingBlock.FACING, facing);
        if (block instanceof WallSkullBlock) {
            return !mc.world.getBlockState(placeTargetBlock.offset(direction)).canReplace(context);
        }
        if (block instanceof LadderBlock && !context.canReplaceExisting()) {
            BlockState supportState = mc.world.getBlockState(placeTargetBlock.offset(direction));
            if (supportState.isOf(block) && supportState.get(LadderBlock.FACING) == side) {
                return false;
            }
        }
        return placementState.canPlaceAt(mc.world, placeTargetBlock);
    }

    private static Vec3d getDirectReplacingHitPos(
            BlockPos placeTargetBlock, BlockState currentState, BlockState targetState, Direction side) {
        Vec3d hitPos = placeTargetBlock.toCenterPos().offset(side, 0.5D);
        if (!(currentState.getBlock() instanceof SlabBlock)
                || !currentState.isOf(targetState.getBlock())
                || !side.getAxis().isHorizontal()) {
            return hitPos;
        }

        SlabType currentType = currentState.get(SlabBlock.TYPE);
        if (currentType == SlabType.BOTTOM) {
            return hitPos.add(0.0D, 0.25D, 0.0D);
        }
        if (currentType == SlabType.TOP) {
            return hitPos.add(0.0D, -0.25D, 0.0D);
        }
        return hitPos;
    }

    // here we use real eyePos because this idiot water-place is calculated by server
    public static FlagEntry<Vec2f> createLiquidPlacementRaycast(Vec3d eyePos, BlockPos pos, BlockState targetState) {
        if (mc.world == null || mc.player == null) {
            return null;
        }

        boolean isLiquidState = targetState.isLiquid()
                && (targetState.getFluidState().isIn(FluidTags.WATER)
                        || targetState.getFluidState().isIn(FluidTags.LAVA));
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
            } else if (isLiquidState) {
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
            Vec3i vec3i = direction.getVector();
            if (new Vec3d(vec3i.getX(), vec3i.getY(), vec3i.getZ()).dotProduct(facingDirection) > 0) {
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

    public static BlockPos predictBlockScreenFrom(Predicate<Block> targetBlock) {
        int timeStamp = Tasks.getTick();
        // 在一秒内反应的 可以考虑
        List<UseItemOnBlock> potentialHit = getSequencedActionManager()
                .getCurrentPendingBlockPlace()
                .filter(s -> {
                    var lastInteact = s.hitResult();
                    return targetBlock.test(
                            mc.world.getBlockState(lastInteact.getBlockPos()).getBlock());
                })
                .toList();
        Optional<UseItemOnBlock> lastInteract =
                potentialHit.isEmpty() ? Optional.empty() : Optional.of(potentialHit.getLast());
        return lastInteract
                .map(UseItemOnBlock::hitResult)
                .map(BlockHitResult::getBlockPos)
                .orElse(null);
    }

    public static Entity predictEntityScreenFrom(Predicate<Entity> targetBlock) {
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
    private static SequencedActionManager sequencedActionManager;

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
    private static SelfTrap selfTrap;

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
        sequencedActionManager = new SequencedActionManager().register(m);
        guiInteract = new GuiInteract().register(m);
        autoClick = new AutoClick().register(m);
        interact = new Interact().register(m);
        scaffold = new Scaffold().register(m);
        tpInteract = new TpInteract().register(m);
        airplace = new Airplace().register(m);
        autoSurround = new AutoSurround().register(m);
        selfTrap = new SelfTrap().register(m);
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
