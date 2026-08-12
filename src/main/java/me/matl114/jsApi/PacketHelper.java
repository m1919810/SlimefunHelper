package me.matl114.jsApi;

import java.util.List;
import java.util.Objects;
import me.matl114.accessors.access.PlayerInteractBlockC2SPacketAccess;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Listener;
import me.matl114.hacks.InvTasks;
import me.matl114.utils.ApiMethod;
import me.matl114.utils.InventoryUtils;
import me.matl114.utils.NetworkUtils;
import me.matl114.utils.RaycastUtils;
import me.matl114.versioned.api.VPacket;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PacketType;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

@ApiMethod
public class PacketHelper {
    static MinecraftClient mc = MinecraftClient.getInstance();

    public static Class<? extends Packet<?>> getPacketType(String packetType, boolean s2c) {
        Identifier id = Identifier.tryParse(packetType);
        return Listener.getPacketClassById(id, s2c);
    }

    public static void sendPacket(Packet<?> packet) {
        mc.getNetworkHandler().sendPacket(packet);
    }

    public static List<String> getAllPacketTypes() {
        return Listener.getRegisteredPacketTypes().keySet().stream()
                .map(PacketType::id)
                .map(Identifier::toString)
                .toList();
    }

    public static int generateSequenceId() {
        return NetworkUtils.generateNextSequence();
    }

    public static void sendInventoryPacket(int slotId, int button, Object actionTypeStr) {
        InvTasks.clickSlotAsync(slotId, button, JsHelper.toEnum(actionTypeStr, SlotActionType.class));
    }

    public static int getLastServerScreenSyncId() {
        return InvTasks.LAST_SYNC_ID;
    }

