package me.matl114.utils.commands.params.types;

import me.matl114.utils.commands.params.api.CommandExecution;
import net.minecraft.util.math.Vec2f;
import org.joml.Vector2f;

public interface ExecuteRotation {
    Vector2f getRotation(CommandExecution execution);

    default String asString() {
        Vector2f value = getRotation(null);
        return "%s %s".formatted(formatAbsolute(value.x()), formatAbsolute(value.y()));
    }

    static ExecuteRotation current() {
        return new RelativePitchYaw(3, new Vec2f(0.0F, 0.0F));
    }

    static ExecuteRotation fixed(float pitch, float yaw) {
        return new Fixed(new Vec2f(pitch, yaw));
    }

    static ExecuteRotation of(Vec2f rotation) {
        return new Fixed(rotation == null ? new Vec2f(0.0F, 0.0F) : rotation);
    }

    static ExecuteRotation ofExecutor(CommandExecution execution) {
        Vector2f vec2f = execution.getExecuteRot();
        return new Fixed(new Vec2f(vec2f.x, vec2f.y));
    }

    static ExecuteRotation relative(int flag, float pitch, float yaw) {
        return new RelativePitchYaw(flag, new Vec2f(pitch, yaw));
    }

    record Fixed(Vec2f rotation) implements ExecuteRotation {
        @Override
        public Vector2f getRotation(CommandExecution execution) {
            return rotation == null ? new Vector2f(0.0F, 0.0F) : new Vector2f(rotation.x, rotation.y);
        }
    }

    record RelativePitchYaw(int flag, Vec2f rotation) implements ExecuteRotation {
        @Override
        public Vector2f getRotation(CommandExecution execution) {
            Vector2f base = execution.getExecuteRot();
            Vec2f value = rotation == null ? new Vec2f(0.0F, 0.0F) : rotation;
            return new Vector2f(
                    (flag & 1) != 0 ? base.x + value.x : value.x, (flag & 2) != 0 ? base.y + value.y : value.y);
        }

        @Override
        public String asString() {
            Vec2f value = rotation == null ? new Vec2f(0.0F, 0.0F) : rotation;
            return "%s %s".formatted(formatPart((flag & 1) != 0, value.x), formatPart((flag & 2) != 0, value.y));
        }
    }

    private static String formatPart(boolean relative, float value) {
        if (relative) {
            return value == 0.0F ? "~" : "~" + formatAbsolute(value);
        }
        return formatAbsolute(value);
    }

    private static String formatAbsolute(float value) {
        return value == (long) value ? String.valueOf((long) value) : "%.1f".formatted(value);
    }
}
