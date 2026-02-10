package me.matl114.versioned.api;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

// matrix stack for GUI, deprecate depth test now, but not then
public interface MatrixStack {
    public void pushMatrix();

    public void popMatrix();

    public Matrix4f peek3D();

    public void translate(float x, float y);

    public void translateZ(float z);

    public void scale(float x, float y);

    public void multiply3D(Quaternionf quaternion);

    static MatrixStack of(net.minecraft.client.util.math.MatrixStack matrixStack) {
        return (MatrixStack) matrixStack;
    }
}
