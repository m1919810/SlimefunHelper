package me.matl114.hacks.utils.entity;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public record LocalEntityPredictor(Entity entity) implements Predictor {
    @Override
    public Vec3d getKnownDeltaMovement() {
        return Vec3d.ZERO;
    }

    @Override
    public Vec3d predict(int ticksLater, int method, int useTickBefore) {
        return entity.getPos();
    }

    @Override
    public void tick() {}
}
