package me.matl114.hacks.modules.interact;

import com.google.common.util.concurrent.Runnables;
import me.matl114.accessors.access.HitResultAccess;
import me.matl114.accessors.access.PlayerInteractBlockC2SPacketAccess;
import me.matl114.events.Event;
import me.matl114.events.EventContainer;
import me.matl114.events.Listener;
import me.matl114.events.PacketManager;
import me.matl114.hacks.InteractionTasks;
import me.matl114.hacks.RenderTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.api.ModulePreset;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hooks.LitematicaHooks;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.managers.Configs;
import me.matl114.managers.config.EnumRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.EntityUtils;
import me.matl114.utils.NetworkUtils;
import net.minecraft.block.*;
import net.minecraft.block.enums.Orientation;
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

    public boolean enableBlockRotateModify() {
        return enable.get() || enable2.get();
    }

    // can not bypass
    public void onPreSendInteractBlockRotate(Event<PlayerInteractBlockC2SPacket> e) {
        if (e.isCancelled()) return;
        if (enableBlockRotateModify()) {
            if (e.context instanceof PlayerInteractBlockC2SPacketAccess paccess
                    && paccess.hasUseContext()
                    && !paccess.getUseContext().isEmpty()) {
                //
                PlayerInteractBlockC2SPacketAccess.UseContext context = paccess.getUseContext();
                Item checkItem = context.stack().getItem();
                if (checkItem instanceof BlockItem blockItem) {
                    Event<PitchYawDeceive> yawDeceive = new Event<>(new PitchYawDeceive(), false, true);
                    Event<Vec3d> playerLookAt = new Event<>(null, false, true);
                    handlePlaceCorrectLitematica(blockItem, e.context, context, yawDeceive, playerLookAt);
                    handlePlaceCorrectDemo(blockItem, e.context, yawDeceive, playerLookAt);
                    PitchYawDeceive deceivePy = null;
                    Vec2f currentPy =
                            new Vec2f(PlayerStateManager.INSTANCE.lastPitch, PlayerStateManager.INSTANCE.lastYaw);
                    if (yawDeceive.context != null && yawDeceive.context.hasDeceive()) {
                        deceivePy = yawDeceive.context;
                        //                        PitchYawDeceive py = yawDeceive.context;
                        //                        if (py.pitch != null && py.yaw != null) {
                        //                            Vec2f py2 = new Vec2f(py.pitch, py.yaw);
                        //                            Direction direction = EntityUtils.pitchYawToDirection(py2);
                        //                            Direction currentDirection =
                        // EntityUtils.pitchYawToDirection(currentPy);
                        //                            Direction horizontal =
                        // EntityUtils.yawToHorizontalDirection(py.yaw);
                        //                            Direction currentHorizontal =
                        // EntityUtils.yawToHorizontalDirection(currentPy.y);
                        //                            if (direction != currentDirection || horizontal !=
                        // currentHorizontal) {
                        //                                deceivePy = py;
                        //                            }
                        //                        } else if (py.yaw != null) {
                        //                            Direction horizontal =
                        // EntityUtils.yawToHorizontalDirection(py.yaw);
                        //                            Direction currentHorizontal =
                        // EntityUtils.yawToHorizontalDirection(currentPy.y);
                        //                            if (horizontal != currentHorizontal) {
                        //                                deceivePy = py;
                        //                            }
                        //                        } else if (py.pitch != null) {
                        //                            boolean upper = py.pitch > 0;
                        //                            boolean meUpper = currentPy.x > 0;
                        //                            if (upper != meUpper) {
                        //                                deceivePy = py;
                        //                            }
                        //                        }
                    }
                    if (deceivePy != null
                            && ViaFabricPlusHooks.getInstance()
                                    .getCurrentVersion()
                                    .isHigherOrEqualTo(21, 0)) {
                        if (bypassMode.get() == Configs.BypassMode.BYPASS_GRIM) {
                            // to ensure the rotate is successfully done
                            // use a wrong sequence id to ensure that this packet cancelled by grimac
                            Listener.sendPacketNoEvents(new PlayerInteractBlockC2SPacket(
                                    Hand.OFF_HAND, e.context.getBlockHitResult(), e.context.getSequence() - 1));
                        }
                        mc.getNetworkHandler()
                                .sendPacket(new PlayerInteractItemC2SPacket(
                                        Hand.MAIN_HAND,
                                        e.context.getSequence(),
                                        deceivePy.getYaw(currentPy.y),
                                        deceivePy.getPitch(currentPy.x)));
                        PlayerInteractBlockC2SPacketAccess.of(e.context)
                                .setSequence(NetworkUtils.generateNextSequence());
                    }
                    if (deceivePy != null
                            && ViaFabricPlusHooks.getInstance()
                                    .getCurrentVersion()
                                    .isLowerOrEqualTo(20, 8)) {
                        LegacySnapRotManager.INSTANCE.snapAt(
                                deceivePy.getPitch(currentPy.x), deceivePy.getYaw(currentPy.y), true);
                    }
                    // todo: check legacy snap
                    if (playerLookAt.context != null
                            && (bypassMode2.get().hasAc()
                                    || (deceivePy != null
                                            && ViaFabricPlusHooks.getInstance()
                                                    .getCurrentVersion()
                                                    .isLowerOrEqualTo(20, 8)))) {
                        Vec3d lookVec = playerLookAt.context;
                        if (ViaFabricPlusHooks.isSupportDupRot()) {
                            var packet =
                                    LegacySnapRotManager.INSTANCE.createSnapAt(lookVec.subtract(mc.player.getEyePos()));
                            PacketManager.schedulePostSendPacket(e.context, packet);
                        } else {
                            InteractionTasks.addPostRotationCorrectTask(lookVec, Runnables.doNothing());
                        }
                    }
                    //                    if (enable3.get()
                    //                            && LitematicaHooks.getInstance().isEnabled()
                    //                            && LitematicaHooks.getInstance().isEasyPlaceEnabled()) {
                    //                        // fix post
                    //                        ACTasks.addPostTransactionAction(ch -> {
                    //                            Listener.sendPacketNoEvents(e.context);
                    //                        });
                    //                        e.cancel();
                    //                    }
                }
            }
        }
    }

    public void handlePlaceCorrectDemo(
            BlockItem item, PlayerInteractBlockC2SPacket packet, Event<PitchYawDeceive> yawDeceive, Event<Vec3d> look) {
        if (enable.get()) {
            // ?
        }
    }

    public void handlePlaceCorrectLitematica(
            BlockItem item,
            PlayerInteractBlockC2SPacket packet,
            PlayerInteractBlockC2SPacketAccess.UseContext useContext,
            Event<PitchYawDeceive> yawDeceive,
            Event<Vec3d> look) {

        if (enable2.get() && LitematicaHooks.getInstance().isEnabled()) {
            BlockHitResult packetHitResult = packet.getBlockHitResult();
            BlockState litematicaState, clientState;
            World litematicaWorld = LitematicaHooks.getInstance().getSchematicWorld();

            BlockPos modifyingBlockPos = useContext.getPlaceBlockPos(packet.getHand(), packetHitResult);
            if (!LitematicaHooks.getInstance().isPositionWithinRange(modifyingBlockPos)) return;
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
                }
                // handle direction
                handleYawDeceive(litematicaState, yawDeceive.context);
                if (legal.get()) {
                    look.context(packetHitResult.getBlockPos().toCenterPos());
                }
            }

            RenderTasks.debugBlockHitResult(packetHitResult);
        }
    }

    public BlockHitResult correctEasyPlaceHitResult(BlockHitResult hitResult, BlockState targetState) {

        var result = InteractionTasks.createSpecificStateHitResult(
                hitResult.getSide().getOpposite(), hitResult.getBlockPos(), targetState, false, false);
        return result == null ? hitResult : result.val();
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

    public static void handleYawDeceive(BlockState targetState, PitchYawDeceive deceive) {
        Block block = targetState.getBlock();
        if (block instanceof ObserverBlock ob) {
            Vec2f pitchYaw = EntityUtils.directionToPitchYaw(targetState.get(ObserverBlock.FACING));
            deceive.pitch = pitchYaw.x;
            deceive.yaw = pitchYaw.y;
            return;
        }
        if (block instanceof PistonBlock ps) {
            Vec2f pitchYaw = EntityUtils.directionToPitchYaw(
                    targetState.get(PistonBlock.FACING).getOpposite());
            deceive.pitch = pitchYaw.x;
            deceive.yaw = pitchYaw.y;
            return;
        }
        if (block instanceof DispenserBlock disp) {
            Vec2f pitchYaw = EntityUtils.directionToPitchYaw(
                    targetState.get(DispenserBlock.FACING).getOpposite());
            deceive.pitch = pitchYaw.x;
            deceive.yaw = pitchYaw.y;
            return;
        }
        if (block instanceof BarrelBlock barrelBlock) {
            Vec2f pitchYaw = EntityUtils.directionToPitchYaw(
                    targetState.get(BarrelBlock.FACING).getOpposite());
            deceive.pitch = pitchYaw.x;
            deceive.yaw = pitchYaw.y;
            return;
        }
        if (block instanceof CrafterBlock crafterBlock) {
            Orientation orientation = targetState.get(Properties.ORIENTATION);
            Direction facing = orientation.getFacing();
            Direction rotation = orientation.getRotation();
            Vec2f pitchYaw;
            switch (facing) {
                case DOWN -> {
                    pitchYaw = EntityUtils.rotationToPitchYaw(
                            Vec3d.of(rotation.getVector()).add(0, -4, 0).normalize());
                }
                case UP -> {
                    pitchYaw = EntityUtils.rotationToPitchYaw(
                            Vec3d.of(rotation.getOpposite().getVector())
                                    .add(0, 4, 0)
                                    .normalize());
                }
                default -> {
                    pitchYaw = EntityUtils.rotationToPitchYaw(
                            Vec3d.of(facing.getOpposite().getVector()).normalize());
                }
            }
            deceive.pitch = pitchYaw.x;
            deceive.yaw = pitchYaw.y;
            return;
        }

        // 以下分支只修改 yaw，保持玩家当前 pitch，因此只赋值 deceive.yaw
        if (block instanceof AbstractFurnaceBlock) {
            Direction facing = targetState.get(AbstractFurnaceBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            return;
        }
        if (block instanceof ChiseledBookshelfBlock) {
            Direction facing = targetState.get(HorizontalFacingBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            return;
        }
        if (block instanceof VaultBlock) {
            Direction facing = targetState.get(VaultBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            return;
        }
        if (block instanceof LoomBlock) {
            Direction facing = targetState.get(LoomBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            return;
        }
        if (block instanceof GlazedTerracottaBlock) {
            Direction facing = targetState.get(GlazedTerracottaBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            return;
        }
        if (block instanceof BeehiveBlock) {
            Direction facing = targetState.get(BeehiveBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            return;
        }
        if (block instanceof AbstractRedstoneGateBlock) {
            Direction facing = targetState.get(AbstractRedstoneGateBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            return;
        }
        if (block instanceof StonecutterBlock) {
            Direction facing = targetState.get(StonecutterBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            return;
        }
        // 以下方块不需要取反
        if (block instanceof FenceGateBlock) {
            Direction facing = targetState.get(FenceGateBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing);
            return;
        }
        if (block instanceof DoorBlock) {
            Direction facing = targetState.get(DoorBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing);
            return;
        }
        if (block instanceof CampfireBlock) {
            Direction facing = targetState.get(CampfireBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing);
            return;
        }
        if (block instanceof DecoratedPotBlock) {
            Direction facing = targetState.get(Properties.HORIZONTAL_FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing);
            return;
        }
        if (block instanceof StairsBlock) {
            Direction facing = targetState.get(StairsBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing);
            return;
        }
        if (block instanceof CalibratedSculkSensorBlock) {
            Direction facing = targetState.get(CalibratedSculkSensorBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing);
            return;
        }
        // 需要取反的分支
        if (block instanceof EnderChestBlock) {
            Direction facing = targetState.get(EnderChestBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            return;
        }
        //        if (block instanceof DriedGhastBlock) {
        //            Direction facing = targetState.get(DriedGhastBlock.FACING);
        //            deceive.yaw = EntityUtils.rotationToYaw(facing.getOpposite());
        //            return;
        //        }
        //        if (block instanceof ShelfBlock) {
        //            Direction facing = targetState.get(ShelfBlock.FACING);
        //            deceive.yaw = EntityUtils.rotationToYaw(facing.getOpposite());
        //            return;
        //        }
        if (block instanceof LecternBlock) {
            Direction facing = targetState.get(LecternBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            return;
        }
        //        if (block instanceof CopperGolemStatueBlock) {
        //            Direction facing = targetState.get(CopperGolemStatueBlock.FACING);
        //            deceive.yaw = EntityUtils.rotationToYaw(facing.getOpposite());
        //            return;
        //        }
        if (block instanceof TrapdoorBlock) {
            Direction facing = targetState.get(TrapdoorBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            return;
        }
        if (block instanceof ChestBlock) {
            Direction facing = targetState.get(ChestBlock.FACING);
            deceive.yaw = EntityUtils.rotationToYaw(facing.getOpposite());
            return;
        }
        if (block instanceof AnvilBlock) {
            Direction facing = targetState.get(AnvilBlock.FACING);
            Direction playerFacing = facing.rotateYCounterclockwise();
            deceive.yaw = EntityUtils.rotationToYaw(playerFacing);
            return;
        }
    }

    public static class PitchYawDeceive {
        Float pitch = null;
        Float yaw = null;

        public boolean hasDeceive() {
            return pitch != null || yaw != null;
        }

        public float getPitch(float currentPitch) {
            return pitch != null ? pitch : currentPitch;
        }

        public float getYaw(float currentYaw) {
            return yaw != null ? yaw : currentYaw;
        }
    }
}
