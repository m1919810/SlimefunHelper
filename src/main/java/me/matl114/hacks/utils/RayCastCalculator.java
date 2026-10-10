package me.matl114.hacks.utils;

import java.util.Objects;
import java.util.stream.Stream;
import me.matl114.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public interface RayCastCalculator<T> {
    MinecraftClient client = MinecraftClient.getInstance();

    Box getEffectiveBox(T target);

    default Vec3d getRaycast(Vec3d pos, T target) {
        Box box = getEffectiveBox(target);
        ;
        double range = getReachDistance(target);
        Vec3d fallbackEye = pos.add(0, client.player.dimensions.eyeHeight(), 0);
        return getPotentialEyeHeights(pos)
                .filter(eyePos -> box.squaredMagnitude(eyePos) <= MathUtils.s2(range))
                .map(eyePos -> {
                    Vec3d ray = raycastDirection(box, eyePos, range);
                    if (box.raycast(eyePos, eyePos.add(ray.multiply(range))).isPresent()) {
                        return ray;
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> raycastDirection(box, fallbackEye, range));
    }

    default Stream<Vec3d> getPotentialEyeHeights(Vec3d pos) {
        return Stream.of(pos.add(0, client.player.dimensions.eyeHeight(), 0));
    }

    default boolean canRayCast(Vec3d pos, T target, float pitch, float yaw) {
        Box box = getEffectiveBox(target);
        double range = getReachDistance(target);
        Vec3d direction = EntityUtils.pitchYawToRotation(pitch, yaw).normalize();
        return getPotentialEyeHeights(pos).anyMatch(eyePos -> box.raycast(eyePos, eyePos.add(direction.multiply(range)))
                .isPresent());
    }

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

    double getReachDistance(T val);
}
