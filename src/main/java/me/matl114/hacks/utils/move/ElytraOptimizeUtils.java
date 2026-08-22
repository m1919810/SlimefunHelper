package me.matl114.hacks.utils.move;

import me.matl114.hacks.modules.move.ElytraExtra;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.utils.EntityUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class ElytraOptimizeUtils {
    public static final MinecraftClient mc = MinecraftClient.getInstance();
    public static boolean shouldAbortV3Optimize = false;
    public static ElytraExtra.Al lastAl = ElytraExtra.Al.V3;

    public static void toggleElytraAl() {
        if (ElytraExtra.INSTANCE.autoRescaleAl.get().isIn(ElytraExtra.Al.V3)) {
            lastAl = ElytraExtra.INSTANCE.autoRescaleAl.get();
            ElytraExtra.INSTANCE.autoRescaleAl.set(ElytraExtra.Al.V2);
        } else {
            ElytraExtra.INSTANCE.autoRescaleAl.set(lastAl);
        }
    }

    public static void setOverridingFireworkVelocity(Vec3d vec3d) {
        ElytraExtra.INSTANCE.setOverridingFireworkVelocity(vec3d);
    }

    public static Vec3d applyAxisLimit30(Vec3d currentMotion, float pitch, float yaw, double autoRescaleAmount) {
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
        double uMinX = v3.x + eMinX - zeroPointThreeTest;
        double uMaxX = v3.x + eMaxX + zeroPointThreeTest;
        double uMinY = v3.y + eMinY;
        double uMaxY = v3.y + eMaxY;
        double uMinZ = v3.z + eMinZ - zeroPointThreeTest;
        double uMaxZ = v3.z + eMaxZ + zeroPointThreeTest;
        // I dont understand.
        if (!ViaFabricPlusHooks.isSupportEndTick()) {
            if (uMaxY > 1E-6) {
                double len = currentRotation.length();
                double horizontalLen = currentRotation.horizontalLength();
                if (horizontalLen < 0.04 * currentRotation.y) {
                    double max = EntityUtils.calculateGlidingVelocity(
                                    mc.player,
                                    currentMotion.multiply(
                                            (len + ElytraExtra.INSTANCE.autoRescaleZeroPointThreeY.get()) / len),
                                    currentRotation,
                                    true)
                            .y;
                    uMaxY = Math.max(uMaxY, max);
                }
            }
        }
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
            setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        Vec3d predictedMotion = EntityUtils.calculateGlidingVelocity(mc.player, currentMotion, currentRotation, true);
        Vec3d clampedMotion = currentMotion.multiply(1 / maxScale);
        // todo : add more angle restrict
        if (clampedMotion.y > 0) {
            clampedMotion = clampedMotion.withAxis(Direction.Axis.Y, uMaxY);
        } else if (clampedMotion.y < 0) {
            clampedMotion = clampedMotion.withAxis(Direction.Axis.Y, uMinY);
        }
        // 已在盒内，无需缩放
        if (clampedMotion.lengthSquared() < predictedMotion.lengthSquared()) {
            setOverridingFireworkVelocity(null);
            return currentMotion;
        }

        // 需要缩小至盒子边界

        setOverridingFireworkVelocity(clampedMotion); // <-- 保存边界值
        return currentMotion;
    }

    public static Vec3d applyAxisLimit3(Vec3d currentMotion, float pitch, float yaw, double autoRescaleAmount) {
        if (currentMotion.lengthSquared() < 1E-6) {
            setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        if (shouldAbortV3Optimize) {
            return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw);
        }
        ;
        if (pitch > 0) {
            if (pitch > 60) {
                return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw);
            } else if (pitch < 6) {
                return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw);
            }
        } else {
            if (pitch < -70) {
                return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw);
            } else if (pitch > -7) {
                return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw);
            }
        }
        return applyAxisLimit30(currentMotion, pitch, yaw, autoRescaleAmount);
    }
}
