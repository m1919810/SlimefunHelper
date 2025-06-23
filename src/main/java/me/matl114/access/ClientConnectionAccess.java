package me.matl114.access;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.handler.PacketSizeLogger;

public interface ClientConnectionAccess {
    public PacketSizeLogger getPacketSizeLogger();
    public static ClientConnectionAccess of(ClientConnection connection){
        return (ClientConnectionAccess) connection;
    }
}
