package me.matl114.accessors.access;

import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;

public interface PlayerInteractItemC2SPacketAccess {
    void setHand(Hand hand);

    void setYaw(float yaw);

    void setPitch(float pitch);

    static PlayerInteractItemC2SPacketAccess of(PlayerInteractItemC2SPacket packet) {
        return (PlayerInteractItemC2SPacketAccess) packet;
    }
}
