package me.matl114.versioned.impl;

import me.matl114.versioned.api.VPacket;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;

public class Packet_v1_21_1 implements VPacket {

    @Override
    public PlayerMoveC2SPacket createOnGroundOnly(boolean isOnGround, boolean collision) {
        // 1.21.1 版本不支持 collision 参数，忽略它
        return new PlayerMoveC2SPacket.OnGroundOnly(isOnGround);
    }

    @Override
    public PlayerMoveC2SPacket createPositionAndOnGround(
            double x, double y, double z, boolean isOnGround, boolean collision) {
        // 1.21.1 版本不支持 collision 参数，忽略它
        return new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, isOnGround);
    }

    @Override
    public PlayerMoveC2SPacket createLookAndOnGround(float yaw, float pitch, boolean isOnGround, boolean collision) {
        // 1.21.1 版本不支持 collision 参数，忽略它
        return new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, isOnGround);
    }

    @Override
    public PlayerMoveC2SPacket createFull(
            double x, double y, double z, float yaw, float pitch, boolean isOnGround, boolean collision) {
        // 1.21.1 版本不支持 collision 参数，忽略它
        return new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, isOnGround);
    }

    public VehicleMoveC2SPacket createVehicleMove(Entity entity) {
        return new VehicleMoveC2SPacket(entity);
    }
}
