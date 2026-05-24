package com.viaversion.viaversion.api.protocol.packet;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import org.jspecify.annotations.Nullable;

public interface PacketWrapper {
    int PASSTHROUGH_ID = 1000;

    static PacketWrapper create(
            com.viaversion.viaversion.api.protocol.packet.PacketType packetType, UserConnection connection) {
        return null;
    }

    static PacketWrapper create(
            com.viaversion.viaversion.api.protocol.packet.PacketType packetType,
            @Nullable ByteBuf inputBuffer,
            UserConnection connection) {
        return null;
    }

    /** @deprecated */
    @Deprecated
    static PacketWrapper create(int packetId, @Nullable ByteBuf inputBuffer, UserConnection connection) {
        return null;
    }

    <T> T get(Type<T> var1, int var2);

    /** @deprecated */
    @Deprecated
    boolean is(Type var1, int var2);

    boolean isReadable(Type var1, int var2);

    <T> void set(Type<T> var1, int var2, @Nullable T var3);

    <T> T read(Type<T> var1);

    <T> void write(Type<T> var1, @Nullable T var2);

    <T> T passthrough(Type<T> var1);

    <T> T passthroughAndMap(Type<?> var1, Type<T> var2);

    void passthroughAll();

    void writeToBuffer(ByteBuf var1);

    void clearInputBuffer();

    void clearPacket();

    default void send(Class protocol) {
        this.send(protocol, true);
    }

    void send(Class var1, boolean var2);

    default void scheduleSend(Class protocol) {
        this.scheduleSend(protocol, true);
    }

    void scheduleSend(Class var1, boolean var2);

    ChannelFuture sendFuture(Class var1);

    void sendRaw();

    ChannelFuture sendFutureRaw();

    void scheduleSendRaw();

    boolean isCancelled();

    default void cancel() {
        this.setCancelled(true);
    }

    void setCancelled(boolean var1);

    UserConnection user();

    void resetReader();

    void sendToServerRaw();

    void scheduleSendToServerRaw();

    default void sendToServer(Class protocol) {
        this.sendToServer(protocol, true);
    }

    void sendToServer(Class var1, boolean var2);

    default void scheduleSendToServer(Class protocol) {
        this.scheduleSendToServer(protocol, true);
    }

    void scheduleSendToServer(Class var1, boolean var2);

    @Nullable
    PacketType getPacketType();

    void setPacketType(com.viaversion.viaversion.api.protocol.packet.PacketType var1);

    int getId();

    /** @deprecated */
    @Deprecated
    void setId(int var1);
}
