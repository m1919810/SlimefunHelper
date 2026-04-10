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
public class ElytraVelocity {
    double x, y, z;

    public ElytraVelocity(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public ElytraVelocity(Vec3d vec) {
        this(vec.x, vec.y, vec.z);
    }

    public Vec3d toVelocity() {
        return new Vec3d(x, y, z);
    }
}
