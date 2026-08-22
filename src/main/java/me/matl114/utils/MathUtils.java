package me.matl114.utils;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntSupplier;
import lombok.AllArgsConstructor;
import net.minecraft.util.math.*;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix3d;
import org.joml.Vector3d;

public class MathUtils {
    public static double s2(double x) {
        return x * x;
    }

    public static int s2(int x) {
        return x * x;
    }

    public static double squareSum(double... x) {
        double sum = 0;
        for (double y : x) {
            sum += y * y;
        }
        return sum;
    }

    /**
     * 将两个 int 打包成一个 long
     * @param high 存入高32位的 int（可以为负）
     * @param low  存入低32位的 int（可以为负）
     * @return 打包后的 long
     */
    public static long packInt(int high, int low) {
        // 先将 high 提升为 long，左移32位，然后与低32位（取无符号）按位或
        return ((long) high << 32) | (low & 0xFFFFFFFFL);
    }

    /**
     * 从打包的 long 中提取高32位 int
     */
    public static int unpackFirst(long packed) {
        return (int) (packed >> 32);
    }

    /**
     * 从打包的 long 中提取低32位 int
     */
    public static int unpackSecond(long packed) {
        return (int) packed;
    }

    public static double squaredMagnitude(Box thi, Box other) {
        double d = Math.max(Math.max(thi.minX - other.maxX, other.minX - thi.maxX), 0.0);
        double e = Math.max(Math.max(thi.minY - other.maxY, other.minY - thi.maxY), 0.0);
        double f = Math.max(Math.max(thi.minZ - other.maxZ, other.minZ - thi.maxZ), 0.0);
        return MathHelper.squaredMagnitude(d, e, f);
    }

    public static Vec3d magnitudePoint(Box shrinkedBox, Vec3d bestEyePos) {
        double x = Math.clamp(bestEyePos.x, shrinkedBox.minX, shrinkedBox.maxX);
        double y = Math.clamp(bestEyePos.y, shrinkedBox.minY, shrinkedBox.maxY);
        double z = Math.clamp(bestEyePos.z, shrinkedBox.minZ, shrinkedBox.maxZ);
        return new Vec3d(x, y, z);
    }

    public static int sgn(int t) {
        return Integer.compare(t, 0);
    }

    public static double sgn(double t) {
        return Double.compare(t, 0.0D);
    }

    public static double sgn(double t, double threshold) {
        return Math.abs(t) > threshold ? sgn(t) : 0.0D;
    }

    public static boolean isInBox(Vec3d a, Vec3d b, double range) {
        return isInBox(a.subtract(b), range);
    }

    public static boolean isInBox(Vec3d a, double range) {
        return Math.abs(a.x) < range && Math.abs(a.y) < range && Math.abs(a.z) < range;
    }

    public static boolean isInXZRange(Vec3d a, Vec3d b, double range) {
        return isInBox(a.subtract(b), range);
    }

    public static boolean isInXZRange(Vec3d a, double range) {
        return Math.abs(a.x) < range && Math.abs(a.z) < range;
    }

    public static int getManhattanDistance(BlockPos pos1, BlockPos pos2) {
        return Math.abs(pos1.getX() - pos2.getX())
                + Math.abs(pos1.getY() - pos2.getY())
                + Math.abs(pos1.getZ() - pos2.getZ());
    }

    public static Box getBlockBox(BlockPos pos) {
        return new Box(pos);
    }

    public static Box createBox(Vec3d vec3d, double ra) {
        return new Box(vec3d.subtract(ra, ra, ra), vec3d.add(ra, ra, ra));
    }

    public static String getDirectionName(int xSgn, int zSgn) {
        if (xSgn == 0 && zSgn == 0) return "Center";
        if (xSgn == 0 && zSgn == 1) return "South";
        if (xSgn == 0 && zSgn == -1) return "North";
        if (xSgn == 1 && zSgn == 0) return "East";
        if (xSgn == -1 && zSgn == 0) return "West";
        if (xSgn == 1 && zSgn == 1) return "Southeast";
        if (xSgn == 1 && zSgn == -1) return "Northeast";
        if (xSgn == -1 && zSgn == 1) return "Southwest";
        if (xSgn == -1 && zSgn == -1) return "Northwest";
        return "Unknown";
    }

