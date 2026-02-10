package me.matl114.versioned.api;

import java.awt.*;
import java.util.List;
import me.matl114.versioned.impl.Render_v1_21_1;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public interface VRender {
    public static final VRender INSTANCE = new Render_v1_21_1();

    public static VRender getInstance() {
        return INSTANCE;
    }

    public void drawStripLineVirtual(MatrixStack matrixStack, List<Vec3d> path, Color color);

    public void drawLineVirtual(MatrixStack matrixStack, List<Vec3d> pairs, Color color);

    public void drawLineVirtualCameraCoord(MatrixStack matrixStack, List<Vec3d> pairs, Color color);

    public void drawOutlinedBoxCameraCoord(Matrix4f matrix, Vec3d from, Vec3d to);

    public void drawSolidBoxCameraCoord(Matrix4f matrix, Vec3d from, Vec3d to);

    public void drawQuadCameraCoord(Matrix4f matrix4f, Vec3d a, Vec3d b, Vec3d c, Vec3d d);

    public void setAsShaderColor(Color color, float opacity);
}
