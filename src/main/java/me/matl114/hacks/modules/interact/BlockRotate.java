package me.matl114.hacks.modules.interact;

import com.google.common.util.concurrent.Runnables;
import javax.annotation.Nonnull;
import me.matl114.accessors.access.PendingUpdateManagerAccess;
import me.matl114.accessors.access.PlayerInteractBlockC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.modules.move.RotationManager;
import me.matl114.hacks.utils.*;
import me.matl114.hacks.utils.enums.LegalInteractMode;
import me.matl114.hacks.utils.move.Rotation;
import me.matl114.hooks.LitematicaHooks;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.Orientation;
import net.minecraft.block.enums.RailShape;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public class BlockRotate extends BaseModule {
    public final ModulePath blockRotate = makePath(Configs.INTERACT_CONFIG, "block-rotate");
    public final ModulePath yawDeceiveSettings = blockRotate.add("yaw-deceive-settings");
    public final ModulePath litematicaFix = blockRotate.add("litematica-shit-fix");
    public final ModulePath placeCorrect = blockRotate.add("simple-placement-correct");
    public static BlockRotate INSTANCE;

    public BlockRotate() {
        super("BlockRotate");
        INSTANCE = this;
    }

    public final FlagRef yawPacketOrderFix =
            flagBuilder(yawDeceiveSettings.add("yaw-packet-order-fix"), true).build();

    public final FlagRef enableLitematicaFix =
            flagBuilder(litematicaFix.addEnable()).build();

    public final EnumRef<LegalInteractMode> look1 = builder(litematicaFix.add("rotate-mode"), LegalInteractMode.class)
            .defaultValue(LegalInteractMode.LEGACY_SLIENT_ROT)
            .build();

    public final FlagRef enableAirPlace =
            flagBuilder(litematicaFix.add("enable-air-place")).build();

    public final FlagRef enablePlaceCorrect =
            flagBuilder(placeCorrect.addEnable()).build();

    public final EnumRef<LegalInteractMode> look2 = builder(placeCorrect.add("rotate-mode"), LegalInteractMode.class)
            .defaultValue(LegalInteractMode.LEGACY_SLIENT_ROT)
            .build();

    //    public final FlagRef enable3 =
    //            flagBuilder(litematicaFix.add("enable-easyplace-post-fix")).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(
                Listener.getPacketPoint().getChannel(PlayerInteractBlockC2SPacket.class),
                this::onPreSendInteractBlockRotate);
        registerListener(Listener.getCustomListener().getChannel(ModulePreset.class), this::onPresetLoad);
    }

    public void onPresetLoad(Event<EventContainer<ModulePreset>> event) {
        ModulePreset preset = event.context.getValue();
        if (preset.isAirplacePreset()) enableAirPlace.set(preset.getAirplace(enableAirPlace.get()));
        if (preset.isOptionPreset()) return;
        switch (preset) {
            case AC_GRIM_LEGACY -> {
                look1.set(LegalInteractMode.LEGACY_SLIENT_ROT);
                look2.set(LegalInteractMode.LEGACY_SLIENT_ROT);
            }
            case AC_GRIM, AC_MATRIX -> {
                look1.set(LegalInteractMode.DELAY_MOVEMENT);
                look2.set(LegalInteractMode.DELAY_MOVEMENT);
            }
            default -> {
                look1.set(LegalInteractMode.NONE);
                look2.set(LegalInteractMode.NONE);
            }
        }
    }

    public boolean handleYawDeceivePlace(
            LegalInteractMode mode,
            BlockHitResult result,
            Hand hand,
            @Nonnull BlockState targetState,
            boolean swingHand) {
        PitchYawDeceive deceive = getPlaceYawDeceive(result, targetState);
        if (deceive == null) {
            return InteractionTasks.handlePlaceMode(mode, result, hand, swingHand);
        }
        return handleYawDeceiveMode(mode, result, hand, swingHand, deceive);
    }

    private boolean handleYawDeceiveMode(
            LegalInteractMode mode, BlockHitResult result, Hand hand, boolean swingHand, PitchYawDeceive deceive) {
        if (deceive == null || !deceive.hasDeceive()) {
            return InteractionTasks.handlePlaceMode(mode, result, hand, swingHand);
        }
        // DO NOT USE handlePlaceMode,
        // because it may send rotation before place
        // but isForce() is base on current, pitch and yaw
        if (deceive.isForce()) {
            boolean legacy = ViaFabricPlusHooks.isSupportDupRot() || mode.canMultiRotPlace();
            sendYawDeceiveRotation(legacy, result, hand, deceive);
        }
        // use the rotation after the yawDeceive, grim doesn't consider high version useItem rotation into rayCast
        boolean directlyHit = InteractionTasks.canDirectHit(result);
        if (!directlyHit && !mode.isIn(LegalInteractMode.NONE)) {
            RotationManager.INSTANCE.startQueuePacket();
        }
        InteractionTasks.interactBlock(hand, result, swingHand);
        switch (mode) {
            case LEGACY_SLIENT_ROT -> {
                if (!directlyHit) {
                    LegacySnapRotManager.INSTANCE.snapAt(
                            InteractionTasks.createBlockRayCastDirection(result.getBlockPos()), true);
                    RotationManager.INSTANCE.flushQueuePackets();
                }
                return true;
            }
            case DELAY_MOVEMENT, MOVEMENT_POST -> {
                if (!directlyHit) {
                    InteractionTasks.addPostRotationCorrectTask(result.getBlockPos(), Runnables.doNothing());
                    return false;
                }
                return true;
            }
            case NONE -> {
                return true;
            }
        }
        return true;
    }

    public boolean hasPlaceYawDeceive(BlockHitResult result, @Nonnull BlockState targetState) {
        var deceive = getPlaceYawDeceive(result, targetState);
        return deceive != null && deceive.hasDeceive();
    }

    private PitchYawDeceive getPlaceYawDeceive(BlockHitResult result, @Nonnull BlockState targetState) {
        if (checkNull()) return null;

        BlockPos interactPos = result.getBlockPos();
        BlockState interactState = mc.world.getBlockState(interactPos);
        if (!mc.player.shouldCancelInteraction()
                && InteractUtils.isInteractOnBlockAcceptable(mc.world, mc.player, interactPos, interactState)) {
            return handleYawInteractDeceive(
                    targetState,
                    PlayerStateManager.INSTANCE.lastServerSidePitch,
                    PlayerStateManager.INSTANCE.lastServerSideYaw);
        }

        return handleYawDeceive(
                result,
                targetState,
                PlayerStateManager.INSTANCE.lastServerSidePitch,
                PlayerStateManager.INSTANCE.lastServerSideYaw);
    }

    private void sendYawDeceiveRotation(boolean legacy, BlockHitResult result, Hand hand, PitchYawDeceive deceive) {
        float pitch = deceive.getPitch(PlayerStateManager.INSTANCE.lastServerSidePitch);
        float yaw = deceive.getYaw(PlayerStateManager.INSTANCE.lastServerSideYaw);
        if (legacy) {
            LegacySnapRotManager.INSTANCE.snapAt(pitch, yaw, true);
            return;
        }
        // may place
        Hand oppoHand = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
        boolean isInteractable = mc.player.getStackInHand(oppoHand).getItem() instanceof BlockItem;
        if (isInteractable) {
            int sequence = NetworkUtils.generateNextSequence();
            if (yawPacketOrderFix.get()) {
                Listener.sendPacketNoEvents(new PlayerInteractBlockC2SPacket(oppoHand, result, sequence - 1));
            }
            mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(hand, sequence, yaw, pitch));
        } else {
            if (yawPacketOrderFix.get()) {
                Listener.sendPacketNoEvents(
                        new PlayerInteractBlockC2SPacket(oppoHand, result, NetworkUtils.generateNextSequence()));
            }
            mc.getNetworkHandler()
                    .sendPacket(new PlayerInteractItemC2SPacket(hand, NetworkUtils.generateNextSequence(), yaw, pitch));
        }
    }

    // can not bypass
    public void onPreSendInteractBlockRotate(Event<PlayerInteractBlockC2SPacket> e) {
        if (e.isCancelled()) return;
        if (e.context instanceof PlayerInteractBlockC2SPacketAccess paccess) {
            PitchYawDeceive deceivePy = null;
            BlockPos lookRot = null;
            LegalInteractMode rotateMode = null;
            if (paccess.hasUseContext()) {
                Event<PitchYawDeceive> yawDeceive = new Event<>(new PitchYawDeceive(), false, true);
                Event<LegalInteractMode> playerLookAt = new Event<>(null, false, true);
                if (paccess.getUseContext().isAccepted()
                        && paccess.getUseContext().placePos().isPresent()) {
                    handlePlaceCorrectLitematica(paccess.getUseContext(), e.context, yawDeceive, playerLookAt);
                } else if (paccess.getUseContext().isAccepted()) {
                    BlockPos interactState = e.context.getBlockHitResult().getBlockPos();
                    BlockState newState = mc.world.getBlockState(interactState);
                    BlockState oldState = PendingUpdateManagerAccess.of(mc.world.getPendingUpdateManager())
                            .getPendingBlockState(e.context.getSequence(), interactState)
                            .orElse(newState);
                    if (oldState != newState) {
                        // handle yaw fix
                        handleInteractCorrectLitematica(interactState, newState, yawDeceive, playerLookAt);
                    }
                }

                handleSimpleInteractCorrect(e.context.getBlockHitResult().getBlockPos(), playerLookAt);
                if (yawDeceive.context != null && yawDeceive.context.hasDeceive()) {
                    deceivePy = yawDeceive.context;
                }
                if (playerLookAt.context != null) {
                    lookRot = e.context.getBlockHitResult().getBlockPos();
                    rotateMode = playerLookAt.context;
                }
            }

            if (deceivePy != null && deceivePy.hasDeceive() && deceivePy.isForce()) {
                NetworkUtils.restoreSequence(e.context.getSequence());
                boolean legacy =
                        ViaFabricPlusHooks.isSupportDupRot() || (rotateMode == null || rotateMode.canMultiRotPlace());
                sendYawDeceiveRotation(legacy, e.context.getBlockHitResult(), e.context.getHand(), deceivePy);
                paccess.setSequence(NetworkUtils.generateNextSequence());
            }
            if (lookRot != null) {
                LegalInteractMode finalMode = rotateMode == null
                        ? LegalInteractMode.NONE
                        : (rotateMode == LegalInteractMode.MOVEMENT_POST
                                ? LegalInteractMode.DELAY_MOVEMENT
                                : rotateMode);
                BlockPos lookRot0 = lookRot;
                PacketManager.schedulePostScheduleCallback(e.context, () -> {
                    switch (finalMode) {
                        case LEGACY_SLIENT_ROT -> LegacySnapRotManager.INSTANCE.snapAt(
                                InteractionTasks.createBlockRayCastDirection(lookRot0), false);
                        case DELAY_MOVEMENT -> InteractionTasks.addPostRotationCorrectTask(
                                lookRot0, Runnables.doNothing());
                    }
                });
            }
        }
    }

    public void handlePlaceCorrectLitematica(
            PlayerInteractBlockC2SPacketAccess.UseContext useContext,
            PlayerInteractBlockC2SPacket packet,
            Event<PitchYawDeceive> yawDeceive,
            Event<LegalInteractMode> look) {
        if (!enableLitematicaFix.get() || !LitematicaHooks.getInstance().isEnabled()) {
            return;
        }
        BlockPos modifyingBlockPos = useContext.placePos().orElseThrow();
        if (!LitematicaHooks.getInstance().isPositionWithinRange(modifyingBlockPos)) return;

        Item item = useContext.stack().getItem();
        BlockState placedState = mc.world.getBlockState(modifyingBlockPos);
        BlockState litematicaState =
                LitematicaHooks.getInstance().getSchematicWorld().getBlockState(modifyingBlockPos);
        if (litematicaState.getBlock() != placedState.getBlock()
                || matchesExceptToleratedProperties(litematicaState, placedState)
                || litematicaState.getBlock().asItem() != item) return;

        WorldUtils.predictActionAtSequence(packet.getSequence(), () -> {
            var specificHit = InteractionTasks.createSpecificStateHitResult(
                    packet.getBlockHitResult().getSide().getOpposite(),
                    modifyingBlockPos,
                    litematicaState,
                    enableAirPlace.get(),
                    false);
            if (InteractUtils.canInteractAndPlace(mc.player, specificHit)) {
                PlayerInteractBlockC2SPacketAccess.of(packet).setBlockHitResult(specificHit.val());
            }
            yawDeceive.context = handleYawDeceive(
                    packet.getBlockHitResult(),
                    litematicaState,
                    PlayerStateManager.INSTANCE.lastServerSidePitch,
                    PlayerStateManager.INSTANCE.lastServerSideYaw);
            if ((look1.get().isLegal()
                            && !RaycastUtils.canRaycastHit(
                                    mc.player,
                                    PlayerStateManager.INSTANCE.lastPitch,
                                    PlayerStateManager.INSTANCE.lastYaw,
                                    packet.getBlockHitResult().getBlockPos(),
                                    InteractExtra.INSTANCE.getBlockReachDistance()))
                    || (yawDeceive.context != null
                            && yawDeceive.context.hasDeceive()
                            && yawDeceive.context.isForce())) {
                look.context(look1.get());
            }
        });
    }

    public void handleInteractCorrectLitematica(
            BlockPos pos, BlockState newState, Event<PitchYawDeceive> yawDeceive, Event<LegalInteractMode> look) {
        if (enableLitematicaFix.get() && LitematicaHooks.getInstance().isEnabled()) {
            World litematicaWorld = LitematicaHooks.getInstance().getSchematicWorld();
            if (!LitematicaHooks.getInstance().isPositionWithinRange(pos)) return;
            BlockState litematicaState = litematicaWorld.getBlockState(pos);

            if (litematicaState.getBlock() == newState.getBlock()
                    && !matchesExceptToleratedProperties(litematicaState, newState)) {
                yawDeceive.context = handleYawInteractDeceive(
                        litematicaState,
                        PlayerStateManager.INSTANCE.lastServerSidePitch,
                        PlayerStateManager.INSTANCE.lastServerSideYaw);
            }
            if ((look1.get().isLegal()
                            && !RaycastUtils.canRaycastHit(
                                    mc.player,
                                    PlayerStateManager.INSTANCE.lastPitch,
                                    PlayerStateManager.INSTANCE.lastYaw,
                                    pos,
                                    InteractExtra.INSTANCE.getBlockReachDistance()))
                    || (yawDeceive.context != null
                            && yawDeceive.context.hasDeceive()
                            && yawDeceive.context.isForce())) {
                look.context(look1.get());
            }
        }
    }

    private static boolean matchesExceptToleratedProperties(BlockState targetState, BlockState clientState) {
        if (targetState.getBlock() != clientState.getBlock()) return false;
        if (targetState.contains(Properties.WATERLOGGED)) {
            clientState = clientState.with(Properties.WATERLOGGED, targetState.get(Properties.WATERLOGGED));
        }
        if (targetState.contains(Properties.POWERED)) {
            clientState = clientState.with(Properties.POWERED, targetState.get(Properties.POWERED));
        }
        if (targetState.contains(Properties.POWER)) {
            clientState = clientState.with(Properties.POWER, targetState.get(Properties.POWER));
        }
        return targetState == clientState;
    }

    public void handleSimpleInteractCorrect(BlockPos interactPos, Event<LegalInteractMode> look) {
        if (enablePlaceCorrect.get()
                && look2.get().isLegal()
                && look.context == null
                && !RaycastUtils.canRaycastHit(
                        mc.player,
                        PlayerStateManager.INSTANCE.lastPitch,
                        PlayerStateManager.INSTANCE.lastYaw,
                        interactPos,
                        InteractExtra.INSTANCE.getBlockReachDistance())) {
            look.context(look2.get());
        }
    }

    private static PitchYawDeceive handleYawDeceive(
            BlockHitResult hitResult,
            @Nonnull BlockState targetState,
            float currentServerPitch,
            float currentServerYaw) {
        Block block = targetState.getBlock();
        PitchYawDeceive deceive = new PitchYawDeceive();
        boolean replacing = new ItemPlacementContext(
                        mc.player, Hand.MAIN_HAND, new ItemStack(block.asItem()), hitResult)
                .canReplaceExisting();

        Direction placementDirection = block instanceof WallHangingSignBlock
                ? targetState.get(WallHangingSignBlock.FACING).getOpposite()
                : (replacing ? getReplacingSupportDirection(hitResult, targetState) : null);
        if (placementDirection != null) {
            deceive.rotation = Rotation.from(EntityUtils.directionToPitchYaw(placementDirection));
            deceive.pitchForce =
                    EntityUtils.pitchYawToDirection(currentServerPitch, currentServerYaw) != placementDirection;
            deceive.yawForce = deceive.pitchForce;
            return deceive;
        }

        if (block instanceof WallMountedBlock) {
            if (targetState.get(WallMountedBlock.FACE) == BlockFace.FLOOR
                    || targetState.get(WallMountedBlock.FACE) == BlockFace.CEILING) {
                Direction direction = targetState.get(WallMountedBlock.FACING);
                deceive.rotation = deceive.rotation.withYaw(EntityUtils.rotationToYaw(direction));
                deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
                if (replacing) {
                    Direction support =
                            targetState.get(WallMountedBlock.FACE) == BlockFace.FLOOR ? Direction.DOWN : Direction.UP;
                    deceive.rotation =
                            deceive.rotation.withPitch(Rotation.from(EntityUtils.directionToPitchYaw(support))
                                    .pitch());
                    deceive.pitchForce =
                            EntityUtils.pitchYawToDirection(currentServerPitch, currentServerYaw) != support;
                }
                return deceive;
            }
        }

        if (block instanceof AbstractRailBlock rail) {
            RailShape shape = targetState.get(rail.getShapeProperty());
            Direction direction =
                    switch (shape) {
                        case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> Direction.NORTH;
                        case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> Direction.EAST;
                        default -> null;
                    };
            return direction == null ? null : horizontalDeceive(direction, currentServerYaw);
        }

        if (block instanceof BigDripleafBlock) {
            BlockPos placePos = new ItemPlacementContext(
                            mc.player, Hand.MAIN_HAND, new ItemStack(block.asItem()), hitResult)
                    .getBlockPos();
            BlockState below = mc.world.getBlockState(placePos.down());
            if (below.getBlock() instanceof BigDripleafBlock || below.getBlock() instanceof BigDripleafStemBlock) {
                return null;
            }
            return horizontalDeceive(targetState.get(BigDripleafBlock.FACING).getOpposite(), currentServerYaw);
        }

        if (block instanceof BellBlock) {
            if (hitResult.getSide().getAxis() == Direction.Axis.Y) {
                return horizontalDeceive(targetState.get(BellBlock.FACING), currentServerYaw);
            }
            return null;
        }

        if (block instanceof CarvedPumpkinBlock
                || block instanceof EndPortalFrameBlock
                || block instanceof SmallDripleafBlock) {
            return horizontalDeceive(
                    targetState.get(Properties.HORIZONTAL_FACING).getOpposite(), currentServerYaw);
        }

        if (block instanceof JigsawBlock && hitResult.getSide().getAxis() == Direction.Axis.Y) {
            return horizontalDeceive(
                    targetState.get(JigsawBlock.ORIENTATION).getRotation().getOpposite(), currentServerYaw);
        }

        if (block instanceof CommandBlock) {
            Direction look = targetState.get(CommandBlock.FACING).getOpposite();
            deceive.rotation = Rotation.from(EntityUtils.directionToPitchYaw(look));
            deceive.pitchForce = EntityUtils.pitchYawToDirection(currentServerPitch, currentServerYaw) != look;
            deceive.yawForce = deceive.pitchForce;
            return deceive;
        }

        if (block instanceof ObserverBlock) {
            Direction facing = targetState.get(ObserverBlock.FACING);
            deceive.rotation = Rotation.from(EntityUtils.directionToPitchYaw(facing));

            if (EntityUtils.pitchYawToDirection(currentServerPitch, currentServerYaw) == facing) {
                deceive.pitchForce = false;
                deceive.yawForce = false;
            }
            return deceive;
        }

        if (block instanceof PistonBlock) {
            Direction facing = targetState.get(PistonBlock.FACING).getOpposite();
            deceive.rotation = Rotation.from(EntityUtils.directionToPitchYaw(facing));

            if (EntityUtils.pitchYawToDirection(currentServerPitch, currentServerYaw) == facing) {
                deceive.pitchForce = false;
                deceive.yawForce = false;
            }
            return deceive;
        }

        if (block instanceof DispenserBlock) {
            Direction facing = targetState.get(DispenserBlock.FACING).getOpposite();
            deceive.rotation = Rotation.from(EntityUtils.directionToPitchYaw(facing));

            if (EntityUtils.pitchYawToDirection(currentServerPitch, currentServerYaw) == facing) {
                deceive.pitchForce = false;
                deceive.yawForce = false;
            }
            return deceive;
        }

        if (block instanceof BarrelBlock) {
            Direction facing = targetState.get(BarrelBlock.FACING).getOpposite();
            deceive.rotation = Rotation.from(EntityUtils.directionToPitchYaw(facing));

            if (EntityUtils.pitchYawToDirection(currentServerPitch, currentServerYaw) == facing) {
                deceive.pitchForce = false;
                deceive.yawForce = false;
            }
            return deceive;
        }

        if (block instanceof CrafterBlock) {
            Orientation orientation = targetState.get(Properties.ORIENTATION);
            Direction facing = orientation.getFacing();
            Direction rotation = orientation.getRotation();
            Rotation pitchYaw;

            switch (facing) {
                case DOWN -> pitchYaw = Rotation.from(EntityUtils.rotationToPitchYaw(
                        Vec3d.of(rotation.getOpposite().getVector())
                                .add(0, 4, 0)
                                .normalize()));
                case UP -> pitchYaw = Rotation.from(EntityUtils.rotationToPitchYaw(
                        Vec3d.of(rotation.getVector()).add(0, -4, 0).normalize()));
                default -> pitchYaw = Rotation.from(EntityUtils.rotationToPitchYaw(
                        Vec3d.of(facing.getOpposite().getVector()).normalize()));
            }

            deceive.rotation = pitchYaw;

            Direction currentFacing = EntityUtils.pitchYawToDirection(currentServerPitch, currentServerYaw)
                    .getOpposite();

            Direction horizontalFacing = EntityUtils.yawToHorizontalDirection(currentServerYaw);

            Direction currentRotation =
                    switch (currentFacing) {
                        case DOWN -> horizontalFacing.getOpposite();
                        case UP -> horizontalFacing;
                        default -> Direction.UP;
                    };

            boolean matches = currentFacing == facing && currentRotation == rotation;
            deceive.pitchForce = !matches;
            deceive.yawForce = !matches;

            return deceive;
        }

        if (block instanceof AbstractFurnaceBlock) {
            deceive.rotation = deceive.rotation.withYaw(EntityUtils.rotationToYaw(
                    targetState.get(AbstractFurnaceBlock.FACING).getOpposite()));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof ChiseledBookshelfBlock) {
            deceive.rotation = deceive.rotation.withYaw(EntityUtils.rotationToYaw(
                    targetState.get(HorizontalFacingBlock.FACING).getOpposite()));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof VaultBlock) {
            deceive.rotation = deceive.rotation.withYaw(
                    EntityUtils.rotationToYaw(targetState.get(VaultBlock.FACING).getOpposite()));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof LoomBlock) {
            deceive.rotation = deceive.rotation.withYaw(
                    EntityUtils.rotationToYaw(targetState.get(LoomBlock.FACING).getOpposite()));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof GlazedTerracottaBlock) {
            deceive.rotation = deceive.rotation.withYaw(EntityUtils.rotationToYaw(
                    targetState.get(GlazedTerracottaBlock.FACING).getOpposite()));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof BeehiveBlock) {
            deceive.rotation = deceive.rotation.withYaw(EntityUtils.rotationToYaw(
                    targetState.get(BeehiveBlock.FACING).getOpposite()));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof AbstractRedstoneGateBlock) {
            deceive.rotation = deceive.rotation.withYaw(EntityUtils.rotationToYaw(
                    targetState.get(AbstractRedstoneGateBlock.FACING).getOpposite()));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof StonecutterBlock) {
            deceive.rotation = deceive.rotation.withYaw(EntityUtils.rotationToYaw(
                    targetState.get(StonecutterBlock.FACING).getOpposite()));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof FenceGateBlock) {
            deceive.rotation =
                    deceive.rotation.withYaw(EntityUtils.rotationToYaw(targetState.get(FenceGateBlock.FACING)));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof DoorBlock) {
            deceive.rotation = deceive.rotation.withYaw(EntityUtils.rotationToYaw(targetState.get(DoorBlock.FACING)));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof BedBlock) {
            deceive.rotation = deceive.rotation.withYaw(EntityUtils.rotationToYaw(targetState.get(BedBlock.FACING)));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof CampfireBlock) {
            deceive.rotation =
                    deceive.rotation.withYaw(EntityUtils.rotationToYaw(targetState.get(CampfireBlock.FACING)));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof DecoratedPotBlock) {
            deceive.rotation =
                    deceive.rotation.withYaw(EntityUtils.rotationToYaw(targetState.get(Properties.HORIZONTAL_FACING)));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof StairsBlock) {
            deceive.rotation = deceive.rotation.withYaw(EntityUtils.rotationToYaw(targetState.get(StairsBlock.FACING)));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof CalibratedSculkSensorBlock) {
            deceive.rotation = deceive.rotation.withYaw(
                    EntityUtils.rotationToYaw(targetState.get(CalibratedSculkSensorBlock.FACING)));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof EnderChestBlock) {
            deceive.rotation = deceive.rotation.withYaw(EntityUtils.rotationToYaw(
                    targetState.get(EnderChestBlock.FACING).getOpposite()));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof LecternBlock) {
            deceive.rotation = deceive.rotation.withYaw(EntityUtils.rotationToYaw(
                    targetState.get(LecternBlock.FACING).getOpposite()));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof TrapdoorBlock) {
            if (hitResult.getSide().getAxis().isHorizontal() && !replacing) {
                return null;
            }
            deceive.rotation = deceive.rotation.withYaw(EntityUtils.rotationToYaw(
                    targetState.get(TrapdoorBlock.FACING).getOpposite()));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof ChestBlock) {
            deceive.rotation = deceive.rotation.withYaw(
                    EntityUtils.rotationToYaw(targetState.get(ChestBlock.FACING).getOpposite()));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        if (block instanceof AnvilBlock) {
            Direction facing = targetState.get(AnvilBlock.FACING);
            deceive.rotation = deceive.rotation.withYaw(EntityUtils.rotationToYaw(facing.rotateYCounterclockwise()));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        return null;
    }

    private static PitchYawDeceive horizontalDeceive(Direction direction, float currentServerYaw) {
        PitchYawDeceive deceive = new PitchYawDeceive();
        deceive.rotation = deceive.rotation.withYaw(EntityUtils.rotationToYaw(direction));
        deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
        return deceive;
    }

    private static Direction getReplacingSupportDirection(BlockHitResult hitResult, BlockState state) {
        Block block = state.getBlock();
        BlockPos placePos = hitResult.getBlockPos();
        BlockState currentState = mc.world.getBlockState(placePos);
        if (block instanceof WallMountedBlock) {
            return state.get(WallMountedBlock.FACE) == BlockFace.WALL
                    ? state.get(WallMountedBlock.FACING).getOpposite()
                    : null;
        }
        if (block instanceof LanternBlock) {
            return state.get(LanternBlock.HANGING) ? Direction.UP : Direction.DOWN;
        }
        if (block instanceof VineBlock) {
            BlockState aboveState = mc.world.getBlockState(placePos.up());
            for (Direction direction : Direction.values()) {
                var property = VineBlock.FACING_PROPERTIES.get(direction);
                if (property != null
                        && state.get(property)
                        && !(currentState.isOf(block) && currentState.get(property))
                        && (VineBlock.shouldConnectTo(mc.world, placePos.offset(direction), direction)
                                || (direction.getAxis().isHorizontal()
                                        && aboveState.isOf(block)
                                        && aboveState.get(property)))) return direction;
            }
            return null;
        }
        if (block instanceof MultifaceBlock multiface) {
            for (Direction direction : Direction.values()) {
                var property = ConnectingBlock.FACING_PROPERTIES.get(direction);
                if (state.contains(property)
                        && state.get(property)
                        && multiface.withDirection(currentState, mc.world, placePos, direction) != null)
                    return direction;
            }
            return null;
        }
        if (block instanceof CocoaBlock) {
            return state.get(CocoaBlock.FACING);
        }
        if (block instanceof WallRedstoneTorchBlock) {
            return state.get(WallRedstoneTorchBlock.FACING).getOpposite();
        }
        if (block instanceof WallTorchBlock) {
            return state.get(WallTorchBlock.FACING).getOpposite();
        }
        if (block instanceof WallSkullBlock) {
            return state.get(WallSkullBlock.FACING).getOpposite();
        }
        if (block instanceof WallSignBlock) {
            return state.get(WallSignBlock.FACING).getOpposite();
        }
        if (block instanceof WallBannerBlock) {
            return state.get(WallBannerBlock.FACING).getOpposite();
        }
        if (block instanceof TripwireHookBlock) {
            return state.get(TripwireHookBlock.FACING).getOpposite();
        }
        if (block instanceof DeadCoralWallFanBlock) {
            return state.get(DeadCoralWallFanBlock.FACING).getOpposite();
        }
        if (block instanceof LadderBlock) {
            return state.get(LadderBlock.FACING).getOpposite();
        }
        return null;
    }

    public static PitchYawDeceive handleYawInteractDeceive(
            BlockState targetState, float currentServerPitch, float currentServerYaw) {
        Block block = targetState.getBlock();

        if (block instanceof FenceGateBlock) {
            PitchYawDeceive deceive = new PitchYawDeceive();
            Direction facing = targetState.get(FenceGateBlock.FACING);
            deceive.rotation = deceive.rotation.withYaw(EntityUtils.rotationToYaw(facing));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.rotation.yaw())) >= 45.0F;
            return deceive;
        }

        return null;
    }

    private static class PitchYawDeceive {
        Rotation rotation = Rotation.EMPTY;
        boolean pitchForce = true;
        boolean yawForce = true;

        public boolean hasDeceive() {
            return rotation.hasRotate();
        }

        public boolean isForce() {
            return (rotation.hasPitch() && pitchForce) || (rotation.hasYaw() && yawForce);
        }

        public float getPitch(float currentPitch) {
            return rotation.hasPitch() && pitchForce ? rotation.getPitch() : currentPitch;
        }

        public float getYaw(float currentYaw) {
            return rotation.hasYaw() && yawForce ? rotation.getYaw() : currentYaw;
        }
    }
}
