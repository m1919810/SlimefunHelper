package me.matl114.jsApi;

import me.matl114.listenerUtils.Listener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.state.PlayStateFactories;
import net.minecraft.util.Identifier;

public class PacketHelper {
    static MinecraftClient mc = MinecraftClient.getInstance();
    public static Class<? extends Packet<?>> getPacketType(String packetType, boolean s2c){
        Identifier id = Identifier.tryParse(packetType);
        return Listener.getPacketClassById(id, s2c);
    }
    public static void sendPacket(Packet<?> packet){
        mc.getNetworkHandler().sendPacket(packet);
    }
}
