package me.matl114.hacks.utils.move.goal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public record GoalNear(Vec3d center, double radius) implements IPathGoal {
    public GoalNear(BlockPos center, double radius) {
        this(center.toBottomCenterPos(), radius + 0.5);
    }

    @Override
    public Vec3d sample() {
        return center;
    }

    @Override
    public boolean isInGoal(Vec3d playerPos) {
        Vec3d delta = playerPos.subtract(center);
        return Math.abs(delta.x) + Math.abs(delta.y) + Math.abs(delta.z) <= radius;
    }
}
