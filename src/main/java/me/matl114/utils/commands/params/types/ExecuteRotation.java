package me.matl114.utils.commands.params.types;

import me.matl114.utils.EntityUtils;
import me.matl114.utils.commands.params.api.CommandExecution;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public interface ExecuteRotation {
    Vec2f getRotation(CommandExecution execution);

    String asString();

    static ExecuteRotation look() {
        return new Look();
    }

    static ExecuteRotation fixed(float yaw, float pitch) {
        return new Fixed(yaw, pitch);
    }

    static ExecuteRotation pos(ExecutePos pos) {
        return new Pos(pos);
    }

    static ExecuteRotation entity(EntitySelector selector) {
        return new EntityTarget(selector);
    }

    record Look() implements ExecuteRotation {
        @Override
        public Vec2f getRotation(CommandExecution execution) {
            if (execution.getExecutor() instanceof Entity entity) {
                return new Vec2f(entity.getPitch(), entity.getYaw());
            }
            return null;
        }

        @Override
        public String asString() {
            return "look";
        }
    }

    record Fixed(float yaw, float pitch) implements ExecuteRotation {
        @Override
        public Vec2f getRotation(CommandExecution execution) {
            return new Vec2f(pitch, yaw);
        }

        @Override
        public String asString() {
            return "%s %s".formatted(yaw, pitch);
        }
    }

    record Pos(ExecutePos pos) implements ExecuteRotation {
        @Override
        public Vec2f getRotation(CommandExecution execution) {
            if (!(execution.getExecutor() instanceof Entity entity) || pos == null) {
                return null;
            }
            Vector3d target = pos.getPosition(execution);
            Vec3d delta = new Vec3d(target.x, target.y, target.z).subtract(entity.getEyePos());
            return delta.lengthSquared() <= 1.0E-7 ? null : EntityUtils.rotationToPitchYaw(delta.normalize());
        }

        @Override
        public String asString() {
            return pos == null ? "pos" : "pos " + pos.asString();
        }
    }

    record EntityTarget(EntitySelector selector) implements ExecuteRotation {
        @Override
        public Vec2f getRotation(CommandExecution execution) {
            if (!(execution.getExecutor() instanceof Entity entity) || selector == null) {
                return null;
            }
            Entity target = selector.random(execution);
            if (target == null) {
                return null;
            }
            Vec3d delta = target.getEyePos().subtract(entity.getEyePos());
            return delta.lengthSquared() <= 1.0E-7 ? null : EntityUtils.rotationToPitchYaw(delta.normalize());
        }

        @Override
        public String asString() {
            return selector == null ? "entity" : "entity " + selector.asString();
        }
    }
}