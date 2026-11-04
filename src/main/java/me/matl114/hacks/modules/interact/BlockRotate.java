package me.matl114.hacks.modules.interact;

import com.google.common.util.concurrent.Runnables;
import javax.annotation.Nonnull;
import me.matl114.accessors.access.HitResultAccess;
import me.matl114.accessors.access.PlayerInteractBlockC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hacks.utils.RaycastUtils;
import me.matl114.hacks.utils.enums.LegalInteractMode;
import me.matl114.hooks.LitematicaHooks;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.*;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.Orientation;
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
        InteractionTasks.interactBlock(hand, result, swingHand);
        switch (mode) {
            case LEGACY_SLIENT_ROT -> {
                if (!directlyHit) {
                    LegacySnapRotManager.INSTANCE.snapAt(
                            InteractionTasks.createBlockRayCastDirection(result.getBlockPos()), true);
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
                if (paccess.getUseContext().blockPlace()) {
                    PlayerInteractBlockC2SPacketAccess.UseContext context = paccess.getUseContext();
                    Item blockItem = context.stack().getItem();
                    handlePlaceCorrectLitematica(blockItem, e.context, context, yawDeceive, playerLookAt);
                } else if (paccess.getUseContext().isAccepted()) {
                    BlockState oldState = paccess.getUseContext().oldState();
                    BlockPos interactState = e.context.getBlockHitResult().getBlockPos();
                    BlockState newState = mc.world.getBlockState(interactState);
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
            Item item,
            PlayerInteractBlockC2SPacket packet,
            PlayerInteractBlockC2SPacketAccess.UseContext useContext,
            Event<PitchYawDeceive> yawDeceive,
            Event<LegalInteractMode> look) {

        if (enableLitematicaFix.get() && LitematicaHooks.getInstance().isEnabled()) {
            BlockHitResult packetHitResult = packet.getBlockHitResult();
            BlockState litematicaState;
            World litematicaWorld = LitematicaHooks.getInstance().getSchematicWorld();

            BlockPos modifyingBlockPos = useContext.getPlaceBlockPos(packet.getHand(), packetHitResult);
            if (!LitematicaHooks.getInstance().isPositionWithinRange(modifyingBlockPos)) return;

            litematicaState = litematicaWorld.getBlockState(modifyingBlockPos);
            BlockHitResult newPacketHitResult = handlePlaceCorrect(
                    item,
                    modifyingBlockPos,
                    litematicaState,
                    packet,
                    useContext,
                    useContext.oldState().isAir() && !enableAirPlace.get());
            if (newPacketHitResult != null) {
                // wrong state, need correct
                packetHitResult = newPacketHitResult;
                BlockState clientState = mc.world.getBlockState(modifyingBlockPos);
                if (litematicaState != clientState) {
                    BlockHitResult easyPlaceResult = LitematicaHooks.getInstance()
                            .getEasyPlaceClickedPosition(packetHitResult, litematicaState, clientState);
                    if (easyPlaceResult != null) {
                        HitResultAccess access = HitResultAccess.of(packetHitResult);
                        access.setPos(easyPlaceResult.getPos());
                    }
                }
                yawDeceive.context = handleYawDeceive(
                        litematicaState,
                        PlayerStateManager.INSTANCE.lastServerSidePitch,
                        PlayerStateManager.INSTANCE.lastServerSideYaw);
                if ((look1.get().isLegal()
                                && !RaycastUtils.canRaycastHit(
                                        mc.player,
                                        PlayerStateManager.INSTANCE.lastPitch,
                                        PlayerStateManager.INSTANCE.lastYaw,
                                        packetHitResult.getBlockPos(),
                                        InteractExtra.INSTANCE.getBlockReachDistance()))
                        || yawDeceive.context != null) {
                    look.context(look1.get());
                }
            }
            RenderTasks.debugBlockHitResult(packetHitResult);
        }
    }

    public void handleInteractCorrectLitematica(
            BlockPos pos, BlockState newState, Event<PitchYawDeceive> yawDeceive, Event<LegalInteractMode> look) {
        if (enableLitematicaFix.get() && LitematicaHooks.getInstance().isEnabled()) {
            World litematicaWorld = LitematicaHooks.getInstance().getSchematicWorld();
            if (!LitematicaHooks.getInstance().isPositionWithinRange(pos)) return;
            BlockState litematicaState = litematicaWorld.getBlockState(pos);

            if (litematicaState.getBlock() == newState.getBlock() && litematicaState != newState) {
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
                    || yawDeceive.context != null) {
                look.context(look1.get());
            }
        }
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

    public BlockHitResult handlePlaceCorrect(
            Item item,
            BlockPos modifyingBlockPos,
            BlockState targetState,
            PlayerInteractBlockC2SPacket packet,
            PlayerInteractBlockC2SPacketAccess.UseContext useContext,
            boolean forceCorrect) {
        BlockHitResult packetHitResult = packet.getBlockHitResult();
        BlockState clientState = mc.world.getBlockState(modifyingBlockPos);
        if (!targetState.isAir()
                && targetState.getBlock().asItem() == item
                && targetState.getBlock() == clientState.getBlock()) {
            // sb easy place, use illegal hitResult or shit
            if (forceCorrect) {
                // handle airplace shit
                BlockHitResult hitResult = correctEasyPlaceHitResult(
                        packetHitResult, modifyingBlockPos, targetState, useContext.oldPlaceState());
                // RenderTasks.debugBlockHitResult(hitResult);
                PlayerInteractBlockC2SPacketAccess.of(packet).setBlockHitResult(hitResult);
                packetHitResult = hitResult;
            }
            return packetHitResult;
        }
        return null;
    }

    public BlockHitResult correctEasyPlaceHitResult(
            BlockHitResult hitResult, BlockPos placingPos, BlockState targetState, BlockState oldPlaceState) {
        BlockState placedState = mc.world.getBlockState(placingPos);
        try {
            if (placedState != oldPlaceState) {
                mc.world.setBlockState(placingPos, oldPlaceState, WorldUtils.UPDATE_BLOCK_NO_PHYSICS);
            }
            var result = InteractionTasks.createSpecificStateHitResult(
                    hitResult.getSide().getOpposite(), placingPos, targetState, enableAirPlace.get(), false);
            return result == null ? hitResult : result.val();
        } finally {
            if (placedState != oldPlaceState) {
                mc.world.setBlockState(placingPos, placedState, WorldUtils.UPDATE_BLOCK_NO_PHYSICS);
            }
        }
    }

    private static PitchYawDeceive handleYawDeceive(
            @Nonnull BlockState targetState, float currentServerPitch, float currentServerYaw) {
        Block block = targetState.getBlock();
        PitchYawDeceive deceive = new PitchYawDeceive();

        if (block instanceof WallMountedBlock) {
            if (targetState.get(WallMountedBlock.FACE) == BlockFace.FLOOR
                    || targetState.get(WallMountedBlock.FACE) == BlockFace.CEILING) {
                Direction direction = targetState.get(WallMountedBlock.FACING);
                deceive.yaw = EntityUtils.directionToPitchYaw(direction).y;
                deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
                return deceive;
            }
        }

        if (block instanceof ObserverBlock) {
            Direction facing = targetState.get(ObserverBlock.FACING);
            Vec2f pitchYaw = EntityUtils.directionToPitchYaw(facing);
            deceive.pitch = pitchYaw.x;
            deceive.yaw = pitchYaw.y;

            if (EntityUtils.pitchYawToDirection(new Vec2f(currentServerPitch, currentServerYaw)) == facing) {
                deceive.pitchForce = false;
                deceive.yawForce = false;
            }
            return deceive;
        }

        if (block instanceof PistonBlock) {
            Direction facing = targetState.get(PistonBlock.FACING).getOpposite();
            Vec2f pitchYaw = EntityUtils.directionToPitchYaw(facing);
            deceive.pitch = pitchYaw.x;
            deceive.yaw = pitchYaw.y;

            if (EntityUtils.pitchYawToDirection(new Vec2f(currentServerPitch, currentServerYaw)) == facing) {
                deceive.pitchForce = false;
                deceive.yawForce = false;
            }
            return deceive;
        }

        if (block instanceof DispenserBlock) {
            Direction facing = targetState.get(DispenserBlock.FACING).getOpposite();
            Vec2f pitchYaw = EntityUtils.directionToPitchYaw(facing);
            deceive.pitch = pitchYaw.x;
            deceive.yaw = pitchYaw.y;

            if (EntityUtils.pitchYawToDirection(new Vec2f(currentServerPitch, currentServerYaw)) == facing) {
                deceive.pitchForce = false;
                deceive.yawForce = false;
            }
            return deceive;
        }

        if (block instanceof BarrelBlock) {
            Direction facing = targetState.get(BarrelBlock.FACING).getOpposite();
            Vec2f pitchYaw = EntityUtils.directionToPitchYaw(facing);
            deceive.pitch = pitchYaw.x;
            deceive.yaw = pitchYaw.y;

            if (EntityUtils.pitchYawToDirection(new Vec2f(currentServerPitch, currentServerYaw)) == facing) {
                deceive.pitchForce = false;
                deceive.yawForce = false;
            }
            return deceive;
        }

        if (block instanceof CrafterBlock) {
            Orientation orientation = targetState.get(Properties.ORIENTATION);
            Direction facing = orientation.getFacing();
            Direction rotation = orientation.getRotation();
            Vec2f pitchYaw;

            switch (facing) {
                case DOWN -> pitchYaw = EntityUtils.rotationToPitchYaw(
                        Vec3d.of(rotation.getOpposite().getVector())
                                .add(0, 4, 0)
                                .normalize());
                case UP -> pitchYaw = EntityUtils.rotationToPitchYaw(
                        Vec3d.of(rotation.getVector()).add(0, -4, 0).normalize());
                default -> pitchYaw = EntityUtils.rotationToPitchYaw(
                        Vec3d.of(facing.getOpposite().getVector()).normalize());
            }

            deceive.pitch = pitchYaw.x;
            deceive.yaw = pitchYaw.y;

            Direction currentFacing = EntityUtils.pitchYawToDirection(new Vec2f(currentServerPitch, currentServerYaw))
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
            deceive.yaw = EntityUtils.rotationToYaw(
                    targetState.get(AbstractFurnaceBlock.FACING).getOpposite());
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof ChiseledBookshelfBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(
                    targetState.get(HorizontalFacingBlock.FACING).getOpposite());
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof VaultBlock) {
            deceive.yaw =
                    EntityUtils.rotationToYaw(targetState.get(VaultBlock.FACING).getOpposite());
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof LoomBlock) {
            deceive.yaw =
                    EntityUtils.rotationToYaw(targetState.get(LoomBlock.FACING).getOpposite());
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof GlazedTerracottaBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(
                    targetState.get(GlazedTerracottaBlock.FACING).getOpposite());
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof BeehiveBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(
                    targetState.get(BeehiveBlock.FACING).getOpposite());
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof AbstractRedstoneGateBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(
                    targetState.get(AbstractRedstoneGateBlock.FACING).getOpposite());
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof StonecutterBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(
                    targetState.get(StonecutterBlock.FACING).getOpposite());
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof FenceGateBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(targetState.get(FenceGateBlock.FACING));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof DoorBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(targetState.get(DoorBlock.FACING));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof BedBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(targetState.get(BedBlock.FACING));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof CampfireBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(targetState.get(CampfireBlock.FACING));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof DecoratedPotBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(targetState.get(Properties.HORIZONTAL_FACING));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof StairsBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(targetState.get(StairsBlock.FACING));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof CalibratedSculkSensorBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(targetState.get(CalibratedSculkSensorBlock.FACING));
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof EnderChestBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(
                    targetState.get(EnderChestBlock.FACING).getOpposite());
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof DriedGhastBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(
                    targetState.get(DriedGhastBlock.FACING).getOpposite());
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof ShelfBlock) {
            deceive.yaw =
                    EntityUtils.rotationToYaw(targetState.get(ShelfBlock.FACING).getOpposite());
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof LecternBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(
                    targetState.get(LecternBlock.FACING).getOpposite());
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof CopperGolemStatueBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(
                    targetState.get(CopperGolemStatueBlock.FACING).getOpposite());
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof TrapdoorBlock) {
            deceive.yaw = EntityUtils.rotationToYaw(
                    targetState.get(TrapdoorBlock.FACING).getOpposite());
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof ChestBlock) {
            deceive.yaw =
                    EntityUtils.rotationToYaw(targetState.get(ChestBlock.FACING).getOpposite());
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        if (block instanceof AnvilBlock) {
            Direction facing = targetState.get(AnvilBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing.rotateYCounterclockwise());
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        return null;
    }

    public static PitchYawDeceive handleYawInteractDeceive(
            BlockState targetState, float currentServerPitch, float currentServerYaw) {
        Block block = targetState.getBlock();

        if (block instanceof FenceGateBlock) {
            PitchYawDeceive deceive = new PitchYawDeceive();
            Direction facing = targetState.get(FenceGateBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing);
            deceive.yawForce = Math.abs(MathHelper.wrapDegrees(currentServerYaw - deceive.yaw)) >= 45.0F;
            return deceive;
        }

        return null;
    }

    private static class PitchYawDeceive {
        Float pitch = null;
        Float yaw = null;
        boolean pitchForce = true;
        boolean yawForce = true;

        public boolean hasDeceive() {
            return pitch != null || yaw != null;
        }

        public boolean isForce() {
            return (pitch != null && pitchForce) || (yaw != null && yawForce);
        }

        public float getPitch(float currentPitch) {
            return pitch != null && pitchForce ? pitch : currentPitch;
        }

        public float getYaw(float currentYaw) {
            return yaw != null && yawForce ? yaw : currentYaw;
        }
    }
}
