package me.matl114.accessors.access;

import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;

public interface PlayerInteractEntityC2SPacketAccess {
    void setEntityId(int entityId);

    void setType(PlayerInteractEntityC2SPacket.InteractTypeHandler type);

    void setPlayerSneaking(boolean playerSneaking);

    static PlayerInteractEntityC2SPacketAccess of(PlayerInteractEntityC2SPacket packet) {
        return (PlayerInteractEntityC2SPacketAccess) packet;
    }
}
