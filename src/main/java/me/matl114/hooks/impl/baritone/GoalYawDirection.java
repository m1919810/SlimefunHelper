package me.matl114.hooks.impl.baritone;

import baritone.api.pathing.goals.Goal;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.SettingsUtil;
import net.minecraft.util.math.BlockPos;

public class GoalYawDirection implements Goal {

    public final int x;
    public final int y;
    public final int z;
    public final float yaw;
    public final double dirX;
    public final double dirZ;

    public GoalYawDirection(BlockPos origin, float yaw) {
        this.x = origin.getX();
        this.y = origin.getY();
        this.z = origin.getZ();
        this.yaw = yaw;

        double rad = Math.toRadians(yaw);
        this.dirX = -Math.sin(rad);
        this.dirZ = Math.cos(rad);

        if (Math.abs(dirX) < 1.0E-9 && Math.abs(dirZ) < 1.0E-9) {
            throw new IllegalArgumentException("Invalid yaw " + yaw);
        }
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return false;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double relX = x - this.x;
        double relY = y - this.y;
        double relZ = z - this.z;

        double forward = relX * dirX + relZ * dirZ;
        double lateralX = relX - forward * dirX;
        double lateralZ = relZ - forward * dirZ;
        double lateral = Math.abs(lateralX) + Math.abs(lateralZ);
        double vertical = Math.abs(relY);

        double heuristic = 0.0;

        // 越往目标方向前进，值越小
        heuristic -= forward * 100.0;

        // 越偏离方向轴线，惩罚越大
        heuristic += lateral * 1000.0;

        // 越偏离初始高度，惩罚越大
        heuristic += vertical * 1000.0;

        return heuristic;
    }

    @Override
    public double heuristic() {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GoalYawDirection other)) {
            return false;
        }
        return x == other.x && y == other.y && z == other.z && Float.compare(other.yaw, yaw) == 0;
    }

    @Override
    public int hashCode() {
        int hash = (int) BetterBlockPos.longHash(x, y, z);
        hash = 31 * hash + Float.hashCode(yaw);
        return hash;
    }

    @Override
    public String toString() {
        return String.format(
                "GoalYawDirection{x=%s,y=%s,z=%s,yaw=%s}",
                SettingsUtil.maybeCensor(x), SettingsUtil.maybeCensor(y), SettingsUtil.maybeCensor(z), yaw);
    }
}
