package com.viaversion.viaversion.api.protocol.packet;

public enum State {
    HANDSHAKE,
    STATUS,
    LOGIN,
    CONFIGURATION,
    PLAY;

    private State() {}
}
