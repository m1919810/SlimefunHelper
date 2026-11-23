package me.matl114.hacks.utils;

import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Stream;
import me.matl114.hacks.utils.move.RaycastRotationSolver;
import me.matl114.hacks.utils.move.Rotation;
import me.matl114.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

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
        return getPotentialEyeHeights(pos)
                .anyMatch(eyePos ->
                        box.contains(eyePos) || RaycastRotationSolver.canRayCast(box, eyePos, range, pitch, yaw));
    }

    /**
     * 在 locked 已锁定部分角度时，搜索能命中目标的旋转。
     *
     * <p>按 {@link #getPotentialEyeHeights(Vec3d)} 的优先级依次处理每个眼睛高度：眼睛位于碰撞箱内部时直接通过，
     * 不修改角度（返回 locked 本身）；否则先检查当前角度是否命中，再基于已锁定的 pitch 或 yaw 与射线距离
     * 直接求解另一个角度；未锁定任何角度时按 {@link #getRaycast(Vec3d, Object)} 的方式求解。
     * 前一个眼睛高度有解时不再考虑后续高度。pitch 与 yaw 都被锁定时无法搜索，直接返回 locked。
     *
     * @return 找到的旋转；无解时返回 null
     */
    default @Nullable Rotation findRotation(Vec3d pos, T target, Rotation locked, float pitch, float yaw) {
        if (locked.hasPitch() && locked.hasYaw()) return locked;
        float lockedPitch = locked.getPitch(pitch);
        float lockedYaw = locked.getYaw(yaw);
        Box box = getEffectiveBox(target);
        double range = getReachDistance(target);
        Iterator<Vec3d> eyes = getPotentialEyeHeights(pos).iterator();
        while (eyes.hasNext()) {
            Vec3d eye = eyes.next();
            // 眼睛位于碰撞箱内部：任意角度均视为命中，在进入求解器之前直接通过，不修改角度
            if (box.contains(eye)) return locked;
            if (!locked.hasRotate()) {
                if (box.squaredMagnitude(eye) > MathUtils.s2(range)) continue;
                Vec3d ray = raycastDirection(box, eye, range);
                if (box.raycast(eye, eye.add(ray.multiply(range))).isPresent()) return Rotation.from(ray);
                continue;
            }
            if (RaycastRotationSolver.canRayCast(box, eye, range, lockedPitch, lockedYaw))
                return Rotation.from(lockedPitch, lockedYaw);
            if (locked.hasPitch()) {
                Float solvedYaw = RaycastRotationSolver.solveYaw(box, eye, range, lockedPitch, lockedYaw);
                if (solvedYaw != null) return Rotation.from(lockedPitch, solvedYaw);
            } else {
                Float solvedPitch = RaycastRotationSolver.solvePitch(box, eye, range, lockedYaw, lockedPitch);
                if (solvedPitch != null) return Rotation.from(solvedPitch, lockedYaw);
            }
        }
        if (!locked.hasRotate()) {
            // 与 getRaycast 一致：所有眼睛高度均无法命中时退回当前眼睛高度的方向
            Vec3d fallbackEye = pos.add(0, client.player.dimensions.eyeHeight(), 0);
            return Rotation.from(raycastDirection(box, fallbackEye, range));
        }
        return null;
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
