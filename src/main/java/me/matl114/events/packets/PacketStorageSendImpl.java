package me.matl114.events.packets;

import java.util.function.BiConsumer;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.PacketType;
import org.jetbrains.annotations.Nullable;

public record PacketStorageSendImpl(Packet<?> packet, long timestampMS, BiConsumer<Packet<?>, Boolean> callback)
        implements PacketStorage.Send {
    @Nullable
    @Override
    public PacketType<?> packetType() {
        return packet.getPacketId();
    }

    @Override
    public void send(boolean forceFlush) {
        callback.accept(packet, forceFlush);
    }
}
