package me.matl114.accessors.events;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;

public interface ClientConnectionAccess {
    public void handlePacket(Packet<?> packet);

    public static ClientConnectionAccess of(ClientConnection connection) {
        return (ClientConnectionAccess) connection;
    }
}
