package me.matl114.hacks.utils.move;

import java.util.function.Function;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.modules.move.ElytraExtra;
import me.matl114.hacks.modules.move.ElytraFlight;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.EntityUtils;
import me.matl114.hooks.ViaFabricPlusHooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public class ElytraOptimizeUtils {
    public static final MinecraftClient mc = MinecraftClient.getInstance();
    public static boolean shouldAbortV3Optimize = false;

    private static final float V5_PITCH = -89.9F;
    private static final int V5_YAW_CANDIDATE_COUNT = 360;
    private static final double[] V5_EXTRA_YAWS = {0.0D, 90.0D, 180.0D, 270.0D};
    private static final double V5_FALLBACK_REQUEST_SPEED = 1.7D;
    private static final double V5_EPSILON = 1E-9D;
    private static final double V3_GREEDY_REQUEST_SPEED = 1.7D;
    private static final float[] V3_GREEDY_YAWS = {45.0F, 135.0F, 225.0F, 315.0F};

    public static ElytraExtra.Al getPullupOnlyAl(ElytraExtra.Al al) {
        return switch (al) {
            case V3, V3_PULLUP_ONLY -> ElytraExtra.Al.V3_PULLUP_ONLY;
            case V4, V5, V5_PULLUP_ONLY -> ElytraExtra.Al.V5_PULLUP_ONLY;
            default -> al;
        };
    }

    public static Vec2f calculateBestPullupSpeed(float pitch, float yaw, ElytraExtra.Al al) {
        if (al == null) return new Vec2f(pitch, yaw);
        Vec3d vec3d = EntityUtils.pitchYawToRotation(pitch, yaw);
        switch (al) {
            case V3 -> {
                return calculateBestV3ClimbingSpeed(pitch, yaw);
            }
            case V3_PULLUP_ONLY -> {
                return calculateBestV3GreedyClimbingSpeed();
            }
            case V4 -> {
                return calculateBestV4ClimbingSpeed(pitch, yaw);
            }
            case V5 -> {
                if (!ViaFabricPlusHooks.isSupportDupRot()) {
                    return calculateBestV3ClimbingSpeed(pitch, yaw);
                }
                if (pitch < V5_PITCH) {
                    return new Vec2f(pitch, yaw);
                }
                double pitchDeg = -5;
                if (pitch > pitchDeg) {
                    return new Vec2f(pitch, yaw);
                }
                return new Vec2f(V5_PITCH, yaw);
            }
            case V5_PULLUP_ONLY -> {
                return calculateBestV5ClimbingSpeed(pitch, yaw);
            }
            case V1 -> {
                double horizontal = vec3d.horizontalLength();
                if (horizontal < 1E-6) {
                    vec3d = vec3d.withAxis(Direction.Axis.X, 5);
                    horizontal = vec3d.horizontalLength();
                }
                double pitchDeg = -36;
                double pitchRad = Math.toRadians(pitchDeg);
                // 使用 -tan(pitch) 来抵消符号，或者直接用 tan(54.5)
                double newY = -horizontal * Math.tan(pitchRad);
                // 等价写法：double newY = horizontal * Math.tan(Math.toRadians(54.5));

                // 4. 返回新的向量（保持 x 和 z 不变，仅替换 y）
                return new Vec2f(-36.0F, yaw);
            }
            case V2 -> {
                double horizontal = vec3d.horizontalLength();
                if (horizontal < 1E-6) {
                    vec3d = vec3d.withAxis(Direction.Axis.X, 5);
                    horizontal = vec3d.horizontalLength();
                }
                double pitchDeg = -54.5;
                double pitchRad = Math.toRadians(pitchDeg);
                // 使用 -tan(pitch) 来抵消符号，或者直接用 tan(54.5)
                double newY = -horizontal * Math.tan(pitchRad);
                // 等价写法：double newY = horizontal * Math.tan(Math.toRadians(54.5));

                // 4. 返回新的向量（保持 x 和 z 不变，仅替换 y）
                return new Vec2f(-54.5F, yaw);
            }
        }
        return new Vec2f(pitch, yaw);
    }

    public static float calculateBestPullUpAngle(float yaw, ElytraExtra.Al al) {
        return calculateBestPullUpRotation(yaw, al).x;
    }

    public static Vec2f calculateBestPullUpRotation(float yaw, ElytraExtra.Al al) {
        return calculateBestPullupSpeed(-45.0F, yaw, al);
    }

    public static Vec2f calculateBestV3ClimbingSpeed(float pitch, float yaw) {
        float yawDeg = yaw;

        // 2. 计算水平方向的最大分量 M = max(|sin(yaw)|, |cos(yaw)|)
        double yawRad = Math.toRadians(yawDeg);
        double sinY = Math.abs(Math.sin(yawRad));
        double cosY = Math.abs(Math.cos(yawRad));
        double M = Math.max(sinY, cosY);

        // 3. 计算临界俯仰角（向上，负值）
        // 令 max(|look.x|, |look.z|) = M * |cos(pitch)| = 0.5
        // 因为 M >= sqrt(2)/2 ≈ 0.707，所以 0.5/M <= 0.707 < 1，恒有解
        double cosPitchCrit = 0.5 / M;
        // 向上飞，pitch 为负，取 -arccos
        double newPitchRad = -Math.acos(cosPitchCrit);
        float newPitchDeg = (float) Math.toDegrees(newPitchRad);

        // 4. 用新的俯仰角和原始偏航角重新组合视线方向向量
        return new Vec2f(newPitchDeg, yawDeg);
    }

    public static Vec2f calculateBestV3GreedyClimbingSpeed() {
        // Match new18: pair-plan score is horizontal speed over the next two ticks (lambda = 0).
        Vec3d velocity = PlayerStateManager.INSTANCE.lastKnownClientVelocity;
        Vec3d previousLook = EntityUtils.pitchYawToRotation(
                        PlayerStateManager.INSTANCE.lastPitch, PlayerStateManager.INSTANCE.lastYaw)
                .normalize();
        float previousYaw = EntityUtils.rotationToPitchYaw(previousLook).y;
        double autoRescaleAmount = ElytraExtra.INSTANCE.autoRescaleAmount.get();
        double bestScore = Double.NEGATIVE_INFINITY;
        float bestYaw = previousYaw;
        float bestPitch = 45;
        float bestSecondYaw = previousYaw;
        for (float firstYaw : V3_GREEDY_YAWS) {
            Vec2f firstAngles = calculateBestV3ClimbingSpeed(-45.0F, firstYaw);
            Vec3d firstLook =
                    EntityUtils.pitchYawToRotation(firstAngles.x, firstAngles.y).normalize();
            Vec3d firstRequest = firstLook.multiply(V3_GREEDY_REQUEST_SPEED);
            Vec3d firstVelocity = simulateV3GreedyCandidate(
                    velocity, previousLook, firstRequest, firstAngles.x, firstAngles.y, autoRescaleAmount);
            for (float secondYaw : V3_GREEDY_YAWS) {
                Vec2f secondAngles = calculateBestV3ClimbingSpeed(-45.0F, secondYaw);
                Vec3d secondRequest = EntityUtils.pitchYawToRotation(secondAngles.x, secondAngles.y)
                        .normalize()
                        .multiply(V3_GREEDY_REQUEST_SPEED);
                Vec3d secondVelocity = simulateV3GreedyCandidate(
                        firstVelocity, firstLook, secondRequest, secondAngles.x, secondAngles.y, autoRescaleAmount);
                double score = firstVelocity.horizontalLength() + secondVelocity.horizontalLength();
                double currentDelta = absoluteYawDelta(firstYaw, previousYaw);
                double bestDelta = absoluteYawDelta(bestYaw, previousYaw);
                boolean currentSwitches = firstYaw != secondYaw;
                boolean bestSwitches = bestYaw != bestSecondYaw;
                if (score > bestScore
                        || (Double.compare(score, bestScore) == 0
                                && (currentSwitches != bestSwitches ? !currentSwitches : currentDelta < bestDelta))) {
                    bestScore = score;
                    bestYaw = firstYaw;
                    bestPitch = firstAngles.x;
                    bestSecondYaw = secondYaw;
                }
            }
        }
        return new Vec2f(bestPitch, bestYaw);
    }

    private static Vec3d simulateV3GreedyCandidate(
            Vec3d velocity, Vec3d previousLook, Vec3d request, float pitch, float yaw, double autoRescaleAmount) {
        Vec3d savedVelocity = PlayerStateManager.INSTANCE.lastKnownClientVelocity;
        float savedPitch = PlayerStateManager.INSTANCE.lastPitch;
        float savedYaw = PlayerStateManager.INSTANCE.lastYaw;
        Vec2f previousAngles = EntityUtils.rotationToPitchYaw(previousLook);
        PlayerStateManager.INSTANCE.lastKnownClientVelocity = velocity;
        PlayerStateManager.INSTANCE.lastPitch = previousAngles.x;
        PlayerStateManager.INSTANCE.lastYaw = previousAngles.y;
        try {
            return applyAxisLimit30_Greedy(request, pitch, yaw, autoRescaleAmount, false);
        } finally {
            PlayerStateManager.INSTANCE.lastKnownClientVelocity = savedVelocity;
            PlayerStateManager.INSTANCE.lastPitch = savedPitch;
            PlayerStateManager.INSTANCE.lastYaw = savedYaw;
        }
    }

    public static Vec2f calculateBestV4ClimbingSpeed(float pitch, float yaw) {
        float yawDeg = yaw;
        double yawRad = Math.toRadians(yawDeg);
        double sinY = Math.abs(Math.sin(yawRad));
        double cosY = Math.abs(Math.cos(yawRad));
        double m = Math.max(sinY, cosY);

        double cosPitchCrit =
                ViaFabricPlusHooks.isSupportDupRot() ? Math.max(-1.0, Math.min(1.0, 1.0 / m - 1.0)) : 0.5 / m;
        float newPitchDeg = (float) -Math.toDegrees(Math.acos(cosPitchCrit));
        return new Vec2f(Math.clamp(newPitchDeg, -88, 88), yawDeg);
    }

    public static Vec2f calculateBestV5ClimbingSpeed(float pitch, float yaw) {
        if (!ViaFabricPlusHooks.isSupportDupRot()) {
            return calculateBestV3ClimbingSpeed(pitch, yaw);
        }
        double requestSpeed = getV5RequestSpeed();
        double autoRescaleAmount = ElytraExtra.INSTANCE.autoRescaleAmount.get();
        Vec3d lastVelocity = PlayerStateManager.INSTANCE.lastKnownClientVelocity;
        float previousYaw = yaw;
        double anchorYaw = lastVelocity.horizontalLength() > V5_EPSILON
                ? Math.toDegrees(Math.atan2(-lastVelocity.x, lastVelocity.z))
                : previousYaw;

        float bestInputYaw = wrapV5Yaw(yaw);
        double bestScore = Double.NEGATIVE_INFINITY;
        float bestExtraYaw = 0.0F;
        for (int index = 0; index < V5_YAW_CANDIDATE_COUNT; index++) {
            float inputYaw = wrapV5Yaw(anchorYaw + index);
            for (double extraYaw : V5_EXTRA_YAWS) {
                double score = scoreV5Candidate(
                        lastVelocity, inputYaw, (float) extraYaw, V5_PITCH, yaw, requestSpeed, autoRescaleAmount);
                double yawDelta = absoluteYawDelta(inputYaw, previousYaw);
                double bestYawDelta = absoluteYawDelta(bestInputYaw, previousYaw);
                if (score > bestScore + V5_EPSILON
                        || (Math.abs(score - bestScore) <= V5_EPSILON
                                && (yawDelta < bestYawDelta - V5_EPSILON
                                        || (Math.abs(yawDelta - bestYawDelta) <= V5_EPSILON
                                                && extraYaw < bestExtraYaw)))) {
                    bestInputYaw = inputYaw;
                    bestExtraYaw = (float) extraYaw;
                    bestScore = score;
                }
            }
        }
        return new Vec2f(V5_PITCH, bestInputYaw);
    }

    private record V5Bounds(double uMinX, double uMaxX, double uMinY, double uMaxY, double uMinZ, double uMaxZ) {}

    private static double getV5RequestSpeed() {
        if (ElytraFlight.INSTANCE != null) {
            return Math.max(V5_EPSILON, ElytraFlight.INSTANCE.packetMotion.get());
        }
        return V5_FALLBACK_REQUEST_SPEED;
    }

    private static float wrapV5Yaw(double yaw) {
        double wrapped = yaw % 360.0D;
        if (wrapped < 0.0D) wrapped += 360.0D;
        return (float) wrapped;
    }

    private static double absoluteYawDelta(double left, double right) {
        double delta = (left - right + 180.0D) % 360.0D - 180.0D;
        return Math.abs(delta);
    }

    private static Vec3d simulateV5Velocity(Vec3d velocity, Vec3d rotation) {
        return PlayerStateManager.INSTANCE.lastInWater || PlayerStateManager.INSTANCE.lastInLava
                ? EntityUtils.simulateTravelInFluidVelocity(
                        velocity, PlayerStateManager.INSTANCE.lastInWater, PlayerStateManager.INSTANCE.lastInLava, true)
                : EntityUtils.calculateGlidingVelocity(mc.player, velocity, rotation, true);
    }

    private static V5Bounds calculateV5Bounds(
            Vec3d lastVelocity,
            Vec3d simulated,
            Vec3d currentLook,
            Vec3d extraLook,
            Vec3d currentMotion,
            double autoRescaleAmount) {
        double antiTickSkipping = 0.05D;
        currentLook = currentLook.normalize();
        extraLook = extraLook.normalize();

        double minX = Math.min(-antiTickSkipping, currentLook.x) + Math.min(-antiTickSkipping, extraLook.x);
        double minY = Math.min(-antiTickSkipping, currentLook.y) + Math.min(-antiTickSkipping, extraLook.y);
        double minZ = Math.min(-antiTickSkipping, currentLook.z) + Math.min(-antiTickSkipping, extraLook.z);
        double maxX = Math.max(antiTickSkipping, currentLook.x) + Math.max(antiTickSkipping, extraLook.x);
        double maxY = Math.max(antiTickSkipping, currentLook.y) + Math.max(antiTickSkipping, extraLook.y);
        double maxZ = Math.max(antiTickSkipping, currentLook.z) + Math.max(antiTickSkipping, extraLook.z);

        double threshold = Math.min(autoRescaleAmount, currentMotion.length());
        minX = Math.max(-threshold, minX * threshold);
        maxX = Math.min(threshold, maxX * threshold);
        minY = Math.max(-threshold, minY * threshold);
        maxY = Math.min(threshold, maxY * threshold);
        minZ = Math.max(-threshold, minZ * threshold);
        maxZ = Math.min(threshold, maxZ * threshold);

        double eMinX = Math.min(0.0D, minX - lastVelocity.x);
        double eMaxX = Math.max(0.0D, maxX - lastVelocity.x);
        double eMinY = Math.min(0.0D, minY - lastVelocity.y);
        double eMaxY = Math.max(0.0D, maxY - lastVelocity.y);
        double eMinZ = Math.min(0.0D, minZ - lastVelocity.z);
        double eMaxZ = Math.max(0.0D, maxZ - lastVelocity.z);
        double thresholdLeft = ElytraExtra.INSTANCE.autoRescaleThreshold.get();

        return new V5Bounds(
                simulated.x + eMinX + thresholdLeft,
                simulated.x + eMaxX - thresholdLeft,
                simulated.y + eMinY + thresholdLeft,
                simulated.y + eMaxY - thresholdLeft,
                simulated.z + eMinZ + thresholdLeft,
                simulated.z + eMaxZ - thresholdLeft);
    }

    private static double scoreV5Candidate(
            Vec3d lastVelocity,
            float inputYaw,
            float extraYaw,
            float pitch,
            float currentInputYaw,
            double requestSpeed,
            double autoRescaleAmount) {
        Vec3d currentLook = EntityUtils.pitchYawToRotation(pitch, inputYaw).normalize();
        Vec3d extraLook = EntityUtils.pitchYawToRotation(0.0F, extraYaw).normalize();
        Vec3d request = currentLook.multiply(requestSpeed);
        Vec3d simulated = simulateV5Velocity(lastVelocity, currentLook);
        V5Bounds bounds =
                calculateV5Bounds(lastVelocity, simulated, currentLook, extraLook, request, autoRescaleAmount);
        return Math.max(bounds.uMinX() * bounds.uMinX(), bounds.uMaxX() * bounds.uMaxX())
                + Math.max(bounds.uMinZ() * bounds.uMinZ(), bounds.uMaxZ() * bounds.uMaxZ());
    }

    private static float getV5ExtraYaw(Vec3d currentMotion, float pitch, float yaw, double autoRescaleAmount) {
        Vec3d lastVelocity = PlayerStateManager.INSTANCE.lastKnownClientVelocity;
        float inputYaw = yaw;
        float bestExtraYaw = 0.0F;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (double extraYaw : V5_EXTRA_YAWS) {
            double score = scoreV5Candidate(
                    lastVelocity, inputYaw, (float) extraYaw, pitch, yaw, currentMotion.length(), autoRescaleAmount);
            if (score > bestScore + V5_EPSILON
                    || (Math.abs(score - bestScore) <= V5_EPSILON && extraYaw < bestExtraYaw)) {
                bestExtraYaw = (float) extraYaw;
                bestScore = score;
            }
        }
        return bestExtraYaw;
    }

    public static Vec2f calculateBestDownForwardSpeed(
            float pitch, float yaw, ElytraExtra.Al al, boolean natural, boolean downward) {
        if (al.isIn(ElytraExtra.Al.V3, ElytraExtra.Al.V3_PULLUP_ONLY)) {
            return downwardLimitor(pitch, yaw, 30.5F, natural, downward);
        }
        if (al.isIn(ElytraExtra.Al.V4, ElytraExtra.Al.V5)) {
            return downwardLimitor(pitch, yaw, 5.0F, natural, downward);
        }
        if (al.isIn(ElytraExtra.Al.V1)) {
            return downwardLimitor(pitch, yaw, 36.0F, natural, downward);
        }
        return new Vec2f(pitch, yaw);
    }

    private static Vec2f downwardLimitor(float pitch, float yaw, float bestPitch, boolean natural, boolean downward) {
        if (downward && pitch > 75) {
            return new Vec2f(pitch, yaw);
        }
        float pitchDeg = bestPitch;
        if (natural && pitch < pitchDeg) {
            return new Vec2f(pitch, yaw);
        }
        return new Vec2f(pitchDeg, yaw);
    }

    public static void setOverridingFireworkVelocity(Vec3d vec3d) {
        ElytraExtra.INSTANCE.setOverridingFireworkVelocity(vec3d);
    }

    public static Vec3d applyAxisLimit30(
            Vec3d currentMotion, float pitch, float yaw, double autoRescaleAmount, boolean realApply) {
        Vec3d currentRotation = EntityUtils.pitchYawToRotation(pitch, yaw);
        Vec3d lastTickVelocity = PlayerStateManager.INSTANCE.lastKnownClientVelocity;
        Vec3d thisTickSimulationVelocity =
                PlayerStateManager.INSTANCE.lastInWater || PlayerStateManager.INSTANCE.lastInLava
                        ? EntityUtils.simulateTravelInFluidVelocity(
                                lastTickVelocity,
                                PlayerStateManager.INSTANCE.lastInWater,
                                PlayerStateManager.INSTANCE.lastInLava,
                                true)
                        : EntityUtils.calculateGlidingVelocity(mc.player, lastTickVelocity, currentRotation, true);

        // --- fireworksBox 构造 (保持不变) ---
        Vec3d lastPitchYaw = EntityUtils.pitchYawToRotation(
                PlayerStateManager.INSTANCE.lastPitch, PlayerStateManager.INSTANCE.lastYaw);
        double antiTickSkipping = 0.05;
        Vec3d currentLook = currentRotation.normalize();
        Vec3d lastLook = lastPitchYaw.normalize();
        double minX = Math.min(-antiTickSkipping, currentLook.getX()) + Math.min(-antiTickSkipping, lastLook.getX());
        double minY = Math.min(-antiTickSkipping, currentLook.getY()) + Math.min(-antiTickSkipping, lastLook.getY());
        double minZ = Math.min(-antiTickSkipping, currentLook.getZ()) + Math.min(-antiTickSkipping, lastLook.getZ());
        double maxX = Math.max(antiTickSkipping, currentLook.getX()) + Math.max(antiTickSkipping, lastLook.getX());
        double maxY = Math.max(antiTickSkipping, currentLook.getY()) + Math.max(antiTickSkipping, lastLook.getY());
        double maxZ = Math.max(antiTickSkipping, currentLook.getZ()) + Math.max(antiTickSkipping, lastLook.getZ());

        double threshold = Math.min(autoRescaleAmount, currentMotion.length());
        minX *= threshold;
        maxX *= threshold;
        minY *= threshold;
        maxY *= threshold;
        minZ *= threshold;
        maxZ *= threshold;
        minX = Math.max(-threshold, minX);
        maxX = Math.min(threshold, maxX);
        minY = Math.max(-threshold, minY);
        maxY = Math.min(threshold, maxY);
        minZ = Math.max(-threshold, minZ);
        maxZ = Math.min(threshold, maxZ);
        // Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        Vec3d v1 = lastTickVelocity;
        Vec3d v3 = thisTickSimulationVelocity;
        double eMinX = Math.min(0, minX - v1.x);
        double eMaxX = Math.max(0, maxX - v1.x);
        double eMinY = Math.min(0, minY - v1.y);
        double eMaxY = Math.max(0, maxY - v1.y);
        double eMinZ = Math.min(0, minZ - v1.z);
        double eMaxZ = Math.max(0, maxZ - v1.z);
        double zeroPointThreeTest = 0.0;
        double thresoldLeft = ElytraExtra.INSTANCE.autoRescaleThreshold.get();
        double uMinX = v3.x + eMinX - zeroPointThreeTest + thresoldLeft;
        double uMaxX = v3.x + eMaxX + zeroPointThreeTest - thresoldLeft;
        double uMinY = v3.y + eMinY + thresoldLeft;
        double uMaxY = v3.y + eMaxY - thresoldLeft;
        double uMinZ = v3.z + eMinZ - zeroPointThreeTest + thresoldLeft;
        double uMaxZ = v3.z + eMaxZ + zeroPointThreeTest - thresoldLeft;
        // I dont understand.
        double dx = currentMotion.x, dz = currentMotion.z;
        double exceedX = 0.0, exceedZ = 0.0;

        if (dx > 0) exceedX = dx / uMaxX;
        else if (dx < 0) exceedX = dx / uMinX; // 注意 dx 为负，uMinX 也为负，比值 >1 若 dx < uMinX

        if (dz > 0) exceedZ = dz / uMaxZ;
        else if (dz < 0) exceedZ = dz / uMinZ;

        // ??????????????????????????????????????????????????????????????????????????
        // dy

        double maxScale = Math.max(exceedX, exceedZ);

        // 退化情况：所有 scale 为 0
        if (maxScale < 1E-6) {
            if (realApply) setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        Vec3d clampedMotion = currentMotion.multiply(1 / maxScale);
        // todo : add more angle restrict
        if (clampedMotion.y > 0) {
            clampedMotion = clampedMotion.withAxis(Direction.Axis.Y, uMaxY);
        } else if (clampedMotion.y < 0) {
            clampedMotion = clampedMotion.withAxis(Direction.Axis.Y, uMinY);
        }
        clampedMotion = ElytraExtra.INSTANCE.applySpeedLimit(clampedMotion);

        // 需要缩小至盒子边界
        if (realApply) setOverridingFireworkVelocity(clampedMotion); // <-- 保存边界值
        return clampedMotion;
    }

    public static Vec3d applyAxisLimit3(Vec3d currentMotion, float pitch, float yaw, double autoRescaleAmount) {
        return applyAxisLimit3_0(currentMotion, pitch, yaw, autoRescaleAmount, true);
    }

    public static Vec3d applyAxisLimit3Pullup(Vec3d currentMotion, float pitch, float yaw, double autoRescaleAmount) {
        return applyAxisLimit3_1(currentMotion, pitch, yaw, autoRescaleAmount, true);
    }

    private static Vec3d applyAxisLimit3_0(
            Vec3d currentMotion, float pitch, float yaw, double autoRescaleAmount, boolean apply) {
        if (currentMotion.lengthSquared() < 1E-6) {
            if (apply) setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        if (shouldAbortV3Optimize) {
            return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw, apply);
        }
        ;
        if (pitch > 0) {
            if (pitch > 60) {
                return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw, apply);
            } else if (pitch < 1) {
                return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw, apply);
            }
        } else {
            if (pitch < -70) {
                return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw, apply);
            } else if (pitch > -1) {
                return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw, apply);
            }
        }
        return applyAxisLimit30(currentMotion, pitch, yaw, autoRescaleAmount, apply);
    }

    private static Vec3d applyAxisLimit3_1(
            Vec3d currentMotion, float pitch, float yaw, double autoRescaleAmount, boolean apply) {
        if (currentMotion.lengthSquared() < 1E-6) {
            if (apply) setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        if (shouldAbortV3Optimize) {
            return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw, apply);
        }
        return applyAxisLimit30_Greedy(currentMotion, pitch, yaw, autoRescaleAmount, apply);
    }

    public static Vec3d calculateTowardsTargetV3Direction(ElytraExtra.Al al, Vec3d targetPosition, double speed) {
        Vec3d targetDirection = targetPosition.normalize();
        Vec2f pitchYaw = EntityUtils.rotationToPitchYaw(targetDirection);
        float p = pitchYaw.x;
        float y = pitchYaw.y;
        float startPitch;
        float endPitch;
        float minDelta = 1;
        float minDot = 3.0F;
        // todo: add up direction, add down direction
        if (pitchYaw.x > 0) {
            // downwards
            startPitch = 1.14F;
            endPitch = 85.8F;
        } else {
            startPitch = -85.6F;
            endPitch = -1.14F;
        }
        if (p < startPitch || p > endPitch) {
            return targetDirection;
        }
        float startDot;
        float endDot;

        Function<Float, Float> dotFunction = (pitch) -> {
            Vec3d simulateVec3d = EntityUtils.pitchYawToRotation(pitch, y).normalize();
            Vec3d tickSpeed = simulateAxisLimitSpeed(al, simulateVec3d.multiply(speed), pitch, y);
            float movingPitch = EntityUtils.rotationToPitch(tickSpeed.normalize());
            return movingPitch - p;
        };
        startDot = dotFunction.apply(startPitch);
        endDot = dotFunction.apply(endPitch);
        if (startDot * endDot >= 0) {
            // No Idea
            return targetDirection;
        }
        float midPitch = p;
        float midDot = dotFunction.apply(midPitch);
        while (!(Math.abs(midDot) < minDot || Math.abs(startPitch - endPitch) < minDelta)) {
            if (midDot == 0) break;
            if (midDot * startDot < 0) {
                endPitch = midPitch;
                endDot = midDot;
            } else {
                startPitch = midPitch;
                startDot = midDot;
            }
            midPitch = (startPitch + endPitch) / 2;
            midDot = dotFunction.apply(midPitch);
        }
        return EntityUtils.pitchYawToRotation(midPitch, y);
    }

    public static Vec3d calculateLookTowardsTargetV3Direction(ElytraExtra.Al al, Vec3d targetPosition, double speed) {
        Vec3d targetDirection = targetPosition.normalize();
        Vec2f pitchYaw = EntityUtils.rotationToPitchYaw(targetDirection);
        float p = pitchYaw.x;
        float y = pitchYaw.y;
        float startPitch;
        float endPitch;
        float minDelta = 1;
        float minDot = 3.0F;
        // todo: add up direction, add down direction
        if (pitchYaw.x > 0) {
            // downwards
            startPitch = 1.14F;
            endPitch = 85.8F;
        } else {
            startPitch = -85.6F;
            endPitch = -1.14F;
        }
        if (p < startPitch || p > endPitch) {
            return targetDirection;
        }
        float startDot;
        float endDot;

        Function<Float, Float> dotFunction = (pitch) -> {
            Vec3d simulateVec3d = EntityUtils.pitchYawToRotation(pitch, y).normalize();
            Vec3d tickSpeed = simulateAxisLimitSpeed(al, simulateVec3d.multiply(speed), pitch, y);
            Vec3d predictLook = targetPosition.subtract(tickSpeed);
            float realNeedPitch = EntityUtils.rotationToPitch(predictLook.normalize());
            return realNeedPitch - pitch;
        };
        startDot = dotFunction.apply(startPitch);
        endDot = dotFunction.apply(endPitch);
        if (startDot * endDot >= 0) {
            // No Idea
            return targetDirection;
        }
        float midPitch = p;
        float midDot = dotFunction.apply(midPitch);
        while (!(Math.abs(midDot) < minDot || Math.abs(startPitch - endPitch) < minDelta)) {
            if (midDot == 0) break;
            if (midDot * startDot < 0) {
                endPitch = midPitch;
                endDot = midDot;
            } else {
                startPitch = midPitch;
                startDot = midDot;
            }
            midPitch = (startPitch + endPitch) / 2;
            midDot = dotFunction.apply(midPitch);
        }
        return EntityUtils.pitchYawToRotation(midPitch, y);
    }

    public static Vec3d simulateAxisLimitSpeed(ElytraExtra.Al al, Vec3d currentMotion, float pitch, float yaw) {
        return switch (al) {
            case V1 -> ElytraExtra.INSTANCE.applyAxisLimit1(currentMotion, pitch, yaw, false);
            case V2 -> ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw, false);
            case V3 -> applyAxisLimit3_0(
                    currentMotion, pitch, yaw, ElytraExtra.INSTANCE.autoRescaleAmount.get(), false);
            case V3_PULLUP_ONLY -> applyAxisLimit3_1(
                    currentMotion, pitch, yaw, ElytraExtra.INSTANCE.autoRescaleAmount.get(), false);
            case V4 -> applyAxisLimit4_0(
                    currentMotion, pitch, yaw, ElytraExtra.INSTANCE.autoRescaleAmount.get(), false);
            case V5, V5_PULLUP_ONLY -> applyAxisLimit5_0(
                    currentMotion, pitch, yaw, ElytraExtra.INSTANCE.autoRescaleAmount.get(), false);
        };
    }

    public void onPreTravel() {}

    public static void onPreElytraMovement(Event<Vec3d> currentMotion) {
        if (packetToSend != null) {
            mc.getNetworkHandler().sendPacket(packetToSend);
            packetToSend = null;
        }
    }

    private static PlayerMoveC2SPacket packetToSend;

    public static Vec3d applyAxisLimit4(Vec3d currentMotion, float pitch, float yaw, double autoRescaleAmount) {
        return applyAxisLimit4_0(currentMotion, pitch, yaw, autoRescaleAmount, true);
    }

    public static Vec3d applyAxisLimit4_0(
            Vec3d currentMotion, float pitch, float yaw, double autoRescaleAmount, boolean apply) {
        if (currentMotion.lengthSquared() < 1E-6) {
            if (apply) setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        Vec3d currentRotation = EntityUtils.pitchYawToRotation(pitch, yaw);
        if (shouldAbortV3Optimize) {
            return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw, apply);
        }
        if (!ViaFabricPlusHooks.isSupportDupRot()) {
            return applyAxisLimit3_0(currentMotion, pitch, yaw, autoRescaleAmount, apply);
        }
        Vec3d extraTargeting = null;
        if (pitch > 0) {
            if (pitch > 60) {
                return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw, apply);
            } else {
                double len = 1.01 - Math.abs(currentRotation.y);
                Vec3d targetTo = new Vec3d(0, -len, 0);
                double lenSqr = targetTo.lengthSquared();
                if (lenSqr < 1) {
                    Vec3d horizontal =
                            EntityUtils.pitchYawToRotation(0, yaw).normalize().multiply(Math.sqrt(1 - lenSqr));
                    targetTo = targetTo.add(horizontal);
                }
                targetTo = targetTo.normalize();
                extraTargeting = targetTo;
            }
        } else {
            if (pitch > -7) {
                return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw, apply);
            } else {
                //
                Vec3d horizontal = new Vec3d(currentRotation.x, 0, currentRotation.z);
                Vec3d targetHorizontal = EntityUtils.pitchYawToRotation(0, yaw);
                double scale = Math.min(1.01 / Math.abs(targetHorizontal.x), 1.01 / Math.abs(targetHorizontal.z));
                targetHorizontal = targetHorizontal.multiply(scale);
                Vec3d targetTo = targetHorizontal.subtract(horizontal);
                double lenSqr = targetTo.lengthSquared();
                if (lenSqr < 1) {
                    targetTo = targetTo.add(0, Math.sqrt(1 - lenSqr), 0);
                }
                targetTo = targetTo.normalize();
                extraTargeting = targetTo;
            }
        }
        float lastPitch = PlayerStateManager.INSTANCE.lastPitch;
        float lastYaw = PlayerStateManager.INSTANCE.lastYaw;
        if (extraTargeting != null) {
            float extraPitch = EntityUtils.rotationToPitch(extraTargeting);
            float extraYaw = EntityUtils.rotationToYaw(extraTargeting);
            if (apply) packetToSend = LegacySnapRotManager.INSTANCE.createSnapAt(extraPitch, extraYaw);
            PlayerStateManager.INSTANCE.lastPitch = extraPitch;
            PlayerStateManager.INSTANCE.lastYaw = extraYaw;
        }
        Vec3d result = applyAxisLimit30(currentMotion, pitch, yaw, autoRescaleAmount, apply);
        PlayerStateManager.INSTANCE.lastPitch = lastPitch;
        PlayerStateManager.INSTANCE.lastYaw = lastYaw;
        return result;
    }

    public static Vec3d applyAxisLimit30_Greedy(
            Vec3d currentMotion, float pitch, float yaw, double autoRescaleAmount, boolean realApply) {
        Vec3d currentRotation = EntityUtils.pitchYawToRotation(pitch, yaw);
        Vec3d lastTickVelocity = PlayerStateManager.INSTANCE.lastKnownClientVelocity;
        Vec3d thisTickSimulationVelocity =
                PlayerStateManager.INSTANCE.lastInWater || PlayerStateManager.INSTANCE.lastInLava
                        ? EntityUtils.simulateTravelInFluidVelocity(
                                lastTickVelocity,
                                PlayerStateManager.INSTANCE.lastInWater,
                                PlayerStateManager.INSTANCE.lastInLava,
                                true)
                        : EntityUtils.calculateGlidingVelocity(mc.player, lastTickVelocity, currentRotation, true);

        // --- fireworksBox 构造 (保持不变) ---
        Vec3d lastPitchYaw = EntityUtils.pitchYawToRotation(
                PlayerStateManager.INSTANCE.lastPitch, PlayerStateManager.INSTANCE.lastYaw);
        double antiTickSkipping = 0.05;
        Vec3d currentLook = currentRotation.normalize();
        Vec3d lastLook = lastPitchYaw.normalize();
        double minX = Math.min(-antiTickSkipping, currentLook.getX()) + Math.min(-antiTickSkipping, lastLook.getX());
        double minY = Math.min(-antiTickSkipping, currentLook.getY()) + Math.min(-antiTickSkipping, lastLook.getY());
        double minZ = Math.min(-antiTickSkipping, currentLook.getZ()) + Math.min(-antiTickSkipping, lastLook.getZ());
        double maxX = Math.max(antiTickSkipping, currentLook.getX()) + Math.max(antiTickSkipping, lastLook.getX());
        double maxY = Math.max(antiTickSkipping, currentLook.getY()) + Math.max(antiTickSkipping, lastLook.getY());
        double maxZ = Math.max(antiTickSkipping, currentLook.getZ()) + Math.max(antiTickSkipping, lastLook.getZ());

        double threshold = Math.min(autoRescaleAmount, currentMotion.length());
        minX *= threshold;
        maxX *= threshold;
        minY *= threshold;
        maxY *= threshold;
        minZ *= threshold;
        maxZ *= threshold;
        minX = Math.max(-threshold, minX);
        maxX = Math.min(threshold, maxX);
        minY = Math.max(-threshold, minY);
        maxY = Math.min(threshold, maxY);
        minZ = Math.max(-threshold, minZ);
        maxZ = Math.min(threshold, maxZ);
        // Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        Vec3d v1 = lastTickVelocity;
        Vec3d v3 = thisTickSimulationVelocity;
        double eMinX = Math.min(0, minX - v1.x);
        double eMaxX = Math.max(0, maxX - v1.x);
        double eMinY = Math.min(0, minY - v1.y);
        double eMaxY = Math.max(0, maxY - v1.y);
        double eMinZ = Math.min(0, minZ - v1.z);
        double eMaxZ = Math.max(0, maxZ - v1.z);
        double zeroPointThreeTest = 0.0;
        double thresoldLeft = ElytraExtra.INSTANCE.autoRescaleThreshold.get();
        double uMinX = v3.x + eMinX - zeroPointThreeTest + thresoldLeft;
        double uMaxX = v3.x + eMaxX + zeroPointThreeTest - thresoldLeft;
        double uMinY = v3.y + eMinY + thresoldLeft;
        double uMaxY = v3.y + eMaxY - thresoldLeft;
        double uMinZ = v3.z + eMinZ - zeroPointThreeTest + thresoldLeft;
        double uMaxZ = v3.z + eMaxZ + zeroPointThreeTest - thresoldLeft;
        double dx = currentMotion.x, dz = currentMotion.z;
        double exceedX = 0.0, exceedZ = 0.0;

        if (dx > 0) exceedX = dx / uMaxX;
        else if (dx < 0) exceedX = dx / uMinX; // 注意 dx 为负，uMinX 也为负，比值 >1 若 dx < uMinX

        if (dz > 0) exceedZ = dz / uMaxZ;
        else if (dz < 0) exceedZ = dz / uMinZ;

        // ??????????????????????????????????????????????????????????????????????????
        // dy

        Vec3d clampedMotion = currentMotion;
        // todo : add more angle restrict
        if (clampedMotion.y > 0) {
            clampedMotion = clampedMotion.withAxis(Direction.Axis.Y, uMaxY);
        } else if (clampedMotion.y < 0) {
            clampedMotion = clampedMotion.withAxis(Direction.Axis.Y, uMinY);
        }
        if (Math.abs(uMaxX) > Math.abs(uMinX)) {
            clampedMotion = clampedMotion.withAxis(Direction.Axis.X, uMaxX);
        } else {
            clampedMotion = clampedMotion.withAxis(Direction.Axis.X, uMinX);
        }
        if (Math.abs(uMaxZ) > Math.abs(uMinZ)) {
            clampedMotion = clampedMotion.withAxis(Direction.Axis.Z, uMaxZ);
        } else {
            clampedMotion = clampedMotion.withAxis(Direction.Axis.Z, uMinZ);
        }
        clampedMotion = ElytraExtra.INSTANCE.applySpeedLimit(clampedMotion);
        // 已在盒内，无需缩放

        //        Debug.info(
        //            "check"
        //                + " current=" + currentMotion
        //
        //                + " exceedX=" + exceedX
        //                + " exceedZ=" + exceedZ
        //                + " uX=[" + uMinX + ", " + uMaxX + "]"
        //                + " uZ=[" + uMinZ + ", " + uMaxZ + "]"
        //                + " candidate=" + clampedMotion+ " current horizontal=" +clampedMotion.horizontalLength()
        //        );
        // 需要缩小至盒子边界
        if (realApply) setOverridingFireworkVelocity(clampedMotion); // <-- 保存边界值
        return clampedMotion;
    }

    public static Vec3d applyAxisLimit5_0(
            Vec3d currentMotion, float pitch, float yaw, double autoRescaleAmount, boolean apply) {
        if (currentMotion.lengthSquared() < 1E-6) {
            if (apply) setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        Vec3d currentRotation = EntityUtils.pitchYawToRotation(pitch, yaw);
        if (shouldAbortV3Optimize) {
            return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw, apply);
        }
        if (!ViaFabricPlusHooks.isSupportDupRot()) {
            return applyAxisLimit3_0(currentMotion, pitch, yaw, autoRescaleAmount, apply);
        }
        Vec3d extraTargeting = null;
        if (pitch > 0) {
            if (pitch > 60) {
                return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw, apply);
            } else {
                double len = 1.01 - Math.abs(currentRotation.y);
                Vec3d targetTo = new Vec3d(0, -len, 0);
                double lenSqr = targetTo.lengthSquared();
                if (lenSqr < 1) {
                    Vec3d horizontal =
                            EntityUtils.pitchYawToRotation(0, yaw).normalize().multiply(Math.sqrt(1 - lenSqr));
                    targetTo = targetTo.add(horizontal);
                }
                targetTo = targetTo.normalize();
                extraTargeting = targetTo;
            }
        } else {
            if (pitch > -7) {
                return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw, apply);
            } else {
                float extraYaw = getV5ExtraYaw(currentMotion, pitch, yaw, autoRescaleAmount);
                extraTargeting = EntityUtils.pitchYawToRotation(0.0F, extraYaw);
            }
        }
        float lastPitch = PlayerStateManager.INSTANCE.lastPitch;
        float lastYaw = PlayerStateManager.INSTANCE.lastYaw;
        if (extraTargeting != null) {
            float extraPitch = EntityUtils.rotationToPitch(extraTargeting);
            float extraYaw = EntityUtils.rotationToYaw(extraTargeting);
            if (apply) packetToSend = LegacySnapRotManager.INSTANCE.createSnapAt(extraPitch, extraYaw);
            PlayerStateManager.INSTANCE.lastPitch = extraPitch;
            PlayerStateManager.INSTANCE.lastYaw = extraYaw;
        }
        Vec3d result = applyAxisLimit30_Greedy(currentMotion, pitch, yaw, autoRescaleAmount, apply);
        PlayerStateManager.INSTANCE.lastPitch = lastPitch;
        PlayerStateManager.INSTANCE.lastYaw = lastYaw;
        return result;
    }

    static {
        Listener.getPlayerTravelingTick().registerHandler(ElytraOptimizeUtils::onPreElytraMovement);
    }
}
