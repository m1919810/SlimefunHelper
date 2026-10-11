package me.matl114.events.packets;

import javax.annotation.Nullable;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.PacketType;

public interface PacketStorage {
    long timestampMS();

    @Nullable
    PacketType<?> packetType();

    NetworkSide side();

    public interface Recv extends PacketStorage {
        default NetworkSide side() {
            return NetworkSide.CLIENTBOUND;
        }

        void handle();
    }

    public interface Send extends PacketStorage {
        default NetworkSide side() {
            return NetworkSide.SERVERBOUND;
        }

        void send(boolean forceFlush);
    }
}
