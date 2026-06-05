package me.matl114.hacks.utils.entity;

import net.minecraft.util.math.Vec3d;

public interface Predictor {
    public Vec3d getKnownDeltaMovement();

    public Vec3d predict(int ticksLater, int method, int useTickBefore);
}
