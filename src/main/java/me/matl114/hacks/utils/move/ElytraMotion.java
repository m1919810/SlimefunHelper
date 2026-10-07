package me.matl114.hacks.utils.move;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.With;
import lombok.experimental.Accessors;
import me.matl114.hacks.modules.move.ElytraExtra;
import net.minecraft.util.math.Vec3d;

@Getter
@Setter
@ToString
@Accessors(chain = true, fluent = true)
public class ElytraMotion {
    @With
    private ElytraExtra.Al algorithm;

    private Vec3d rotation;

    @With
    private double speed;

    public ElytraMotion(ElytraExtra.Al algorithm, Vec3d rotation, double speed) {
        this.algorithm = algorithm;
        this.rotation = rotation;
        this.speed = speed;
    }

    public Vec3d rotation() {
        return rotation.normalize();
    }

    public ElytraMotion rotation(Vec3d rotation) {
        this.rotation = rotation.normalize();
        return this;
    }

    public ElytraMotion withRotation(Vec3d vec3d) {
        return new ElytraMotion(algorithm, vec3d.normalize(), speed);
    }
}