    public static List<BlockPos> getOccupiedBlockPositions(Box box) {
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.ceil(box.maxX) - 1;
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.ceil(box.maxY) - 1;
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.ceil(box.maxZ) - 1;

        List<BlockPos> positions = new ArrayList<>((maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1));
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }

    public static final Direction[] HORIZONTALS = (Direction[]) Arrays.stream(Direction.values())
            .filter((direction) -> {
                return direction.getAxis().isHorizontal();
            })
            .sorted(Comparator.comparingInt(Direction::getHorizontal))
            .toArray(Direction[]::new);

    public static Direction fromHorizontalQuarterTurns(int quarterTurns) {
        return HORIZONTALS[MathHelper.abs(quarterTurns % HORIZONTALS.length)];
    }

    public static Direction fromHorizontalDegrees(double angle) {
        return fromHorizontalQuarterTurns(MathHelper.floor(angle / 90.0 + 0.5) & 3);
    }

    public static Direction getHorizontalFacing(Vec3d vec3d) {
        float yaw = EntityUtils.rotationToYaw(vec3d.normalize());
        return fromHorizontalDegrees(yaw);
    }

    public static ChunkPos toChunkPos(Vec3d vec3d) {
        return new ChunkPos(BlockPos.ofFloored(vec3d));
    }

    /**
     * 根据两个 Vec3d 点（最小和最大坐标）构建 Box 并获取占据的方块。
     */
    public static List<BlockPos> getOccupiedBlockPositions(Vec3d min, Vec3d max) {
        return getOccupiedBlockPositions(new Box(min, max));
    }

    public static Vec3d getVerticalWithSameXZ(Vec3d vec3d) {
        Vec3d direction = vec3d.normalize();
        return (direction.y != 0
                        ? new Vec3d(
                                direction.x,
                                -(MathUtils.s2(direction.x) + MathUtils.s2(direction.z)) / direction.y,
                                direction.z)
                        : new Vec3d(0, 1, 0))
                .normalize();
    }

    public static Vec3d getVerticalWithSameY(Vec3d vec3d) {
        Vec3d dir = vec3d.normalize();
        double dx = dir.x;
        double dz = dir.z;
        if (Math.abs(dx) < 1e-8 && Math.abs(dz) < 1e-8) {
            // 点在 Y 轴上，任何水平向量都是垂直的
            return new Vec3d(1, 0, 0);
        }
        // 与 (dx, dz) 垂直的向量为 (dz, -dx)，y=0
        return new Vec3d(dz, 0, -dx).normalize();
    }

    public static Pair<Vec3d, Vec3d> getTangentWithSameXZ(Vec3d center, double range, Vec3d point) {
        return getTangentWithSameXZ(range, point.subtract(center));
    }

    public static Pair<Vec3d, Vec3d> getTangentWithSameXZ(double range, Vec3d point) {
        double r2 = MathUtils.s2(range);
        double len = point.length();
        if (MathUtils.s2(len) <= r2) {
            // in ball
            Vec3d vec3d = getVerticalWithSameXZ(point);
            return Pair.of(vec3d, vec3d.negate());
        }
        double cutLine = r2 / len; // < range
        double cutLen = Math.sqrt(r2 - MathUtils.s2(cutLine));
        Vec3d verticals = getVerticalWithSameXZ(point);
        Vec3d cutPoint = point.normalize().multiply(cutLine);
        return Pair.of(
                cutPoint.add(verticals.multiply(cutLen)).subtract(point),
                cutPoint.subtract(verticals.multiply(cutLen)).subtract(point));
    }

    public static Pair<Vec3d, Vec3d> getTangentWithSamePlate(Vec3d center, double range, Vec3d point) {
        return getTangentWithSamePlate(range, point.subtract(center));
    }

