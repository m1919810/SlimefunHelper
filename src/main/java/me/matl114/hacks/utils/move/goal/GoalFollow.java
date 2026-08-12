package me.matl114.hacks.utils.move.goal;

import me.matl114.utils.MathUtils;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public record GoalFollow(Entity entity) implements IPathGoal {
    @Override
    public Vec3d sample() {
        return entity.getPos();
    }

    @Override
    public boolean isInGoal(Vec3d playerPos) {
        return entity.getPos().squaredDistanceTo(playerPos)
                <= MathUtils.s2(0.3 + (entity.getDimensions(entity.getPose()).width() / 2));
    }
}
