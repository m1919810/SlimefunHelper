package com.jsmacrosce.jsmacros.client.api.classes.math;

import net.minecraft.util.math.Vec3d;

public class Pos3D extends Pos2D {
    public double z;

    public Pos3D(Vec3d vec) {
        this(vec.getX(), vec.getY(), vec.getZ());
    }

    public Pos3D(double x, double y, double z) {
        super(x, y);
        this.z = z;
    }

    public double getZ() {
        return z;
    }
}
