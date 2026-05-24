package me.matl114.hacks.utils.move;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import net.minecraft.util.math.Vec3d;

@Getter
@Setter
@ToString
@Accessors(chain = true, fluent = true)
public class FlightVelocity {
    double x, y, z;
    final double maxVelocity;
    final Mode mode;

    public FlightVelocity(double x, double y, double z, double maxVelocity, Mode mode) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.maxVelocity = maxVelocity;
        this.mode = mode;
    }

    public void velocity(Vec3d vec3d) {
        this.x = vec3d.x;
        this.y = vec3d.y;
        this.z = vec3d.z;
    }

    public FlightVelocity(Vec3d vec, double maxVelocity, Mode mode) {
        this(vec.x, vec.y, vec.z, maxVelocity, mode);
    }

    public Vec3d toVelocity() {
        return new Vec3d(x, y, z);
    }

    public enum Mode {
        ELYTRA_FLIGHT,
        MOTION_FLIGHT;
    }
}
