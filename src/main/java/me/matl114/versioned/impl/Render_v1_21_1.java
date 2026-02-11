package me.matl114.versioned.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import me.matl114.utils.RenderUtils;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class Render_v1_21_1 implements VRender {
    @Override
    public void drawStripLineVirtual(MatrixStack matrixStack, List<Vec3d> path, Color color) {
        drawVertexFormatPosition(matrixStack, VertexFormat.DrawMode.DEBUG_LINE_STRIP, path, color);
    }

    @Override
    public void drawLineVirtual(MatrixStack matrixStack, List<Vec3d> pairs, Color color) {
        drawVertexFormatPosition(matrixStack, VertexFormat.DrawMode.DEBUG_LINES, pairs, color);
    }

    @Override
    public void drawLineVirtualCameraCoord(MatrixStack matrixStack, List<Vec3d> pairs, Color color) {
        drawVertexFormatPositionCameraCoord(matrixStack, VertexFormat.DrawMode.DEBUG_LINES, pairs, color);
    }

    @Override
    public void drawOutlinedBoxCameraCoord(MatrixStack matrix, Vec3d from, Vec3d to) {
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        BufferBuilder bufferBuilder = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
        drawOutlinedBox(matrix.peek(), bufferBuilder, from, to);
        //        Vec3d vec3d = new Vec3d(matrix.transformPosition((float) from.x, (float) from.y, (float) from.z, new
        // Vector3f()));
        //        Vec3d vec3d1 = new Vec3d(matrix.transformPosition((float) to.x, (float) to.y, (float) to.z, new
        // Vector3f()));
        //        drawOutlinedBox(bufferBuilder, vec3d, vec3d1);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }

    @Override
    public void drawSolidBoxCameraCoord(MatrixStack matrix, Vec3d from, Vec3d to) {
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        BufferBuilder bufferBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        drawSolidBox(matrix.peek(), bufferBuilder, from, to);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }

    @Override
    public void drawQuadCameraCoord(MatrixStack matrix4f, Vec3d a, Vec3d b, Vec3d c, Vec3d d) {
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        BufferBuilder bufferBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        var matrix4 = matrix4f.peek();
        bufferBuilder.vertex(matrix4, (float) a.x, (float) a.y, (float) a.z);
        bufferBuilder.vertex(matrix4, (float) b.x, (float) b.y, (float) b.z);
        bufferBuilder.vertex(matrix4, (float) c.x, (float) c.y, (float) c.z);
        bufferBuilder.vertex(matrix4, (float) d.x, (float) d.y, (float) d.z);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }

    public void setAsShaderColor(Color color, float opacity) {
        RenderSystem.setShaderColor(
                color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, opacity);
    }

    // in world coord
    public void drawVertexFormatPosition(
            MatrixStack matrixStack, VertexFormat.DrawMode mode, List<Vec3d> path, Color color) {
        if (path.isEmpty()) return;
        Vec3d camPos = RenderUtils.getCameraPos();
        Matrix4f matrix = matrixStack.peek().getPositionMatrix();
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        BufferBuilder bufferBuilder = tessellator.begin(mode, VertexFormats.POSITION);
        setAsShaderColor(color, 1.0F);

        for (Vec3d point : path) {
            bufferBuilder.vertex(
                    matrix, (float) (point.x - camPos.x), (float) (point.y - camPos.y), (float) (point.z - camPos.z));
        }
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }

    public void drawVertexFormatPositionCameraCoord(
            MatrixStack matrixStack, VertexFormat.DrawMode mode, List<Vec3d> path, Color color) {
        if (path.isEmpty()) return;
        Matrix4f matrix = matrixStack.peek().getPositionMatrix();
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        BufferBuilder bufferBuilder = tessellator.begin(mode, VertexFormats.POSITION);
        setAsShaderColor(color, 1.0F);
        for (Vec3d point : path) {
            bufferBuilder.vertex(matrix, (float) point.x, (float) point.y, (float) point.z);
        }
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }

    public static void drawVertexFromTo(Matrix4f positionMatrix, BufferBuilder bufferBuilder, Vec3d from, Vec3d to) {
        bufferBuilder.vertex(positionMatrix, (float) from.x, (float) from.y, (float) from.z);
        //  .color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        bufferBuilder.vertex(positionMatrix, (float) to.x, (float) to.y, (float) to.z);
        // .color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public static void cacheVertexAction(
            VertexBuffer vertexBuffer,
            VertexFormat.DrawMode mode,
            VertexFormat format,
            Consumer<BufferBuilder> action) {
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        BufferBuilder bufferBuilder = tessellator.begin(mode, format);
        action.accept(bufferBuilder);
        BuiltBuffer builtBuffer = bufferBuilder.endNullable();
        if (builtBuffer != null) {
            // upload datas
            vertexBuffer.bind();
            vertexBuffer.upload(builtBuffer);
            VertexBuffer.unbind();
        }
    }

    public static void drawOutlinedBox(MatrixStack.Entry matrix, BufferBuilder bufferBuilder, Vec3d from, Vec3d to) {
        float minX = (float) from.getX();
        float minY = (float) from.getY();
        float minZ = (float) from.getZ();
        float maxX = (float) to.getX();
        float maxY = (float) to.getY();
        float maxZ = (float) to.getZ();
        bufferBuilder.vertex(matrix, minX, minY, minZ);
        bufferBuilder.vertex(matrix, maxX, minY, minZ);

        bufferBuilder.vertex(matrix, maxX, minY, minZ);
        bufferBuilder.vertex(matrix, maxX, minY, maxZ);

        bufferBuilder.vertex(matrix, maxX, minY, maxZ);
        bufferBuilder.vertex(matrix, minX, minY, maxZ);

        bufferBuilder.vertex(matrix, minX, minY, maxZ);
        bufferBuilder.vertex(matrix, minX, minY, minZ);

        bufferBuilder.vertex(matrix, minX, minY, minZ);
        bufferBuilder.vertex(matrix, minX, maxY, minZ);

        bufferBuilder.vertex(matrix, maxX, minY, minZ);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ);

        bufferBuilder.vertex(matrix, maxX, minY, maxZ);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ);

        bufferBuilder.vertex(matrix, minX, minY, maxZ);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ);

        bufferBuilder.vertex(matrix, minX, maxY, minZ);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ);

        bufferBuilder.vertex(matrix, maxX, maxY, minZ);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ);

        bufferBuilder.vertex(matrix, maxX, maxY, maxZ);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ);

        bufferBuilder.vertex(matrix, minX, maxY, maxZ);
        bufferBuilder.vertex(matrix, minX, maxY, minZ);
    }

    public static void drawSolidBox(MatrixStack.Entry matrix, BufferBuilder bufferBuilder, Vec3d from, Vec3d to) {
        float minX = (float) from.x;
        float minY = (float) from.y;
        float minZ = (float) from.z;
        float maxX = (float) to.x;
        float maxY = (float) to.y;
        float maxZ = (float) to.z;

        bufferBuilder.vertex(matrix, minX, minY, minZ);
        bufferBuilder.vertex(matrix, maxX, minY, minZ);
        bufferBuilder.vertex(matrix, maxX, minY, maxZ);
        bufferBuilder.vertex(matrix, minX, minY, maxZ);

        bufferBuilder.vertex(matrix, minX, maxY, minZ);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ);

        bufferBuilder.vertex(matrix, minX, minY, minZ);
        bufferBuilder.vertex(matrix, minX, maxY, minZ);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ);
        bufferBuilder.vertex(matrix, maxX, minY, minZ);

        bufferBuilder.vertex(matrix, maxX, minY, minZ);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ);
        bufferBuilder.vertex(matrix, maxX, minY, maxZ);

        bufferBuilder.vertex(matrix, minX, minY, maxZ);
        bufferBuilder.vertex(matrix, maxX, minY, maxZ);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ);

        bufferBuilder.vertex(matrix, minX, minY, minZ);
        bufferBuilder.vertex(matrix, minX, minY, maxZ);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ);
        bufferBuilder.vertex(matrix, minX, maxY, minZ);
    }
}
