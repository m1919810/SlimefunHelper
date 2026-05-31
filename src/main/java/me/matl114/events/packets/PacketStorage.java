package me.matl114.events.packets;

import javax.annotation.Nullable;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.PacketType;

public interface PacketStorage {
    long timestampMS();

    @Nullable
    PacketType<?> packetType();

    NetworkSide side();

    void send();

    void handle();
}