    public static Pair<Vec3d, Vec3d> getTangentWithSamePlate(double range, Vec3d point) {
        double r2 = MathUtils.s2(range);
        double len = point.length();
        if (MathUtils.s2(len) <= r2) {
            // in ball
            Vec3d vec3d = getVerticalWithSameY(point);
            return Pair.of(vec3d, vec3d.negate());
        }
        double cutLine = r2 / len; // < range
        double cutLen = Math.sqrt(r2 - MathUtils.s2(cutLine));
        Vec3d verticals = getVerticalWithSameY(point);
        Vec3d cutPoint = point.normalize().multiply(cutLine);
        return Pair.of(
                cutPoint.add(verticals.multiply(cutLen)).subtract(point),
                cutPoint.subtract(verticals.multiply(cutLen)).subtract(point));
    }

    public static List<Vec3i> create2DPointListInRange(double i, int x) {
        List<Vec3i> list = new ArrayList<>();
        int range = (int) i;
        for (var y = -range; y <= range; ++y) {
            for (var z = -range; z <= range; ++z) {
                list.add(new Vec3i(y, x, z));
            }
        }
        list.sort(Comparator.comparingDouble(v -> v.getX() * v.getX() + v.getZ() * v.getZ()));
        return list;
    }

    public static List<Vec3i> create3DPointListInRange(double i) {
        List<Vec3i> list = new ArrayList<>();
        int range = (int) i;
        for (var x = -range; x <= range; ++x) {
            for (var y = -range; y <= range; ++y) {
                for (var z = -range; z <= range; ++z) {
                    list.add(new Vec3i(x, y, z));
                }
            }
        }

        list.sort(Comparator.comparingDouble(s -> s.getX() * s.getX() + s.getZ() * s.getZ() + s.getY() * s.getY()));
        return list;
    }

    public static double getBoxDistance(double x, double minX, double maxX) {
        return Math.max(Math.max(minX - x, x - maxX), 0.0);
    }

    public static Vec3d linearInterpolation(Vec3d[] vec3ds, int ticksLater) {
        if (ticksLater <= 0 || vec3ds.length < 3) return vec3ds[vec3ds.length - 1];
        if (vec3ds[0] == null || vec3ds[1] == null || vec3ds[2] == null) return vec3ds[2];

        // 计算最近的速度（位置变化）
        Vec3d velocity1 = vec3ds[2].subtract(vec3ds[1]);
        Vec3d velocity2 = vec3ds[1].subtract(vec3ds[0]);

        // 计算加速度
        Vec3d acceleration = velocity1.subtract(velocity2);

        // 预测：position = p0 + v*t + 0.5*a*t^2
        double t = ticksLater;
        return vec3ds[2].add(velocity1.multiply(t)).add(acceleration.multiply(0.5 * t * t));
    }

    public static Vec3d quadraticPolynomialFit(Vec3d[] positions, int ticksLater) {
        if (ticksLater <= 0 || positions.length < 3) return positions[positions.length - 1];
        if (positions[0] == null || positions[1] == null || positions[2] == null) return positions[2];
        // 使用最近3个点进行二次拟合
        // 对x, y, z分别进行二次多项式拟合
        // 设多项式为: p(t) = a*t^2 + b*t + c
        // 其中t是时间偏移，令当前时刻t=0

        double[] times = {-2, -1, 0}; // 相对于当前时间的时间点
        double[] xVals = new double[3];
        double[] yVals = new double[3];
        double[] zVals = new double[3];

        for (int i = 0; i < 3; i++) {
            xVals[i] = positions[i].x;
            yVals[i] = positions[i].y;
            zVals[i] = positions[i].z;
        }

        // 解二次多项式系数
        // 使用三个点解方程
        double[] xCoeffs = solveQuadratic(times, xVals);
        double[] yCoeffs = solveQuadratic(times, yVals);
        double[] zCoeffs = solveQuadratic(times, zVals);

        // 预测ticksLater后的位置
        double t = ticksLater;
        double t2 = t * t;

        return new Vec3d(
                xCoeffs[0] * t2 + xCoeffs[1] * t + xCoeffs[2],
                yCoeffs[0] * t2 + yCoeffs[1] * t + yCoeffs[2],
                zCoeffs[0] * t2 + zCoeffs[1] * t + zCoeffs[2]);
    }

