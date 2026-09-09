package me.matl114.events.impl;

import net.minecraft.util.math.Vec3d;

public record Teleportation(int teleportId, double x, double y, double z, float pitch, float yaw) {
    public Vec3d vec3d() {
        return new Vec3d(x, y, z);
    }
}
