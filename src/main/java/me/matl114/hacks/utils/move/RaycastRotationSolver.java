package me.matl114.hacks.utils.move;

import java.util.Arrays;
import me.matl114.hacks.utils.EntityUtils;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * 在锁定 pitch 或 yaw 其中之一时，基于几何关系直接求解另一个角度，使从给定眼睛位置出发、
 * 长度为 range 的射线命中目标碰撞箱。
 *
 * <ul>
 *   <li>锁定 yaw：射线位于一个竖直半平面内，碰撞箱在该平面上的截面是矩形，
 *       与半径为 range 的圆求交后为凸区域，可命中的 pitch 构成单个区间，由区域顶点的仰角确定。</li>
 *   <li>锁定 pitch：竖直方向的约束确定射线在箱体高度范围内的距离区间，换算为水平距离区间后，
 *       问题化为水平面上射线命中「矩形 ∩ 圆环」，可命中的 yaw 区间端点只可能出现在矩形角点
 *       或圆与矩形边的交点处，只需检查这些候选点之间的区间。</li>
 * </ul>
 *
 * 求解前先做距离预判；可行区间会以中点为中心缩放到 0.8 倍宽度后再取最接近期望值的角度，并最终通过与 {@link Box#raycast(Vec3d, Vec3d)} 相同的判定进行验证，
 * 以抵消 {@link MathHelper#sin(double)} 查表精度与边界判定带来的误差。
 */
public final class RaycastRotationSolver {
    private static final double EPS = 1.0E-9D;
    /** 可行区间以中点为中心缩放到原宽度的该比例后再取值，避免边缘精度导致误判。 */
    private static final double SHRINK_FACTOR = 0.8D;
    /** 可行 yaw 段的最小宽度（度），更窄的段视为孤立候选角度并忽略。 */
    private static final double MIN_RUN_WIDTH = 1.0E-3D;

    private RaycastRotationSolver() {}

    /**
     * 纯射线判定，与 {@link Box#raycast(Vec3d, Vec3d)} 一致；不处理眼睛位于碰撞箱内部的情形，
     * 该情形应由调用方在进入求解器之前处理。
     */
    public static boolean canRayCast(Box box, Vec3d eye, double range, float pitch, float yaw) {
        Vec3d direction = EntityUtils.pitchYawToRotation(pitch, yaw).normalize();
        return box.raycast(eye, eye.add(direction.multiply(range))).isPresent();
    }

    /**
     * 在 yaw 锁定时求解 pitch，返回最接近 desiredPitch 且通过射线验证的 pitch；无解时返回 null。
     */
    public static @Nullable Float solvePitch(Box box, Vec3d eye, double range, float yaw, float desiredPitch) {
        // 眼睛位于碰撞箱内部：任意角度均可，直接返回当前角度且不进入几何计算。
        // 调用方应在进入求解器之前处理该情形，正常不会触发
        if (box.contains(eye)) return desiredPitch;
        if (range <= 0.0D) return null;
        // 距离预判：眼睛到碰撞箱的最近距离超过射线长度时必然无解
        if (box.squaredMagnitude(eye) > range * range) return null;
        // 使用与实际射线相同的三角函数实现确定水平方向
        Vec3d horizontal = EntityUtils.pitchYawToRotation(0.0F, yaw);
        double length = Math.hypot(horizontal.x, horizontal.z);
        if (length < EPS) return null;
        double ux = horizontal.x / length;
        double uz = horizontal.z / length;
        // 水平射线穿过碰撞箱水平投影的距离区间 [h0, h1]
        double[] slab = {0.0D, range};
        if (!clipSlab(slab, eye.x, ux, box.minX, box.maxX) || !clipSlab(slab, eye.z, uz, box.minZ, box.maxZ))
            return null;
        double h0 = slab[0];
        double h1 = slab[1];
        double v0 = box.minY - eye.y;
        double v1 = box.maxY - eye.y;
        double r2 = range * range;
        // 截面矩形 [h0, h1] × [v0, v1] 与半径 range 的圆求交，统计各顶点仰角的范围
        double[] elevation = {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double h : new double[] {h0, h1}) {
            for (double v : new double[] {v0, v1}) {
                if (h * h + v * v <= r2) acceptElevation(elevation, h, v);
            }
            double rest = r2 - h * h;
            if (rest >= 0.0D) {
                double v = Math.sqrt(rest);
                if (v >= v0 && v <= v1) acceptElevation(elevation, h, v);
                if (-v >= v0 && -v <= v1) acceptElevation(elevation, h, -v);
            }
        }
        for (double v : new double[] {v0, v1}) {
            double rest = r2 - v * v;
            if (rest >= 0.0D) {
                double h = Math.sqrt(rest);
                if (h >= h0 && h <= h1) acceptElevation(elevation, h, v);
            }
        }
        if (elevation[0] > elevation[1]) return null;
        // pitch 向下为正，与仰角符号相反
        double low = Math.max(-90.0D, -elevation[1]);
        double high = Math.min(90.0D, -elevation[0]);
        if (low > high) return null;
        // 区间同样以 (center, range) 表示，半宽缩放后取最接近 desiredPitch 的值
        double center = (low + high) * 0.5D;
        double shrunkRange = (high - low) * 0.5D * SHRINK_FACTOR;
        double picked = Math.clamp(desiredPitch, center - shrunkRange, center + shrunkRange);
        if (canRayCast(box, eye, range, (float) picked, yaw)) return (float) picked;
        if (canRayCast(box, eye, range, (float) center, yaw)) return (float) center;
        return null;
    }

    /**
     * 在 pitch 锁定时求解 yaw，返回最接近 desiredYaw 且通过射线验证的 yaw（与 desiredYaw 保持在同一圈内）；
     * 无解时返回 null。
     */
    public static @Nullable Float solveYaw(Box box, Vec3d eye, double range, float pitch, float desiredYaw) {
        // 眼睛位于碰撞箱内部：任意角度均可，直接返回当前角度且不进入几何计算。
        // 调用方应在进入求解器之前处理该情形，正常不会触发
        if (box.contains(eye)) return desiredYaw;
        if (range <= 0.0D) return null;
        // 距离预判：眼睛到碰撞箱的最近距离超过射线长度时必然无解
        if (box.squaredMagnitude(eye) > range * range) return null;
        // 使用与实际射线相同的三角函数实现确定水平/竖直分量
        Vec3d planar = EntityUtils.pitchYawToRotation(pitch, 0.0F).normalize();
        double cosPitch = planar.z;
        double sinPitch = -planar.y;
        // 竖直射线不受 yaw 影响，无法通过调整 yaw 命中
        if (cosPitch < 1.0E-6D) return null;
        // 射线处于箱体高度范围内的距离区间，换算为水平距离区间 [sa, sb]
        double[] distance = {0.0D, range};
        if (!clipSlab(distance, eye.y, -sinPitch, box.minY, box.maxY)) return null;
        double sa = distance[0] * cosPitch;
        double sb = distance[1] * cosPitch;

        // 以 desiredYaw 为原点的候选分界角度，范围 [-180, 180]
        double[] candidates = new double[32];
        int count = 0;
        candidates[count++] = -180.0D;
        candidates[count++] = 180.0D;
        double[] xs = {box.minX, box.maxX};
        double[] zs = {box.minZ, box.maxZ};
        for (double x : xs) {
            for (double z : zs) {
                double dx = x - eye.x;
                double dz = z - eye.z;
                double dist = Math.hypot(dx, dz);
                if (dist >= sa && dist <= sb) candidates[count++] = yawOffset(dx, dz, desiredYaw);
            }
        }
        for (double radius : new double[] {sa, sb}) {
            if (radius <= EPS) continue;
            double r2 = radius * radius;
            for (double x : xs) {
                double dx = x - eye.x;
                double rest = r2 - dx * dx;
                if (rest < 0.0D) continue;
                double dz = Math.sqrt(rest);
                for (double z : new double[] {eye.z - dz, eye.z + dz}) {
                    if (z >= box.minZ && z <= box.maxZ) candidates[count++] = yawOffset(dx, z - eye.z, desiredYaw);
                }
            }
            for (double z : zs) {
                double dz = z - eye.z;
                double rest = r2 - dz * dz;
                if (rest < 0.0D) continue;
                double dx = Math.sqrt(rest);
                for (double x : new double[] {eye.x - dx, eye.x + dx}) {
                    if (x >= box.minX && x <= box.maxX) candidates[count++] = yawOffset(x - eye.x, dz, desiredYaw);
                }
            }
        }
        Arrays.sort(candidates, 0, count);
        // 去除重复的候选角度（如圆与边相切、角点恰好落在圆上时产生的重合点）
        int unique = 1;
        for (int i = 1; i < count; i++) {
            if (candidates[i] - candidates[unique - 1] >= EPS) candidates[unique++] = candidates[i];
        }
        count = unique;

        // 只检查候选角度之间的开区间并合并相邻的可行区间；候选角度本身不作为解，
        // 因此仅在孤立候选角度处可行（两侧区间均不可行）的情形会被忽略。
        // 可行段以 (centerYaw, range) 存储：centerYaw 为段中点相对 desiredYaw 的偏移（规整到 [-180, 180)），
        // range 为半宽，不依赖 ±180 分界，避免跨越 ±180 的段在区间计算中出错
        double[] runCenter = new double[count];
        double[] runRange = new double[count];
        int runs = 0;
        double runStart = Double.NaN;
        // 从 -180 开始的首段的终点：-180 与 180 只是人为分界，若末段到 180 结束，二者是跨越 ±180 的同一段
        double firstRunEnd = Double.NaN;
        for (int i = 0; i + 1 < count; i++) {
            double low = candidates[i];
            double high = candidates[i + 1];
            boolean valid = hitsHorizontally(box, eye, sa, sb, desiredYaw + (low + high) * 0.5D);
            if (valid && Double.isNaN(runStart)) runStart = low;
            boolean last = i + 2 == count;
            if ((valid && !last) || Double.isNaN(runStart)) continue;
            double end = valid ? high : low;
            if (runStart <= -180.0D && end < 180.0D) {
                firstRunEnd = end;
            } else if (end >= 180.0D && !Double.isNaN(firstRunEnd)) {
                runs = addRun(runCenter, runRange, runs, runStart, firstRunEnd + 360.0D);
                firstRunEnd = Double.NaN;
            } else {
                runs = addRun(runCenter, runRange, runs, runStart, end);
            }
            runStart = Double.NaN;
        }
        if (!Double.isNaN(firstRunEnd)) runs = addRun(runCenter, runRange, runs, -180.0D, firstRunEnd);
        if (runs == 0) return null;

        // 各可行段半宽缩放后取最接近 desiredYaw 的偏移，按偏移大小依次尝试
        double[] picked = new double[runs];
        Integer[] order = new Integer[runs];
        for (int i = 0; i < runs; i++) {
            double shrunkRange = runRange[i] * SHRINK_FACTOR;
            double center = runCenter[i];
            picked[i] = Math.abs(center) <= shrunkRange ? 0.0D : center - Math.signum(center) * shrunkRange;
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(Math.abs(picked[a]), Math.abs(picked[b])));
        for (int index : order) {
            float yaw = (float) (desiredYaw + picked[index]);
            if (canRayCast(box, eye, range, pitch, yaw)) return yaw;
            float center = (float) (desiredYaw + runCenter[index]);
            if (canRayCast(box, eye, range, pitch, center)) return center;
        }
        return null;
    }

    /** 水平面上沿 yaw 方向的射线在水平距离 [sa, sb] 内是否穿过碰撞箱的水平投影。 */
    private static boolean hitsHorizontally(Box box, Vec3d eye, double sa, double sb, double yaw) {
        double radians = Math.toRadians(yaw);
        double[] slab = {sa, sb};
        return clipSlab(slab, eye.x, -Math.sin(radians), box.minX, box.maxX)
                && clipSlab(slab, eye.z, Math.cos(radians), box.minZ, box.maxZ);
    }

    /** 将参数区间 t 裁剪到 start + t * direction ∈ [min, max] 的部分，返回裁剪后是否非空。 */
    private static boolean clipSlab(double[] t, double start, double direction, double min, double max) {
        if (Math.abs(direction) < EPS) return start >= min && start <= max && t[0] <= t[1];
        double a = (min - start) / direction;
        double b = (max - start) / direction;
        if (a > b) {
            double swap = a;
            a = b;
            b = swap;
        }
        t[0] = Math.max(t[0], a);
        t[1] = Math.min(t[1], b);
        return t[0] <= t[1];
    }

    private static void acceptElevation(double[] range, double horizontal, double vertical) {
        double angle = Math.toDegrees(Math.atan2(vertical, horizontal));
        range[0] = Math.min(range[0], angle);
        range[1] = Math.max(range[1], angle);
    }

    /** 方向 (dx, dz) 对应的 yaw 相对 base 的偏移，范围 [-180, 180)。 */
    private static double yawOffset(double dx, double dz, double base) {
        return MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(-dx, dz)) - base);
    }

    /**
     * 以 (centerYaw, range) 形式记录可行段 [low, high]（high 可能超过 180），宽度过小的段视为孤立候选角度并忽略。
     *
     * @return 记录后的段数量
     */
    private static int addRun(double[] centers, double[] ranges, int runs, double low, double high) {
        double width = high - low;
        if (width < MIN_RUN_WIDTH) return runs;
        centers[runs] = MathHelper.wrapDegrees((low + high) * 0.5D);
        ranges[runs] = width * 0.5D;
        return runs + 1;
    }
}
