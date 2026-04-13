package me.matl114.accessors.events;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;

public interface ClientConnectionAccess {
    public void handlePacket(Packet<?> packet);

    public void stopInBoundDelay();

    public void startInBoundDelay();

    public void startInBoundDelayImmediately();

    public void addPacketInBoundDelayQueue(Packet<?> packet);

    public boolean isInBoundDelay();

    public static ClientConnectionAccess of(ClientConnection connection) {
        return (ClientConnectionAccess) connection;
    }
}