    private static double[] solveQuadratic(double[] t, double[] p) {
        // 三个点(t0,p0),(t1,p1),(t2,p2)
        // 解方程组:
        // a*t0^2 + b*t0 + c = p0
        // a*t1^2 + b*t1 + c = p1
        // a*t2^2 + b*t2 + c = p2

        double t0 = t[0], t1 = t[1], t2 = t[2];
        double p0 = p[0], p1 = p[1], p2 = p[2];

        // 使用克莱姆法则解方程组
        double det = t0 * t0 * (t1 - t2) + t0 * (t2 * t2 - t1 * t1) + (t1 * t1 * t2 - t1 * t2 * t2);

        double detA = p0 * (t1 - t2) + t0 * (p2 - p1) + (p1 * t2 - p2 * t1);
        double detB = t0 * t0 * (p1 - p2) + p0 * (t2 * t2 - t1 * t1) + (t1 * t1 * p2 - t2 * t2 * p1);
        double detC =
                t0 * t0 * (t1 * p2 - t2 * p1) + t0 * (t2 * t2 * p1 - t1 * t1 * p2) + p0 * (t1 * t1 * t2 - t1 * t2 * t2);

        double a = detA / det;
        double b = detB / det;
        double c = detC / det;

        return new double[] {a, b, c};
    }

    public static Vec3d linearPrediction(Vec3d[] vec3ds, int ticksLater) {
        if (ticksLater <= 0) {
            return vec3ds[vec3ds.length - 1];
        }
        int datapoints = 0;
        for (int i = vec3ds.length - 1; i >= 0; --i) {
            if (vec3ds[i] != null) {
                ++datapoints;
            } else {
                break;
            }
        }
        if (datapoints < 2) {
            return vec3ds[vec3ds.length - 1];
        }
        Vec3d[] vec3ds1 = new Vec3d[datapoints];
        System.arraycopy(vec3ds, vec3ds.length - datapoints, vec3ds1, 0, datapoints);
        vec3ds = vec3ds1;
        double[] x = new double[vec3ds.length];
        double[] y = new double[vec3ds.length];
        double[] z = new double[vec3ds.length];
        double[] arg = new double[vec3ds.length];
        for (var i = 0; i < vec3ds.length; i++) {
            x[i] = vec3ds[i].x;
            y[i] = vec3ds[i].y;
            z[i] = vec3ds[i].z;
            arg[i] = -vec3ds.length + 1 + i;
        }
        Linear xl = linearRegression(arg, x);
        Linear yl = linearRegression(arg, y);
        Linear zl = linearRegression(arg, z);
        return new Vec3d(xl.f(ticksLater), yl.f(ticksLater), zl.f(ticksLater));
    }

    public static Vec3d quadraticPrediction(Vec3d[] vec3ds, int ticksLater) {
        if (ticksLater <= 0) {
            return vec3ds[vec3ds.length - 1];
        }
        int datapoints = 0;
        for (int i = vec3ds.length - 1; i >= 0; --i) {
            if (vec3ds[i] != null) {
                ++datapoints;
            } else {
                break;
            }
        }
        if (datapoints < 4) {
            return vec3ds[vec3ds.length - 1];
        }
        Vec3d[] vec3ds1 = new Vec3d[datapoints];
        System.arraycopy(vec3ds, vec3ds.length - datapoints, vec3ds1, 0, datapoints);
        vec3ds = vec3ds1;

        double[] x = new double[vec3ds.length];
        double[] y = new double[vec3ds.length];
        double[] z = new double[vec3ds.length];
        double[] arg = new double[vec3ds.length];
        for (var i = 0; i < vec3ds.length; i++) {
            x[i] = vec3ds[i].x;
            y[i] = vec3ds[i].y;
            z[i] = vec3ds[i].z;
            arg[i] = -vec3ds.length + 1 + i;
        }
        MathFunction xl = quadraticRegression(arg, x);
        MathFunction yl = quadraticRegression(arg, y);
        MathFunction zl = quadraticRegression(arg, z);
        return new Vec3d(xl.f(ticksLater), yl.f(ticksLater), zl.f(ticksLater));
    }

