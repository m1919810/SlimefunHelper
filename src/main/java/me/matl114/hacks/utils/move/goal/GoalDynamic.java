package me.matl114.hacks.utils.move.goal;

import java.util.function.Supplier;
import me.matl114.utils.MathUtils;
import net.minecraft.util.math.Vec3d;

public record GoalDynamic(Supplier<Vec3d> supplier, double radius) implements IPathGoal {
    @Override
    public Vec3d sample() {
        return supplier.get();
    }

    @Override
    public boolean isInGoal(Vec3d playerPos) {
        return supplier.get().squaredDistanceTo(playerPos) <= MathUtils.s2(radius);
    }
}
