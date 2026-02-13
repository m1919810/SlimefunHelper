package me.matl114.versioned.api;

import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@ApiStatus.Experimental
public interface VertexConsumer {
    VertexConsumer vertex(float x, float y, float z);

    VertexConsumer color(int red, int green, int blue, int alpha);

    VertexConsumer color(int argb);

    VertexConsumer texture(float u, float v);

    VertexConsumer overlay(int u, int v);

    VertexConsumer light(int u, int v);

    VertexConsumer normal(float x, float y, float z);

    VertexConsumer lineWidth(float width);

    default VertexConsumer vertex(Vector3fc vec) {
        return this.vertex(vec.x(), vec.y(), vec.z());
    }

    default VertexConsumer vertex(net.minecraft.client.util.math.MatrixStack.Entry matrix, Vector3f vec) {
        return this.vertex(matrix, vec.x(), vec.y(), vec.z());
    }

    default VertexConsumer vertex(net.minecraft.client.util.math.MatrixStack.Entry matrix, float x, float y, float z) {
        return this.vertex((Matrix4fc) matrix.getPositionMatrix(), x, y, z);
    }

    default VertexConsumer vertex(Matrix4fc matrix, float x, float y, float z) {
        Vector3f vector3f = matrix.transformPosition(x, y, z, new Vector3f());
        return this.vertex(vector3f.x(), vector3f.y(), vector3f.z());
    }

    default VertexConsumer normal(net.minecraft.client.util.math.MatrixStack.Entry matrix, float x, float y, float z) {
        Vector3f vector3f = matrix.transformNormal(x, y, z, new Vector3f());
        return this.normal(vector3f.x(), vector3f.y(), vector3f.z());
    }

    default VertexConsumer normal(MatrixStack.Entry matrix, Vector3f vec) {
        return this.normal(matrix, vec.x(), vec.y(), vec.z());
    }

    public void submit();
}
