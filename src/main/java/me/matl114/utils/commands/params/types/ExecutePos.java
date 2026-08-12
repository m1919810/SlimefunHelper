package me.matl114.utils.commands.params.types;

import me.matl114.utils.commands.params.api.CommandExecution;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;
import org.joml.Vector3d;

public interface ExecutePos {
    public Vector3d getPosition(CommandExecution executor);

    public String asString();

    public static ExecutePos of(Vec3d vec3d) {
        return new Fixed(new Vector3d(vec3d.x, vec3d.y, vec3d.z));
    }

    public static ExecutePos ofExecutor(CommandExecution executor) {
        return new Fixed(executor.getExecutePos());
    }

    public record Fixed(Vector3d vector3d) implements ExecutePos {

        @Override
        public Vector3d getPosition(CommandExecution executor) {
            return vector3d;
        }

        @Override
        public String asString() {
            return "%.2f %.2f %.2f".formatted(vector3d.x, vector3d.y, vector3d.z);
        }
    }

    public record RelativeXYZ(int flag, Vector3d vector3d) implements ExecutePos {

        @Override
        public Vector3d getPosition(CommandExecution executor) {
            Vector3d executorPos = executor.getExecutePos();
            double x, y, z;
            if ((flag & 1) != 0) {
                x = executorPos.x() + vector3d.x();
            } else {
                x = vector3d.x();
            }
            if ((flag & 2) != 0) {
                y = executorPos.y() + vector3d.y();
            } else {
                y = vector3d.y();
            }
            if ((flag & 4) != 0) {
                z = executorPos.z() + vector3d.z();
            } else {
                z = vector3d.z();
            }
            return new Vector3d(x, y, z);
        }

        @Override
        public String asString() {
            StringBuilder builder = new StringBuilder();
            if ((flag & 1) != 0) {
                builder.append("~");
            }
            if (vector3d.x() != 0) builder.append("%.1f".formatted(vector3d.x()));
            builder.append(" ");
            if ((flag & 2) != 0) {
                builder.append("~");
            }
            if (vector3d.y() != 0) builder.append("%.1f".formatted(vector3d.y()));
            builder.append(" ");
            if ((flag & 4) != 0) {
                builder.append("~");
            }
            if (vector3d.z() != 0) builder.append("%.1f".formatted(vector3d.z()));
            return builder.toString();
        }
    }

    public record RelativeRotateXYZ(Vector3d vector3d) implements ExecutePos {
        @Override
        public Vector3d getPosition(CommandExecution executor) {
            Vector3d executorPos = executor.getExecutePos();
            Vector2f vec2 = executor.getExecuteRot();
            return executorPos.add(lookCoordTooPos(vec2.x, vec2.y, vector3d.x, vector3d.y, vector3d.z));
        }

        @Override
        public String asString() {
            StringBuilder builder = new StringBuilder();

            builder.append("^");
            if (vector3d.x() != 0) builder.append("%.1f".formatted(vector3d.x()));
            builder.append(" ");
            builder.append("^");
            if (vector3d.y() != 0) builder.append("%.1f".formatted(vector3d.y()));
            builder.append(" ");
            builder.append("^");
            if (vector3d.z() != 0) builder.append("%.1f".formatted(vector3d.z()));
            return builder.toString();
        }

        public static Vector3d lookCoordTooPos(float pitch, float yaw, double x, double y, double z) {
            Vector2f vec2f = new Vector2f(pitch, yaw);
            double f = Math.cos((vec2f.y + 90.0F) * 0.017453292F);
            double g = Math.sin((vec2f.y + 90.0F) * 0.017453292F);
            double h = Math.cos(-vec2f.x * 0.017453292F);
            double i = Math.sin(-vec2f.x * 0.017453292F);
            double j = Math.cos((-vec2f.x + 90.0F) * 0.017453292F);
            double k = Math.sin((-vec2f.x + 90.0F) * 0.017453292F);
            Vector3d vec3d2 = new Vector3d((double) (f * h), (double) i, (double) (g * h));
            Vector3d vec3d3 = new Vector3d((double) (f * j), (double) k, (double) (g * j));
            Vector3d vec3d4 = new Vector3d(vec3d2).cross(new Vector3d(vec3d3)).mul(-1.0);
            double d = vec3d2.x * z + vec3d3.x * y + vec3d4.x * x;
            double e = vec3d2.y * z + vec3d3.y * y + vec3d4.y * x;
            double l = vec3d2.z * z + vec3d3.z * y + vec3d4.z * x;
            return new Vector3d(d, e, l);
        }
    }
}
