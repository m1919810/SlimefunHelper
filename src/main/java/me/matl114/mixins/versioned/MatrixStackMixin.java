package me.matl114.mixins.versioned;

import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MatrixStack.class)
public abstract class MatrixStackMixin implements me.matl114.versioned.api.MatrixStack {
    @Shadow
    public abstract void push();

    @Shadow
    public abstract void pop();

    @Shadow
    public abstract MatrixStack.Entry peek();

    @Shadow
    public abstract void translate(float x, float y, float z);

    @Shadow
    public abstract void scale(float x, float y, float z);

    @Shadow
    public abstract void multiply(Quaternionf quaternion);

    public void pushMatrix() {
        this.push();
    }

    public void popMatrix() {
        this.pop();
    }

    public Matrix4f peek3D() {
        return this.peek().getPositionMatrix();
    }

    public Matrix3f peekNormal(){
        return this.peek().getNormalMatrix();
    }

    public void translate(float x, float y) {
        this.translate(x, y, 0);
    }

    public void translateZ(float z) {
        this.translate(0, 0, z);
    }

    public void scale(float x, float y) {
        this.scale(x, y, 1);
    }

    public void multiply3D(Quaternionf quaternion) {
        this.multiply(quaternion);
    }
}
