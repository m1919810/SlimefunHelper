package me.matl114.hacks.modules.interact;

import com.google.common.util.concurrent.Runnables;
import java.util.*;
import java.util.List;
import me.matl114.accessors.access.HitResultAccess;
import me.matl114.accessors.access.PlayerInteractBlockC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.hacks.ACTasks;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hooks.LitematicaHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.NetworkUtils;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.Orientation;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public class BlockRotate extends BaseModule {
    public final ModulePath blockRotate = makePath(Configs.INTERACT_CONFIG, "block-rotate");
    public final ModulePath blockRotateTest = blockRotate.add("test");
    public final ModulePath litematicaFix = blockRotate.add("litematica-shit-fix");

    public BlockRotate() {}

    public final FlagRef enable = flagBuilder(blockRotateTest.add("enable")).build();

    public final EnumRef<Configs.BypassMode> bypassMode = builder(
                    blockRotate.add("yaw-deceive-bypass-mode"), Configs.BypassMode.class)
            .defaultValue(Configs.BypassMode.NO_BYPASS)
            .build();

    public final EnumRef<Configs.BypassMode> bypassMode2 = builder(
                    blockRotate.add("rotate-bypass-mode"), Configs.BypassMode.class)
            .defaultValue(Configs.BypassMode.NO_BYPASS)
            .build();

    public final FlagRef enable2 = flagBuilder(litematicaFix.add("enable")).build();

    public final FlagRef legal = flagBuilder(litematicaFix.add("legal-look")).build();

    public final FlagRef enable3 =
            flagBuilder(litematicaFix.add("enable-easyplace-post-fix")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractBlockC2SPacket.class),
                this::onPreSendInteractBlockRotate);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetLoad);
    }

    public boolean enableBlockRotateModify() {
        return enable.get() || enable2.get();
    }

    // can not bypass
    public void onPreSendInteractBlockRotate(Event<PlayerInteractBlockC2SPacket> e) {
        if (enableBlockRotateModify()) {
            if (e.context instanceof PlayerInteractBlockC2SPacketAccess paccess
                    && paccess.hasUseContext()
                    && !paccess.getUseContext().isEmpty()) {
                //
                PlayerInteractBlockC2SPacketAccess.UseContext context = paccess.getUseContext();
                Item checkItem = context.stack().getItem();
                if (checkItem instanceof BlockItem blockItem) {
                    Event<Vec2f> yawDeceive = new Event<>(null, false, true);
                    Event<Vec3d> playerLookAt = new Event<>(null, false, true);
                    handlePlaceCorrectLitematica(blockItem, e.context, context, yawDeceive, playerLookAt);
                    handlePlaceCorrectDemo(blockItem, e.context, yawDeceive, playerLookAt);
                    if (yawDeceive.context != null) {
                        Vec2f py = yawDeceive.context;
                        Direction direction = EntityUtils.pitchYawToDirection(py);
                        Direction horizontal = EntityUtils.yawToHorizontalDirection(py.y);
                        Direction currentDirection = mc.player.getFacing();
                        Direction currentHorizontal = mc.player.getHorizontalFacing();
                        // optimize packet, only deceive when mismatch horizontalfacing and facing
                        if (direction != currentDirection || horizontal != currentHorizontal) {
                            // todo: check
                            if (bypassMode.get() == Configs.BypassMode.BYPASS_GRIM) {
                                // to ensure the rotate is successfully done
                                // use a wrong sequence id to ensure that this packet cancelled by grimac
                                Listener.sendPacketNoEvents(new PlayerInteractBlockC2SPacket(
                                        Hand.OFF_HAND, e.context.getBlockHitResult(), e.context.getSequence() - 1));
                            }
                            mc.getNetworkHandler()
                                    .sendPacket(new PlayerInteractItemC2SPacket(
                                            Hand.MAIN_HAND, e.context.getSequence(), py.y, py.x));
                            PlayerInteractBlockC2SPacketAccess.of(e.context)
                                    .setSequence(NetworkUtils.generateNextSequence());
                        }
                    }
                    // todo: check legacy snap
                    if (playerLookAt.context != null && bypassMode2.get().hasAc()) {
                        Vec3d lookVec = playerLookAt.context;
                        InteractionTasks.addPostRotationCorrectTask(lookVec, Runnables.doNothing());
                    }
                    if (enable3.get()
                            && LitematicaHooks.getInstance().isEnabled()
                            && LitematicaHooks.getInstance().isEasyPlaceEnabled()) {
                        // fix post
                        ACTasks.addPostTransactionAction(ch -> Listener.sendPacketNoEvents(e.context));
                        e.cancel();
                    }
                }
            }
        }
    }

    public void handlePlaceCorrectDemo(
            BlockItem item, PlayerInteractBlockC2SPacket packet, Event<Vec2f> yawDeceive, Event<Vec3d> look) {
        if (enable.get()) {
            // ?
        }
    }

    public void handlePlaceCorrectLitematica(
            BlockItem item,
            PlayerInteractBlockC2SPacket packet,
            PlayerInteractBlockC2SPacketAccess.UseContext useContext,
            Event<Vec2f> yawDeceive,
            Event<Vec3d> look) {

        if (enable2.get() && LitematicaHooks.getInstance().isEnabled()) {
            BlockHitResult packetHitResult = packet.getBlockHitResult();
            BlockState litematicaState, clientState;
            World litematicaWorld = LitematicaHooks.getInstance().getSchematicWorld();

            BlockPos modifyingBlockPos = useContext.getPlaceBlockPos(packet.getHand(), packetHitResult);
            clientState = mc.world.getBlockState(modifyingBlockPos);
            litematicaState = litematicaWorld.getBlockState(modifyingBlockPos);
            if (!litematicaState.isAir()
                    && litematicaState.getBlock().asItem() == item
                    && litematicaState.getBlock() == clientState.getBlock()) {
                // sb easy place, use illegal hitResult or shit
                if (useContext.oldState().isAir()) {
                    // handle airplace shit
                    BlockHitResult hitResult = correctEasyPlaceHitResult(packetHitResult, litematicaState);
                    // RenderTasks.debugBlockHitResult(hitResult);
                    PlayerInteractBlockC2SPacketAccess.of(packet).setBlockHitResult(hitResult);
                    packetHitResult = hitResult;
                }

                // wrong state, need correct
                if (litematicaState != clientState) {

                    BlockHitResult easyPlaceResult = LitematicaHooks.getInstance()
                            .getEasyPlaceClickedPosition(packetHitResult, litematicaState, clientState);
                    if (easyPlaceResult != null) {
                        HitResultAccess access = HitResultAccess.of(packetHitResult);
                        access.setPos(easyPlaceResult.getPos());
                    }

                    // handle direction
                    handleYawDeceive(litematicaState, yawDeceive);
                }
                if (legal.get()) {
                    look.context(packetHitResult.getBlockPos().toCenterPos());
                }
            }

            RenderTasks.debugBlockHitResult(packetHitResult);
        }
    }

    public BlockHitResult correctEasyPlaceHitResult(BlockHitResult hitResult, BlockState targetState) {
        BlockHitResult result = null;
        result = createHitResultRelatived(
                hitResult.getSide().getOpposite(), hitResult.getBlockPos(), targetState, false, false);
        return result == null ? hitResult : result;
    }

    public static BlockHitResult createHitResultRelatived(
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
                    return new BlockHitResult(interactPos, direction.getOpposite(), placeTargetBlock, false);
                }
                BlockState interactState = mc.world.getBlockState(targetPos);
                if (interactState.isAir() || interactState.isLiquid()) {
                    continue;
                }
                if (Box.from(Vec3d.of(targetPos)).contains(eyePos)) {
                    // ?
                    return new BlockHitResult(interactPos, direction.getOpposite(), targetPos, true);
                } else {
                    Vec3d iSeeVect = eyePos.subtract(plateCenter);
                    Vec3d plateLLL = Vec3d.of(direction.getVector());
                    if (enablePositionPlace || iSeeVect.dotProduct(plateLLL) < 0) {
                        return new BlockHitResult(interactPos, direction.getOpposite(), targetPos, false);
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
                    return new BlockHitResult(interactPos, direction.getOpposite(), placeTargetBlock, false);
                }
                if ((interactState.isAir() || interactState.isLiquid())) {
                    continue;
                }
                if (Box.from(Vec3d.of(targetPos)).contains(eyePos)) {
                    // ?
                    return new BlockHitResult(interactPos, direction.getOpposite(), targetPos, true);
                } else {
                    Vec3d iSeeVect = eyePos.subtract(plateCenter);
                    Vec3d plateLLL = Vec3d.of(direction.getVector());
                    if (enablePositionPlace || iSeeVect.dotProduct(plateLLL) < 0) {
                        return new BlockHitResult(interactPos, direction.getOpposite(), targetPos, false);
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
                    return new BlockHitResult(interactPos, direction.getOpposite(), placeTargetBlock, false);
                }
                if ((interactState.isAir() || interactState.isLiquid())) {
                    continue;
                }
                if (Box.from(Vec3d.of(targetPos)).contains(eyePos)) {
                    return new BlockHitResult(interactPos, direction.getOpposite(), targetPos, true);
                } else {
                    Vec3d iSeeVect = eyePos.subtract(plateCenter);
                    Vec3d plateLLL = Vec3d.of(direction.getVector());
                    if (enablePositionPlace || iSeeVect.dotProduct(plateLLL) < 0) {
                        return new BlockHitResult(interactPos, direction.getOpposite(), targetPos, false);
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
                return new BlockHitResult(plateCenter, availableDirection.getOpposite(), placeTargetBlock, false);
            } else {
                for (var direction : order) {
                    Vec3d plateCenter = centerPos.offset(direction, 0.5);
                    Vec3d interactBlockCenter = centerPos.offset(direction, 1.0D);
                    BlockPos targetPos = BlockPos.ofFloored(interactBlockCenter);
                    BlockState interactState = mc.world.getBlockState(targetPos);
                    if ((interactState.isAir() || interactState.isLiquid())) {
                        continue;
                    }
                    if (Box.from(Vec3d.of(targetPos)).contains(eyePos)) {
                        // ?
                        return new BlockHitResult(plateCenter, direction.getOpposite(), targetPos, true);
                    } else {
                        Vec3d iSeeVect = eyePos.subtract(plateCenter);
                        Vec3d plateLLL = Vec3d.of(direction.getVector());
                        if (enablePositionPlace || iSeeVect.dotProduct(plateLLL) < 0) {
                            return new BlockHitResult(plateCenter, direction.getOpposite(), targetPos, false);
                        }
                    }
                }
            }
        }
        return null;
    }

    public void onPresetLoad(Event<EventContainer<ModulePreset>> e) {
        switch (e.context.getValue()) {
            case AC_GRIM, AC_GRIM_LEGACY -> bypassMode.set(Configs.BypassMode.BYPASS_GRIM);
            default -> bypassMode.set(Configs.BypassMode.NO_BYPASS);
        }
        switch (e.context.getValue()) {
            case AC_GRIM, AC_GRIM_LEGACY -> bypassMode2.set(Configs.BypassMode.BYPASS_GRIM);
            default -> bypassMode2.set(Configs.BypassMode.NO_BYPASS);
        }
    }

    public static void handleYawDeceive(BlockState targetState, Event<Vec2f> vec2fEvent) {
        Block block = targetState.getBlock();
        if (block instanceof ObserverBlock ob) {
            vec2fEvent.context(EntityUtils.directionToPitchYaw(targetState.get(ObserverBlock.FACING)));
        }
        if (block instanceof PistonBlock ps) {
            vec2fEvent.context(EntityUtils.directionToPitchYaw(
                    targetState.get(PistonBlock.FACING).getOpposite()));
        }
        if (block instanceof DispenserBlock disp) {
            vec2fEvent.context(EntityUtils.directionToPitchYaw(
                    targetState.get(DispenserBlock.FACING).getOpposite()));
        }
        if (block instanceof BarrelBlock barrelBlock) {
            vec2fEvent.context(EntityUtils.directionToPitchYaw(
                    targetState.get(BarrelBlock.FACING).getOpposite()));
        }
        if (block instanceof CrafterBlock crafterBlock) {
            Orientation orientation = targetState.get(Properties.ORIENTATION);
            Direction facing = orientation.getFacing();
            Direction rotation = orientation.getRotation();
            switch (facing) {
                case DOWN -> {
                    vec2fEvent.context(EntityUtils.rotationToPitchYaw(
                            Vec3d.of(rotation.getVector()).add(0, -4, 0).normalize()));
                }
                case UP -> {
                    vec2fEvent.context(EntityUtils.rotationToPitchYaw(
                            Vec3d.of(rotation.getOpposite().getVector())
                                    .add(0, 4, 0)
                                    .normalize()));
                }
                default -> {
                    vec2fEvent.context(EntityUtils.rotationToPitchYaw(
                            Vec3d.of(facing.getOpposite().getVector()).normalize()));
                }
            }
        }
        float pitch = mc.player.getPitch();
        float yaw = -114514;
        if (block instanceof AbstractFurnaceBlock) {
            Direction facing = targetState.get(AbstractFurnaceBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        if (block instanceof ChiseledBookshelfBlock) {
            Direction facing = targetState.get(HorizontalFacingBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        if (block instanceof VaultBlock) {
            Direction facing = targetState.get(VaultBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        if (block instanceof LoomBlock) {
            Direction facing = targetState.get(LoomBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        if (block instanceof GlazedTerracottaBlock) {
            Direction facing = targetState.get(GlazedTerracottaBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        if (block instanceof BeehiveBlock) {
            Direction facing = targetState.get(BeehiveBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        if (block instanceof AbstractRedstoneGateBlock) {
            Direction facing = targetState.get(AbstractRedstoneGateBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        if (block instanceof StonecutterBlock) {
            Direction facing = targetState.get(StonecutterBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        // 注意：以下方块在 placement 时没有使用 getOpposite，应直接使用 facing
        if (block instanceof FenceGateBlock) {
            Direction facing = targetState.get(FenceGateBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing); // 无需取反
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        if (block instanceof DoorBlock) {
            Direction facing = targetState.get(DoorBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing); // 无需取反
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        if (block instanceof CampfireBlock) {
            Direction facing = targetState.get(CampfireBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing); // 无需取反
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        if (block instanceof DecoratedPotBlock) {
            Direction facing = targetState.get(Properties.HORIZONTAL_FACING);
            yaw = EntityUtils.rotationToYaw(facing); // 无需取反
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        if (block instanceof StairsBlock) {
            Direction facing = targetState.get(StairsBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing); // 无需取反
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        if (block instanceof CalibratedSculkSensorBlock) {
            Direction facing = targetState.get(CalibratedSculkSensorBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing); // 无需取反
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }

        // 以下方块在 placement 时使用了 getOpposite，需要取反
        if (block instanceof EnderChestBlock) {
            Direction facing = targetState.get(EnderChestBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        // version 1.21.6+
        //        if (block instanceof DriedGhastBlock) {
        //            Direction facing = targetState.get(DriedGhastBlock.FACING);
        //            yaw = EntityUtils.rotationToYaw(facing.getOpposite());
        //            vec2fEvent.context(new Vec2f(pitch, yaw));
        //            return;
        //        }
        //        if (block instanceof ShelfBlock) {
        //            Direction facing = targetState.get(ShelfBlock.FACING);
        //            yaw = EntityUtils.rotationToYaw(facing.getOpposite());
        //            vec2fEvent.context(new Vec2f(pitch, yaw));
        //            return;
        //        }
        if (block instanceof LecternBlock) {
            Direction facing = targetState.get(LecternBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        //        if (block instanceof CopperGolemStatueBlock) {
        //            Direction facing = targetState.get(CopperGolemStatueBlock.FACING);
        //            yaw = EntityUtils.rotationToYaw(facing.getOpposite());
        //            vec2fEvent.context(new Vec2f(pitch, yaw));
        //            return;
        //        }
        if (block instanceof TrapdoorBlock) {
            Direction facing = targetState.get(TrapdoorBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        if (block instanceof ChestBlock) {
            Direction facing = targetState.get(ChestBlock.FACING);
            yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
        if (block instanceof AnvilBlock) {
            Direction facing = targetState.get(AnvilBlock.FACING);
            // 原版: with(FACING, ctx.getHorizontalPlayerFacing().rotateYClockwise())
            // 因此玩家应面向 facing.rotateYCounterclockwise()
            Direction playerFacing = facing.rotateYCounterclockwise();
            yaw = EntityUtils.rotationToYaw(playerFacing);
            vec2fEvent.context(new Vec2f(pitch, yaw));
            return;
        }
    }
}
