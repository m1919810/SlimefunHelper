package de.florianmichael.viafabricplus.injection.access;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

public interface IServerInfo {
    ProtocolVersion viaFabricPlus$forcedVersion();

    void viaFabricPlus$forceVersion(ProtocolVersion var1);

    boolean viaFabricPlus$passedDirectConnectScreen();

    void viaFabricPlus$passDirectConnectScreen(boolean var1);

    ProtocolVersion viaFabricPlus$translatingVersion();

    void viaFabricPlus$setTranslatingVersion(ProtocolVersion var1);
}