    public static Linear linearRegression(double[] x, double[] y) {
        int n = x.length;

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
        }

        double denominator = n * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 1e-10) {
            return new Linear(0, sumY / n);
        }

        double slope = (n * sumXY - sumX * sumY) / denominator;
        double intercept = (sumY - slope * sumX) / n;

        return new Linear(slope, intercept);
    }
    // todo 卡尔曼滤波实现

    public static MathFunction quadraticRegression(double[] x, double[] y) {
        int n = x.length;

        // 计算各个幂次的和
        double sumX = 0, sumX2 = 0, sumX3 = 0, sumX4 = 0;
        double sumY = 0, sumXY = 0, sumX2Y = 0;

        for (int i = 0; i < n; i++) {
            double xi = x[i];
            double xi2 = xi * xi;
            double xi3 = xi2 * xi;
            double xi4 = xi3 * xi;

            sumX += xi;
            sumX2 += xi2;
            sumX3 += xi3;
            sumX4 += xi4;

            sumY += y[i];
            sumXY += xi * y[i];
            sumX2Y += xi2 * y[i];
        }

        // 构建正规方程矩阵
        double[][] A = {
            {sumX4, sumX3, sumX2},
            {sumX3, sumX2, sumX},
            {sumX2, sumX, n}
        };

        double[] b = {sumX2Y, sumXY, sumY};

        Matrix3d m = new Matrix3d(sumX4, sumX3, sumX2, sumX3, sumX2, sumX, sumX2, sumX, n);
        Vector3d v = new Vector3d(sumX2Y, sumXY, sumY);
        if (Math.abs(m.determinant()) > 1e-6) {
            Matrix3d minv = m.invert();
            Vector3d result = minv.transform(v);
            return new Quadratic(result.x, result.y, result.z);
        } else {
            return linearRegression(x, y);
        }
    }

    public static interface MathFunction {
        public double f(double x);
    }

    @AllArgsConstructor
    public static class Linear implements MathFunction {
        double a;
        double b;

        @Override
        public double f(double x) {
            return a * x + b;
        }
    }

    @AllArgsConstructor
    public static class Quadratic implements MathFunction {
        double a;
        double b;
        double c;

        @Override
        public double f(double x) {
            return a * x * x + b * x + c;
        }
    }
    // 指数加权移动平均
    public static class NVPredictor {
        private final Vec3d[] pointList;
        private final IntSupplier supplier;

        public NVPredictor(Vec3d[] historyStack, IntSupplier currentIndex) {
            pointList = historyStack;
            supplier = currentIndex;
        }

        public Vec3d compute(int ticksLater) {
            int idx = supplier.getAsInt();
            Vec3d currentPos = pointList[idx];
            if (currentPos == null) return null;
            int len = pointList.length;
            int i = 1;
            List<Vec3d> points = new ArrayList<>();
            points.add(currentPos);
            for (; i < len; i++) {
                Vec3d v3d = pointList[(idx - i + len) % len];
                if (v3d != null) {
                    points.add(0, v3d);
                } else {
                    break;
                }
            }
            Vec3d result = null;
            if (!points.isEmpty()) {
                result = currentPos;
            }
            if (points.size() < 2) return result;
            List<Vec3d> diff = new ArrayList<>();
            Vec3d oldV = null;
            for (Vec3d v : points) {
                if (oldV == null) {
                    oldV = v;
                    continue;
                }

                diff.add(v.subtract(oldV));

                oldV = v;
            }
            if (diff.size() >= 2) {
                Vec3d d = new Vec3d(0, 0, 0);
                for (Vec3d v : diff) {
                    d = d.add(v).multiply(0.5);
                }
                return result.add(d.multiply(ticksLater));
            } else if (diff.size() == 1) {
                return currentPos.add(diff.get(0).multiply(ticksLater));
            }

            return result;
        }
    }

    @ApiStatus.Experimental
    public static class RotationalPredictor {
        private static final double MAX_TURN_ANGLE = Math.toRadians(60.0D);

        private final Vec3d[] historyStack;
        private final IntSupplier currentIndex;

        public RotationalPredictor(Vec3d[] historyStack, IntSupplier currentIndex) {
            this.historyStack = historyStack;
            this.currentIndex = currentIndex;
        }

        public Vec3d compute(int ticksLater) {
            List<Vec3d> points = collectPoints();
            if (points.isEmpty()) return null;

            Vec3d current = points.get(points.size() - 1);
            if (ticksLater <= 0) return current;
            if (points.size() < 2) return current;

            Vec3d lastVel = current.subtract(points.get(points.size() - 2));
            if (points.size() < 3) {
                return current.add(lastVel.multiply(ticksLater));
            }

            List<Double> horizontalLengths = new ArrayList<>();
            List<Double> verticalDisplacements = new ArrayList<>();
            List<Double> horizontalAngles = new ArrayList<>();
            for (int i = 1; i < points.size(); i++) {
                Vec3d delta = points.get(i).subtract(points.get(i - 1));
                horizontalLengths.add(Math.hypot(delta.x, delta.z));
                verticalDisplacements.add(delta.y);
                horizontalAngles.add(Math.atan2(delta.z, delta.x));
            }

            double avgDeltaAngle = 0.0D;
            int anglePairs = 0;
            for (int i = 1; i < horizontalAngles.size(); i++) {
                double delta = horizontalAngles.get(i) - horizontalAngles.get(i - 1);
                while (delta > Math.PI) delta -= 2 * Math.PI;
                while (delta < -Math.PI) delta += 2 * Math.PI;
                if (Math.abs(delta) <= MAX_TURN_ANGLE) {
                    avgDeltaAngle += delta;
                }
                anglePairs++;
            }
            if (anglePairs > 0) {
                avgDeltaAngle /= anglePairs;
            }

            double predictedHorizontalLength = predictScalar(horizontalLengths, ticksLater);
            double predictedVerticalDisplacement = predictScalar(verticalDisplacements, ticksLater);

            if (predictedHorizontalLength < 0.0D) {
                predictedHorizontalLength = 0.0D;
            }

            double lastAngle = horizontalAngles.get(horizontalAngles.size() - 1);
            double finalAngle = lastAngle + avgDeltaAngle;

            double dx = Math.cos(finalAngle) * predictedHorizontalLength;
            double dz = Math.sin(finalAngle) * predictedHorizontalLength;

            return current.add(dx, predictedVerticalDisplacement, dz);
        }

        private double predictScalar(List<Double> samples, int ticksLater) {
            if (samples.isEmpty()) return 0.0D;
            double last = samples.get(samples.size() - 1);
            if (ticksLater <= 0 || samples.size() < 2) return last;

            List<Double> diff = new ArrayList<>();
            for (int i = 1; i < samples.size(); i++) {
                diff.add(samples.get(i) - samples.get(i - 1));
            }
            if (diff.isEmpty()) return last;
            if (diff.size() == 1) {
                return last + diff.get(0) * ticksLater;
            }

            double d = 0.0D;
            for (double v : diff) {
                d = (d + v) * 0.5D;
            }
            return last + d * ticksLater;
        }

        private List<Vec3d> collectPoints() {
            int idx = currentIndex.getAsInt();
            Vec3d currentPos = historyStack[idx];
            if (currentPos == null) return List.of();

            List<Vec3d> points = new ArrayList<>();
            points.add(currentPos);
            int len = historyStack.length;
            for (int i = 1; i < len; i++) {
                Vec3d v = historyStack[(idx - i + len) % len];
                if (v != null) {
                    points.add(0, v);
                } else {
                    break;
                }
            }
            return points;
        }
    }
}
