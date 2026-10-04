package me.matl114.versioned.impl;

import java.util.Arrays;
import me.matl114.versioned.api.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class MatrixStackImpl implements MatrixStack {
    private static final int INITIAL_CAPACITY = 8;

    private Matrix3x2f[] matrices = createMatrices(INITIAL_CAPACITY);
    private int position;

    public MatrixStackImpl(MatrixStack initial) {
        matrices[0].set(toMatrix2D(initial.peek3D()));
    }

    public MatrixStackImpl(Matrix3x2f initial) {
        matrices[0].set(initial);
    }

    @Override
    public void pushMatrix() {
        ensureCapacity();
        matrices[position + 1].set(matrices[position]);
        position++;
    }

    @Override
    public void popMatrix() {
        if (position == 0) {
            throw new IllegalStateException("Cannot pop the base matrix");
        }
        position--;
    }

    @Override
    public Matrix4f peek3D() {
        Matrix3x2f matrix = matrices[position];
        return new Matrix4f()
                .identity()
                .m00(matrix.m00())
                .m01(matrix.m01())
                .m10(matrix.m10())
                .m11(matrix.m11())
                .m30(matrix.m20())
                .m31(matrix.m21());
    }

    Matrix3x2f peek() {
        return matrices[position];
    }

    @Override
    public Matrix3f peekNormal() {
        return new Matrix3f().identity();
    }

    @Override
    public void translate(float x, float y) {
        matrices[position].translate(x, y);
    }

    @Override
    public void scale(float x, float y) {
        matrices[position].scale(x, y);
    }

    @Override
    public void multiply3D(Quaternionf quaternion) {
        Matrix4f matrix = peek3D().mul(new Matrix4f().rotate(quaternion));
        matrices[position].set(matrix.m00(), matrix.m01(), matrix.m10(), matrix.m11(), matrix.m30(), matrix.m31());
    }

    @Override
    public void set(Matrix3x2f matrix) {
        matrices[position].set(matrix);
    }

    private void ensureCapacity() {
        if (position + 1 < matrices.length) {
            return;
        }
        int previousLength = matrices.length;
        matrices = Arrays.copyOf(matrices, previousLength * 2);
        for (int index = previousLength; index < matrices.length; index++) {
            matrices[index] = new Matrix3x2f();
        }
    }

    private static Matrix3x2f[] createMatrices(int capacity) {
        Matrix3x2f[] matrices = new Matrix3x2f[capacity];
        for (int index = 0; index < capacity; index++) {
            matrices[index] = new Matrix3x2f();
        }
        return matrices;
    }

    private static Matrix3x2f toMatrix2D(Matrix4f matrix) {
        return new Matrix3x2f(matrix.m00(), matrix.m01(), matrix.m10(), matrix.m11(), matrix.m30(), matrix.m31());
    }
}
