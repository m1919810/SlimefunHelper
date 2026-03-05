package me.matl114.jsApi;

import java.util.List;
import me.matl114.accessors.hacks.PlayerInteractionAccess;
import me.matl114.events.Listener;
import me.matl114.hacks.InvTasks;
import me.matl114.utils.ApiMethod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PacketType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

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
        var re = mc.world.getPendingUpdateManager().incrementSequence();
        int seq = re.getSequence();
        re.close();
        return seq;
    }

    public static void sendInventoryPacket(int slotId, int button, Object actionTypeStr) {
        InvTasks.clickSlotAsync(slotId, button, JsHelper.toEnum(actionTypeStr, SlotActionType.class));
    }
    // todo ; interactionManager methods
    public static void sendAttackBlock(int x, int y, int z, Object direction) {
        sendAttackBlock(new BlockPos(x, y, z), direction);
    }

    public static void sendAttackBlock(Object pos, Object direction) {
        Direction dir = JsHelper.toEnum(direction, Direction.class);
        BlockPos blockPos = DataHelper.createBlockPos(pos);
        mc.execute(() -> {
            mc.interactionManager.attackBlock(blockPos, dir);
        });
    }

    public static void sendStartMining(Object pos, Object direction) {
        Direction dir = JsHelper.toEnum(direction, Direction.class);
        BlockPos blockPos = DataHelper.createBlockPos(pos);
        PlayerInteractionAccess.of(mc.interactionManager).sendStartBreakPacket(blockPos, dir);
    }

    public static void sendStopMining() {
        var access = PlayerInteractionAccess.of(mc.interactionManager); // .sendStopBreakPacket();
        access.sendStopBreakPacket(access.getCurrentMiningPos(), Direction.UP);
    }

    public static void sendStopMining(Object pos, Object direction) {
        Direction dir = JsHelper.toEnum(direction, Direction.class);
        BlockPos blockPos = DataHelper.createBlockPos(pos);
        PlayerInteractionAccess.of(mc.interactionManager).sendStopBreakPacket(blockPos, dir);
    }

    public static void sendStopMining(int x, int y, int z, Object direction) {}
}
