package me.matl114.hooks.impl.baritone;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import java.util.function.Supplier;
import net.minecraft.util.math.Vec3d;

public final class GoalDynamicGoal implements Goal {

    private final Supplier<Vec3d> targetSupplier;
    private final double radius;

    public GoalDynamicGoal(Supplier<Vec3d> targetSupplier, double radius) {
        this.targetSupplier = targetSupplier;
        this.radius = radius;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        Vec3d target = targetSupplier.get();
        if (target == null) {
            return false;
        }

        double dx = x - target.getX();
        double dy = y - target.getY();
        double dz = z - target.getZ();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        Vec3d target = targetSupplier.get();
        if (target == null) {
            return Double.POSITIVE_INFINITY;
        }

        double dx = x - target.getX();
        double dy = y - target.getY();
        double dz = z - target.getZ();
        return GoalBlock.calculate(dx, (int) dy, dz);
    }
}
