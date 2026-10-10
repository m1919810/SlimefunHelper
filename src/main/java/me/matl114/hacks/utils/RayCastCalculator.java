package me.matl114.hacks.utils;

import me.matl114.utils.MathUtils;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public interface RayCastCalculator<T> {
    @Nullable
    Box getEffectiveBox(T target);

    Vec3d getRaycast(Vec3d pos, T target);

    boolean canRayCast(Vec3d pos, T target, float pitch, float yaw);

    default Vec3d raycastDirection(Box box, Vec3d eyePos, double range) {
        Vec3d centerDirection = box.getCenter().subtract(eyePos).normalize();
        double raycastDistance = Math.max(0.0D, range - 0.09178D);
        if (box.raycast(eyePos, eyePos.add(centerDirection.multiply(raycastDistance)))
                .isPresent()) {
            return centerDirection;
        }
        Vec3d point = MathUtils.magnitudePoint(box.expand(-1.0E-7), eyePos);
        return point.subtract(eyePos).normalize();
    }
}
