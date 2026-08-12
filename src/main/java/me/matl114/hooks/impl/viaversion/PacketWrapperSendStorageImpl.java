package me.matl114.hooks.impl.viaversion;

import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import io.netty.channel.Channel;
import java.util.Locale;
import java.util.function.Consumer;
import me.matl114.events.Listener;
import me.matl114.events.packets.PacketStorage;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.PacketType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class PacketWrapperSendStorageImpl implements PacketStorage {
    long timeStamp;
    PacketType<?> type0;

    PacketWrapper wrapper;
    Channel channel;
    Consumer<Boolean> sendOperation;

    public PacketWrapperSendStorageImpl(
            long timeStamp, PacketWrapper wrapper, Channel channel, Consumer<Boolean> sendOperation) {
        this.timeStamp = timeStamp;
        this.wrapper = wrapper;
        this.channel = channel;
        this.sendOperation = sendOperation;
    }

    @Override
    public long timestampMS() {
        return this.timeStamp;
    }

    @Nullable
    @Override
    public PacketType<?> packetType() {
        if (type0 == null) {
            // initialize;
            com.viaversion.viaversion.api.protocol.packet.PacketType type = this.wrapper.getPacketType();
            if (type != null) {
                Identifier id = Identifier.ofVanilla(type.getName().toLowerCase(Locale.ROOT));
                type0 = Listener.getPacketTypeById(id, false);
            }
        }
        ;
        return type0;
    }

    @Override
    public NetworkSide side() {
        return NetworkSide.SERVERBOUND;
    }

    @Override
    public void send() {
        this.sendOperation.accept(channel.eventLoop().inEventLoop());
    }

    @Override
    public void handle() {
        throw new UnsupportedOperationException("Not supported side.");
    }
}
