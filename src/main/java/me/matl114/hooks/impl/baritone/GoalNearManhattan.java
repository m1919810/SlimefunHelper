package me.matl114.hooks.impl.baritone;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.interfaces.IGoalRenderPos;
import java.util.Objects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class GoalNearManhattan implements Goal, IGoalRenderPos {

    private final double x;
    private final double y;
    private final double z;
    private final double threshold;

    public GoalNearManhattan(Vec3d pos, double threshold) {
        this(pos.getX(), pos.getY(), pos.getZ(), threshold);
    }

    public GoalNearManhattan(double x, double y, double z, double threshold) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.threshold = threshold;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        double dx = Math.abs(x - this.x);
        double dy = Math.abs(y - this.y);
        double dz = Math.abs(z - this.z);
        return dx + dy + dz <= threshold;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double dx = x - this.x;
        double dy = y - this.y;
        double dz = z - this.z;

        double manhattan = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
        if (manhattan <= threshold) {
            return 0;
        }

        return GoalBlock.calculate(dx, (int) dy, dz);
    }

    @Override
    public double heuristic() {
        return 0;
    }

    @Override
    public BlockPos getGoalPos() {
        return new BlockPos((int) x, (int) y, (int) z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GoalNearManhattan other)) return false;
        return x == other.x && y == other.y && z == other.z && threshold == other.threshold;
    }

    @Override
    public int hashCode() {
        return (int) Objects.hash(x, y, z, threshold);
    }

    @Override
    public String toString() {
        return String.format("GoalNearManhattan{x=%.2f, y=%.2f, z=%.2f, threshold=%.2f}", x, y, z, threshold);
    }
}
