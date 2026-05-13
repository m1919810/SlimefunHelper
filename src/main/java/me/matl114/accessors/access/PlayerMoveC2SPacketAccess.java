package me.matl114.accessors.access;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public interface PlayerMoveC2SPacketAccess {
    public void setOnGround(boolean onGround);

    public void setPitch(float pitch);

    public void setYaw(float yaw);

    public void setCause(Cause cause);

    public Cause getCause();

    public static PlayerMoveC2SPacket setCause(PlayerMoveC2SPacket packet, Cause cause) {
        PlayerMoveC2SPacketAccess.of(packet).setCause(cause);
        return packet;
    }

    public static PlayerMoveC2SPacketAccess of(PlayerMoveC2SPacket packet) {
        return (PlayerMoveC2SPacketAccess) packet;
    }
    //
    //    public void setManual(boolean manual);
    //
    //    public boolean isManual();
    public enum Cause {
        SET_BACK,
        PLAYER_MOVEMENT,
        HACKING_PACKETS,
        LEGACY_SNAP;
    }
}
