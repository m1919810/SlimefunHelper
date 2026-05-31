package de.florianmichael.protocoltranslator;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.channel.Channel;

public class ProtocolTranslator {
    public static ProtocolVersion getTargetVersion() {
        return null;
    }

    public static ProtocolVersion getTargetVersion(Channel channel) {
        return null;
    }

    public static void setTargetVersion(ProtocolVersion newVersion, boolean revertOnDisconnect) {}

    public static UserConnection getPlayNetworkUserConnection() {
        return null;
    }
}
