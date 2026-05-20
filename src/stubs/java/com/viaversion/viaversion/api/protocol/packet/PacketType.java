package com.viaversion.viaversion.api.protocol.packet;

public interface PacketType {
    int getId();

    String getName();

    Direction direction();

    default State state() {
        return State.PLAY;
    }
}
