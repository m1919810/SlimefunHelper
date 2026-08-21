package me.matl114.hacks.utils.move;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hacks.modules.move.ElytraExtra;
import me.matl114.hacks.modules.move.LegacySnapRotManager;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hooks.ViaFabricPlusHooks;
import me.matl114.utils.EntityUtils;
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
    public static ElytraExtra.Al lastAl = ElytraExtra.Al.V3;

    public static void toggleElytraAl() {
        if (ElytraExtra.INSTANCE.autoRescaleAl.get().isIn(ElytraExtra.Al.V3, ElytraExtra.Al.V4)) {
            lastAl = ElytraExtra.INSTANCE.autoRescaleAl.get();
            ElytraExtra.INSTANCE.autoRescaleAl.set(ElytraExtra.Al.V2);
        } else {
            ElytraExtra.INSTANCE.autoRescaleAl.set(lastAl);
        }
    }

    public static Vec3d calculateBestPullupSpeed(Vec3d vec3d) {
        if (ElytraExtra.INSTANCE.autoRescaleAl.get().isIn(ElytraExtra.Al.V3)) {
            return calculateBestV3ClimbingSpeed(vec3d);
        }
        if (ElytraExtra.INSTANCE.autoRescaleAl.get().isIn(ElytraExtra.Al.V4)) {
            return calculateBestV4ClimbingSpeed(vec3d);
        }
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
        return vec3d.withAxis(Direction.Axis.Y, newY);
    }

    public static Vec3d calculateBestV3ClimbingSpeed(Vec3d rotation) {
        Vec2f py = EntityUtils.rotationToPitchYaw(rotation);
        float pitchDeg = py.x;
        float yawDeg = py.y;

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
        return EntityUtils.pitchYawToRotation(newPitchDeg, yawDeg);
    }

    public static Vec3d calculateBestV4ClimbingSpeed(Vec3d rotation) {
        Vec2f py = EntityUtils.rotationToPitchYaw(rotation);
        float yawDeg = py.y;
        double yawRad = Math.toRadians(yawDeg);
        double sinY = Math.abs(Math.sin(yawRad));
        double cosY = Math.abs(Math.cos(yawRad));
        double m = Math.max(sinY, cosY);

        // applyAxisLimit4 refreshes lastLook via snapAt(...), so the climb-side
        // horizontal saturation boundary changes from cos(pitch)=0.5/M to
        // cos(pitch)=1/M-1.
        double cosPitchCrit = Math.max(-1.0, Math.min(1.0, 1.0 / m - 1.0));
        float newPitchDeg = (float) -Math.toDegrees(Math.acos(cosPitchCrit));
        return EntityUtils.pitchYawToRotation(Math.clamp(newPitchDeg, -88, 88), yawDeg);
    }

    public static Vec3d calculateBestDownForwardSpeed(Vec3d vec3d, boolean natural) {
        if (ElytraExtra.INSTANCE.autoRescaleAl.get().isIn(ElytraExtra.Al.V3)) {
            return calculateBestV3DownForwardSpeed(vec3d, natural);
        }
        if (ElytraExtra.INSTANCE.autoRescaleAl.get().isIn(ElytraExtra.Al.V4)) {
            return calculateBestV4DownForwardSpeed(vec3d, natural);
        }
        return vec3d;
    }

    public static Vec3d calculateBestV3DownForwardSpeed(Vec3d vec3d, boolean natural) {
        double horizontal = vec3d.horizontalLength();
        if (horizontal < 1E-6) {
            vec3d = vec3d.withAxis(Direction.Axis.X, 5);
            horizontal = vec3d.horizontalLength();
        }
        Vec2f py = EntityUtils.rotationToPitchYaw(vec3d);
        if (py.x > 60) {
            return vec3d;
        }
        double pitchDeg = 30.5;
        if (natural && py.x < pitchDeg) {
            return vec3d;
        }
        double pitchRad = Math.toRadians(pitchDeg);
        // 使用 -tan(pitch) 来抵消符号，或者直接用 tan(54.5)
        double newY = -horizontal * Math.tan(pitchRad);
        // 等价写法：double newY = horizontal * Math.tan(Math.toRadians(54.5));

        // 4. 返回新的向量（保持 x 和 z 不变，仅替换 y）
        return vec3d.withAxis(Direction.Axis.Y, newY);
    }

    public static Vec3d calculateBestV4DownForwardSpeed(Vec3d vec3d, boolean natural) {
        double horizontal = vec3d.horizontalLength();
        if (horizontal < 1E-6) {
            vec3d = vec3d.withAxis(Direction.Axis.X, 5);
            horizontal = vec3d.horizontalLength();
        }
        Vec2f py = EntityUtils.rotationToPitchYaw(vec3d);
        if (py.x < -70) {
            return vec3d;
        }
        double pitchDeg = -30.5;
        if (natural && py.x > pitchDeg) {
            return vec3d;
        }
        return EntityUtils.pitchYawToRotation(5, py.y);
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
        double thresoldLeft = 0.002;
        double uMinX = v3.x + eMinX - zeroPointThreeTest + thresoldLeft;
        double uMaxX = v3.x + eMaxX + zeroPointThreeTest - thresoldLeft;
        double uMinY = v3.y + eMinY + thresoldLeft;
        double uMaxY = v3.y + eMaxY - thresoldLeft;
        double uMinZ = v3.z + eMinZ - zeroPointThreeTest + thresoldLeft;
        double uMaxZ = v3.z + eMaxZ + zeroPointThreeTest - thresoldLeft;
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

    public void onPreTravel() {}

    public static void onPreElytraMovement(Event<Vec3d> currentMotion) {
        if (packetToSend != null) {
            mc.getNetworkHandler().sendPacket(packetToSend);
            packetToSend = null;
        }
    }

    private static PlayerMoveC2SPacket packetToSend;

    public static Vec3d applyAxisLimit4(Vec3d currentMotion, float pitch, float yaw, double autoRescaleAmount) {
        if (currentMotion.lengthSquared() < 1E-6) {
            setOverridingFireworkVelocity(null);
            return currentMotion;
        }
        Vec3d currentRotation = EntityUtils.pitchYawToRotation(pitch, yaw);
        if (shouldAbortV3Optimize) {
            return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw);
        }
        if (!ViaFabricPlusHooks.isSupportDupRot()) {
            return applyAxisLimit3(currentMotion, pitch, yaw, autoRescaleAmount);
        }
        Vec3d extraTargeting = null;
        if (pitch > 0) {
            if (pitch > 60) {
                return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw);
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
                return ElytraExtra.INSTANCE.applyAxisLimit2(currentMotion, pitch, yaw);
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
        if (extraTargeting != null) {
            float extraPitch = EntityUtils.rotationToPitch(extraTargeting);
            packetToSend = LegacySnapRotManager.INSTANCE.createSnapAt(extraPitch, yaw);
            PlayerStateManager.INSTANCE.lastPitch = extraPitch;
        }
        Vec3d result = applyAxisLimit30(currentMotion, pitch, yaw, autoRescaleAmount);
        PlayerStateManager.INSTANCE.lastPitch = lastPitch;
        return result;
    }

    static {
        Listener.getPlayerTravelingTick().registerHandler(ElytraOptimizeUtils::onPreElytraMovement);
    }
}
