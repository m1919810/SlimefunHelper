package me.matl114.hacks.utils.move;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.With;
import lombok.experimental.Accessors;
import me.matl114.hacks.modules.move.ElytraExtra;
import me.matl114.hacks.utils.EntityUtils;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

@Getter
@Setter
@ToString
@Accessors(chain = true, fluent = true)
public class ElytraMotion {
    @With
    private ElytraExtra.Al algorithm;

    private float pitch;

    private float yaw;

    @With
    private double speed;

    private final double maxSpeed;

    public ElytraMotion(ElytraExtra.Al algorithm, Vec3d rotation, double speed, double maxSpeed) {
        this(algorithm, rotationToPitch(rotation), rotationToYaw(rotation), speed, maxSpeed);
    }

    public ElytraMotion(ElytraExtra.Al algorithm, Vec2f rotation, double speed, double maxSpeed) {
        this(algorithm, rotation.x, rotation.y, speed, maxSpeed);
    }

    public ElytraMotion(ElytraExtra.Al algorithm, float pitch, float yaw, double speed, double maxSpeed) {
        this.algorithm = algorithm;
        this.pitch = pitch;
        this.yaw = yaw;
        this.speed = speed;
        this.maxSpeed = maxSpeed;
    }

    public Vec3d rotation() {
        return EntityUtils.pitchYawToRotation(pitch, yaw);
    }

    public Vec3d movement() {
        return rotation().multiply(speed);
    }

    public ElytraMotion rotation(Vec3d rotation) {
        Vec2f pitchYaw = rotationToPitchYaw(rotation, yaw);
        this.pitch = pitchYaw.x;
        this.yaw = pitchYaw.y;

        return this;
    }

    public ElytraMotion rotation(Vec2f rotation) {
        this.pitch = rotation.x;
        this.yaw = rotation.y;

        return this;
    }

    public ElytraMotion rotation(float pitch, float yaw) {
        this.pitch = pitch;
        this.yaw = yaw;

        return this;
    }

    public ElytraMotion speed(double speed) {
        this.speed = Math.clamp(speed, 0, maxSpeed);
        return this;
    }

    public ElytraMotion withRotation(Vec3d vec3d) {
        return new ElytraMotion(algorithm, rotationToPitchYaw(vec3d, yaw), speed, maxSpeed);
    }

    public ElytraMotion withRotation(Vec2f rotation) {
        return new ElytraMotion(algorithm, rotation, speed, maxSpeed);
    }

    public ElytraMotion withRotation(float pitch, float yaw) {
        return new ElytraMotion(algorithm, pitch, yaw, speed, maxSpeed);
    }

    public Vec2f rotationAngles() {
        return new Vec2f(pitch, yaw);
    }

    private static Vec2f rotationToPitchYaw(Vec3d rotation) {
        return rotationToPitchYaw(rotation, 0.0F);
    }

    private static Vec2f rotationToPitchYaw(Vec3d rotation, float verticalYaw) {
        if (rotation.horizontalLengthSquared() < 1E-12) {
            return new Vec2f(rotation.y >= 0.0D ? -90.0F : 90.0F, verticalYaw);
        }
        Vec3d normalized = rotation.normalize();
        return EntityUtils.rotationToPitchYaw(normalized);
    }

    private static float rotationToPitch(Vec3d rotation) {
        return rotationToPitchYaw(rotation).x;
    }

    private static float rotationToYaw(Vec3d rotation) {
        return rotationToPitchYaw(rotation).y;
    }
}
