package me.matl114.hacks.utils.move.goal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public record GoalNearBlockPos(BlockPos pos) implements IPathGoal {
    @Override
    public Vec3d sample() {
        return pos.toBottomCenterPos();
    }

    @Override
    public boolean isInGoal(Vec3d playerPos) {
        BlockPos pos2 = BlockPos.ofFloored(playerPos);
        var var1 = pos2.getX() - pos.getX();
        var var2 = pos2.getY() - pos.getY();
        var var3 = pos2.getZ() - pos.getZ();
        return Math.abs(var1) + Math.abs(var2 < 0 ? var2 + 1 : var2) + Math.abs(var3) <= 1;
    }
}
