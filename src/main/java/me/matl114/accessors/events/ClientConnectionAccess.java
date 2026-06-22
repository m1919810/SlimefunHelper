package me.matl114.accessors.events;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkState;
import net.minecraft.network.packet.Packet;

public interface ClientConnectionAccess {
    public void handlePacket(Packet<?> packet);

    public NetworkState<?> getOutboundState();

    public NetworkState<?> getInboundState();

    public void sendByteBuf(ByteBuf buf);

    public static ClientConnectionAccess of(ClientConnection connection) {
        return (ClientConnectionAccess) connection;
    }
}