    public static void sendCloseInventory(int syncId) {
        mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(syncId));
    }

    public static void sendAttackBlock(int x, int y, int z, Object direction) {
        sendAttackBlock(new BlockPos(x, y, z), direction);
    }

    public static void sendAttackBlock(Object pos, Object direction) {
        Direction dir = JsHelper.toEnum(direction, Direction.class);
        BlockPos blockPos = DataHelper.createBlockPos(pos);
        BlockState blockState;
        if (mc.player.getAbilities().creativeMode) {
            mc.interactionManager.sendSequencedPacket(mc.world, (sequence) -> {
                mc.execute(() -> mc.interactionManager.breakBlock(blockPos));
                return new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, blockPos, dir, sequence);
            });
        } else {
            blockState = mc.world.getBlockState(blockPos);
            float speed = blockState.calcBlockBreakingDelta(mc.player, mc.world, blockPos);
            boolean canInstaMine = speed > 1.0F;
            if (canInstaMine) {
                mc.interactionManager.sendSequencedPacket(
                        mc.world,
                        (sequence -> new PlayerActionC2SPacket(
                                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, blockPos, dir, sequence)));
                mc.execute(() -> mc.interactionManager.breakBlock(blockPos));
            } else if (!Objects.equals(
                    blockPos, PlayerInteractionAccess.of(mc.interactionManager).getCurrentMiningPos())) {
                if (mc.interactionManager.isBreakingBlock()) {
                    mc.getNetworkHandler()
                            .sendPacket(new PlayerActionC2SPacket(
                                    PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                                    PlayerInteractionAccess.of(mc.interactionManager)
                                            .getCurrentMiningPos(),
                                    dir));
                }
                PlayerInteractionAccess.of(mc.interactionManager).startMiningBlock(blockPos, dir);
            }
        }
    }

    private static void syncHotbar() {

        PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(InventoryUtils.getSelectedSlot());
    }

    public static void sendInteractBlock(int x, int y, int z, Object direction, boolean offhand) {
        sendInteractBlock(new BlockPos(x, y, z), direction, offhand);
    }

    public static void sendInteractBlock(Object pos, Object direction, boolean offhand) {
        Direction dir = JsHelper.toEnum(direction, Direction.class);
        BlockPos blockPos = DataHelper.createBlockPos(pos);
        BlockHitResult hitResult = RaycastUtils.createHitResult(blockPos, dir);
        syncHotbar();
        mc.interactionManager.sendSequencedPacket(mc.world, (seq) -> {
            var packet = new PlayerInteractBlockC2SPacket(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND, hitResult, seq);
            if (packet instanceof PlayerInteractBlockC2SPacketAccess access) {
                access.setUseContext(new PlayerInteractBlockC2SPacketAccess.UseContext(
                        mc.player
                                .getStackInHand(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND)
                                .copy(),
                        mc.world.getBlockState(hitResult.getBlockPos()),
                        ActionResult.SUCCESS));
            }
            return packet;
        });
    }

    public static void sendInteractEntity(Object entity, boolean offhand) {
        sendInteractEntity(entity, mc.player.isSneaking(), offhand);
    }

    public static void sendInteractEntity(Object entity, boolean sneaking, boolean offhand) {
        int s;
        if (entity instanceof Integer integer) {
            s = integer.intValue();
        } else {
            Entity e = JsHelper.unwrap(entity, Entity.class);
            s = e.getId();
        }
        syncHotbar();
        mc.interactionManager.sendSequencedPacket(mc.world, (seq) -> {
            return new PlayerInteractEntityC2SPacket(
                    s,
                    sneaking,
                    new PlayerInteractEntityC2SPacket.InteractHandler(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND));
        });
    }

    public static void sendInteractItem(boolean offHand) {
        sendInteractItem(offHand, mc.player.getPitch(), mc.player.getYaw());
    }

    public static void sendInteractItem(boolean offHand, float pitch, float yaw) {
        syncHotbar();
        mc.interactionManager.sendSequencedPacket(mc.world, (seq) -> {
            return new PlayerInteractItemC2SPacket(offHand ? Hand.OFF_HAND : Hand.MAIN_HAND, seq, yaw, pitch);
        });
    }

    public static void sendInteractEntityAt(Object entity, Object pos, boolean sneaking, boolean offhand) {
        int s;
        if (entity instanceof Integer integer) {
            s = integer.intValue();
        } else {
            Entity e = JsHelper.unwrap(entity, Entity.class);
            s = e.getId();
        }
        Vec3d p = DataHelper.createVec(pos);
        syncHotbar();
        mc.interactionManager.sendSequencedPacket(mc.world, (seq) -> {
            return new PlayerInteractEntityC2SPacket(
                    s,
                    sneaking,
                    new PlayerInteractEntityC2SPacket.InteractAtHandler(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND, p));
        });
    }

    public static void sendSwingHand(boolean offhand) {
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND));
    }

    public static void startMine(Object pos, Object direction) {}

    public static void sendStartMining(Object pos, Object direction) {
        Direction dir = JsHelper.toEnum(direction, Direction.class);
        BlockPos blockPos = DataHelper.createBlockPos(pos);
        // todo: fix packet
        PlayerInteractionAccess.of(mc.interactionManager).startMiningBlock(blockPos, dir);
    }

    public static void sendStopMining() {
        var access = PlayerInteractionAccess.of(mc.interactionManager); // .sendStopBreakPacket();
        access.sendBreakPacket();
    }

    public static void sendStopMining(Object pos, Object direction) {
        Direction dir = JsHelper.toEnum(direction, Direction.class);
        BlockPos blockPos = DataHelper.createBlockPos(pos);
        PlayerInteractionAccess.of(mc.interactionManager).sendBreakPacket(blockPos, dir);
    }

    public static void sendStopMining(int x, int y, int z, Object direction) {}

    public static void syncSelectedHotbar(int x) {
        PlayerInteractionAccess.of(mc.interactionManager).syncSelectedHotbar(x);
    }

    public static void sendClientCommand(Object cmd) {
        ClientCommandC2SPacket.Mode mode = JsHelper.toEnum(cmd, ClientCommandC2SPacket.Mode.class);
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, mode));
    }

    public static void sendMoveOnGround(boolean onGround, boolean horizontalCollision) {
        mc.getNetworkHandler().sendPacket(VPacket.newOnGroundOnly(onGround, horizontalCollision));
    }

    public static void sendMovePositionAndOnGround(
            double x, double y, double z, boolean isOnGround, boolean collision) {
        mc.getNetworkHandler().sendPacket(VPacket.newPositionAndOnGround(x, y, z, isOnGround, collision));
    }

    public static void sendMoveLookAndOnGround(float yaw, float pitch, boolean isOnGround, boolean collision) {
        mc.getNetworkHandler().sendPacket(VPacket.newLookAndOnGround(yaw, pitch, isOnGround, collision));
    }

    public static void sendMoveVehicle(Entity entity) {
        mc.getNetworkHandler().sendPacket(VPacket.newVehicleMove(JsHelper.unwrap(entity, Entity.class)));
    }

    public static void sendMoveFull(
            double x, double y, double z, float yaw, float pitch, boolean isOnGround, boolean collision) {
        mc.getNetworkHandler().sendPacket(VPacket.newFull(x, y, z, yaw, pitch, isOnGround, collision));
    }

    public static void sendPlayerAction(Object action) {
        PlayerActionC2SPacket.Action actionPacket = JsHelper.toEnum(action, PlayerActionC2SPacket.Action.class);
        switch (actionPacket) {
            case SWAP_ITEM_WITH_OFFHAND, DROP_ITEM, DROP_ALL_ITEMS, RELEASE_USE_ITEM -> {}

            default -> throw new IllegalStateException("Unexpected value: " + actionPacket);
        }
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(actionPacket, BlockPos.ORIGIN, Direction.DOWN));
    }
}
