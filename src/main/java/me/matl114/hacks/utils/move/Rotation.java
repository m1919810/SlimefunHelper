package me.matl114.hacks.utils.move;

import javax.annotation.Nullable;
import lombok.With;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.utils.EntityUtils;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

@With
public record Rotation(@Nullable Float pitch, @Nullable Float yaw) {
    public static Rotation EMPTY = new Rotation();

    private Rotation() {
        this(null, null);
    }

    public static Rotation from(Vec2f vec2f) {
        return new Rotation(vec2f.x, vec2f.y);
    }

    public static Rotation from(Vec3d vec3d) {
        if (vec3d.horizontalLength() == 0) {
            return new Rotation(EntityUtils.rotationToPitch(vec3d), null);
        }
        var vec2f = EntityUtils.rotationToPitchYaw(vec3d);
        return new Rotation(vec2f.x, vec2f.y);
    }

    public static Rotation from(float pitch, float yaw) {
        return new Rotation(pitch, yaw);
    }

    public float getPitch() {
        return pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public boolean hasPitch() {
        return pitch != null;
    }

    public boolean hasYaw() {
        return yaw != null;
    }

    public boolean hasRotate() {
        return hasPitch() || hasYaw();
    }

    public float getPitch(float pitch) {
        return hasPitch() ? getPitch() : pitch;
    }

    public float getYaw(float pitch) {
        return hasYaw() ? getYaw() : pitch;
    }

    public Vec2f toPitchYaw() {
        if (!hasPitch() && !hasYaw()) {
            return null;
        }
        return new Vec2f(getPitch(PlayerStateManager.INSTANCE.lastPitch), getYaw(PlayerStateManager.INSTANCE.lastYaw));
    }
}
