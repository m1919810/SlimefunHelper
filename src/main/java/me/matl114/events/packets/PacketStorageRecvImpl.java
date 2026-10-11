package me.matl114.events.packets;

import me.matl114.accessors.events.ClientConnectionAccess;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PacketType;

public record PacketStorageRecvImpl(Packet<?> packet, long timestampMS, ClientConnection connection)
        implements PacketStorage.Recv {
    @Override
    public PacketType<?> packetType() {
        return packet.getPacketId();
    }

    @Override
    public void handle() {
        try {
            ClientConnectionAccess.of(connection).handlePacket(packet);
        } catch (Throwable throwable) {
        }
    }
}
