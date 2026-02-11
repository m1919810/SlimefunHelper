package me.matl114.mixins.versioned;

import me.matl114.versioned.api.MatrixStack;
import net.minecraft.client.gui.DrawContext;
import org.joml.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DrawContext.class)
public abstract class DrawContextMixin implements MatrixStack {
    @Shadow
    @Final
    private Matrix3x2fStack matrices;

    @Override
    public void pushMatrix() {
        this.matrices.pushMatrix();
    }

    @Override
    public void popMatrix() {
        this.matrices.popMatrix();
    }

    @Override
    public Matrix4f peek3D() {
        Matrix3x2fStack mat2d = this.matrices;
        Matrix4f mat4 = new Matrix4f().identity();

        // 1. 复制线性变换部分 (旋转/缩放)
        mat4.m00(mat2d.m00());
        mat4.m01(mat2d.m01());
        mat4.m10(mat2d.m10());
        mat4.m11(mat2d.m11());

        // 2. 复制平移部分
        mat4.m30(mat2d.m20()); // 注意：Matrix3x2f的m20对应Matrix4f的m30 (平移X)
        mat4.m31(mat2d.m21()); // 注意：Matrix3x2f的m21对应Matrix4f的m31 (平移Y)

        // 3. 其他部分在identity()中已正确设置：
        //    - Z轴相关 (m22=1, m02/m12/m32=0)
        //    - 齐次坐标行 (m03=m13=m23=0, m33=1)

        return mat4;
    }

    @Override
    public void translate(float x, float y) {
        this.matrices.translate(x, y);
    }

    @Override
    public void translateZ(float z) {}

    @Override
    public void scale(float x, float y) {
        this.matrices.scale(x, y);
    }

    @Override
    public void multiply3D(Quaternionf quaternion) {
        Matrix4f mat4 = peek3D();
        Matrix4f mat3 = new Matrix4f().rotate(quaternion);
        mat4.mul(mat3);
        Matrix3x2f result = new Matrix3x2f(
                mat4.m00(), mat4.m01(),
                mat4.m10(), mat4.m11(),
                mat4.m30(), mat4.m31());
        this.matrices.set(result);
    }

    public Matrix3f peekNormal() {
        return new Matrix3f().identity();
    }
}
