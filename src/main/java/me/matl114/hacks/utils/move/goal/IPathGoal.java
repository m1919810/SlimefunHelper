package me.matl114.hacks.utils.move.goal;

import net.minecraft.util.math.Vec3d;

public sealed interface IPathGoal
        permits GoalBlockPos, GoalDirection, GoalDynamic, GoalFollow, GoalList, GoalNear, GoalNearBlockPos {
    public Vec3d sample();

    public boolean isInGoal(Vec3d playerPos);
}
