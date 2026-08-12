package me.matl114.hacks.utils.move.goal;

import me.matl114.utils.EntityUtils;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public record GoalDirection(float yaw) implements IPathGoal {
    public GoalDirection(Direction direction) {
        this(EntityUtils.directionToPitchYaw(direction).y);
    }

    @Override
    public Vec3d sample() {
        return null;
    }

    @Override
    public boolean isInGoal(Vec3d playerPos) {
        return false;
    }
}
