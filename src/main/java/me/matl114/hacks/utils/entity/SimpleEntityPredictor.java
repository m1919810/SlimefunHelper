package me.matl114.hacks.utils.entity;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public record SimpleEntityPredictor(Entity entity) implements Predictor {
    @Override
    public Vec3d getKnownDeltaMovement() {
        return new Vec3d(entity.getX() - entity.prevX, entity.getY() - entity.prevY, entity.getZ() - entity.prevZ);
    }

    @Override
    public Vec3d predict(int ticksLater, int method, int a) {
        return entity.getLerpedPos(ticksLater);
    }
}
