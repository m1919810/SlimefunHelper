package me.matl114.versioned.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import me.matl114.utils.RenderUtils;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

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
    public void drawOutlinedBoxCameraCoord(Matrix4f matrix, Vec3d from, Vec3d to) {
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        BufferBuilder bufferBuilder = tessellator
            .begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
        drawOutlinedBox(matrix, bufferBuilder, from, to);
//        Vec3d vec3d = new Vec3d(matrix.transformPosition((float) from.x, (float) from.y, (float) from.z, new Vector3f()));
//        Vec3d vec3d1 = new Vec3d(matrix.transformPosition((float) to.x, (float) to.y, (float) to.z, new Vector3f()));
//        drawOutlinedBox(bufferBuilder, vec3d, vec3d1);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }

    @Override
    public void drawSolidBoxCameraCoord(Matrix4f matrix, Vec3d from, Vec3d to) {
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        BufferBuilder bufferBuilder = tessellator
            .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        drawSolidBox(matrix, bufferBuilder, from, to);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }

    @Override
    public void drawQuadCameraCoord(Matrix4f matrix4f, Vec3d a, Vec3d b, Vec3d c, Vec3d d) {
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        BufferBuilder bufferBuilder = tessellator
            .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        bufferBuilder.vertex(matrix4f, (float) a.x, (float) a.y, (float) a.z);
        bufferBuilder.vertex(matrix4f, (float) b.x, (float) b.y, (float) b.z);
        bufferBuilder.vertex(matrix4f, (float) c.x, (float) c.y, (float) c.z);
        bufferBuilder.vertex(matrix4f, (float) d.x, (float) d.y, (float) d.z);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }

    @Override
    public void drawHighlightFrame(DrawContext context, int x, int y, int dx, int dy, int color) {
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y, x + dx, y + 1, color, color, 0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x , y, x + 1, y + dy,  color, color,  0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x + dx - 1, y + 1, x + dx, y + dy,  color, color, 0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x + 1, y + dy - 1, x + dx, y + dy,  color, color, 0);
    }

    public  void setAsShaderColor(Color color, float opacity){
        RenderSystem.setShaderColor(color.getRed() / 255.0F, color.getGreen()/ 255.0F, color.getBlue()/ 255.0F, opacity);
    }

    //in world coord
    public  void drawVertexFormatPosition(MatrixStack matrixStack, VertexFormat.DrawMode mode, List<Vec3d> path, Color color){
        if(path.isEmpty())
            return;
        Vec3d camPos = RenderUtils.getCameraPos();
        Matrix4f matrix = matrixStack.peek().getPositionMatrix();
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        BufferBuilder bufferBuilder = tessellator.begin(mode, VertexFormats.POSITION);
        setAsShaderColor(color,1.0F);

        for(Vec3d point : path){
            bufferBuilder.vertex(matrix, (float)(point.x - camPos.x),
                (float)(point.y - camPos.y), (float)(point.z - camPos.z));

        }
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }
    public void drawVertexFormatPositionCameraCoord(MatrixStack matrixStack, VertexFormat.DrawMode mode, List<Vec3d> path, Color color){
        if(path.isEmpty())
            return;
        Matrix4f matrix = matrixStack.peek().getPositionMatrix();
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        BufferBuilder bufferBuilder = tessellator.begin(mode, VertexFormats.POSITION);
        setAsShaderColor(color,1.0F);
        for(Vec3d point : path){
            bufferBuilder.vertex(matrix, (float) point.x, (float) point.y, (float) point.z);

        }
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }


    public static void drawVertexFromTo(Matrix4f positionMatrix, BufferBuilder bufferBuilder, Vec3d from, Vec3d to){
        bufferBuilder.vertex(positionMatrix,(float) from.x,(float) from.y,(float)  from.z);
        //  .color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        bufferBuilder.vertex(positionMatrix,(float) to.x, (float) to.y, (float) to.z);
        // .color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public static void cacheVertexAction(VertexBuffer vertexBuffer, VertexFormat.DrawMode mode, VertexFormat format , Consumer<BufferBuilder> action){
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        BufferBuilder bufferBuilder = tessellator.begin(mode, format);
        action.accept(bufferBuilder);
        BuiltBuffer builtBuffer = bufferBuilder.endNullable();
        if(builtBuffer != null){
            //upload datas
            vertexBuffer.bind();
            vertexBuffer.upload(builtBuffer);
            VertexBuffer.unbind();
        }
    }

    public static void drawOutlinedBox(Matrix4f matrix, BufferBuilder bufferBuilder, Vec3d from, Vec3d to){
        float minX = (float)from.getX();
        float minY = (float)from.getY();
        float minZ = (float)from.getZ();
        float maxX = (float)to.getX();
        float maxY = (float)to.getY();
        float maxZ = (float)to.getZ();
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
    public static void drawSolidBox(Matrix4f matrix, BufferBuilder bufferBuilder, Vec3d from, Vec3d to)
    {
        float minX = (float)from.x;
        float minY = (float)from.y;
        float minZ = (float)from.z;
        float maxX = (float)to.x;
        float maxY = (float)to.y;
        float maxZ = (float)to.z;

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
    public static void drawOutlinedBox(BufferBuilder bufferBuilder, Vec3d from, Vec3d to){
        float minX = (float)from.getX();
        float minY = (float)from.getY();
        float minZ = (float)from.getZ();
        float maxX = (float)to.getX();
        float maxY = (float)to.getY();
        float maxZ = (float)to.getZ();
        bufferBuilder.vertex(minX, minY, minZ);
        bufferBuilder.vertex(maxX, minY, minZ);

        bufferBuilder.vertex(maxX, minY, minZ);
        bufferBuilder.vertex(maxX, minY, maxZ);

        bufferBuilder.vertex(maxX, minY, maxZ);
        bufferBuilder.vertex(minX, minY, maxZ);

        bufferBuilder.vertex(minX, minY, maxZ);
        bufferBuilder.vertex(minX, minY, minZ);

        bufferBuilder.vertex(minX, minY, minZ);
        bufferBuilder.vertex(minX, maxY, minZ);

        bufferBuilder.vertex(maxX, minY, minZ);
        bufferBuilder.vertex(maxX, maxY, minZ);

        bufferBuilder.vertex(maxX, minY, maxZ);
        bufferBuilder.vertex(maxX, maxY, maxZ);

        bufferBuilder.vertex(minX, minY, maxZ);
        bufferBuilder.vertex(minX, maxY, maxZ);

        bufferBuilder.vertex(minX, maxY, minZ);
        bufferBuilder.vertex(maxX, maxY, minZ);

        bufferBuilder.vertex(maxX, maxY, minZ);
        bufferBuilder.vertex(maxX, maxY, maxZ);

        bufferBuilder.vertex(maxX, maxY, maxZ);
        bufferBuilder.vertex(minX, maxY, maxZ);

        bufferBuilder.vertex(minX, maxY, maxZ);
        bufferBuilder.vertex(minX, maxY, minZ);
    }
    public static void drawSolidBox(BufferBuilder bufferBuilder, Vec3d from, Vec3d to)
    {
        float minX = (float)from.x;
        float minY = (float)from.y;
        float minZ = (float)from.z;
        float maxX = (float)to.x;
        float maxY = (float)to.y;
        float maxZ = (float)to.z;

        bufferBuilder.vertex(minX, minY, minZ);
        bufferBuilder.vertex(maxX, minY, minZ);
        bufferBuilder.vertex(maxX, minY, maxZ);
        bufferBuilder.vertex(minX, minY, maxZ);

        bufferBuilder.vertex(minX, maxY, minZ);
        bufferBuilder.vertex(minX, maxY, maxZ);
        bufferBuilder.vertex(maxX, maxY, maxZ);
        bufferBuilder.vertex(maxX, maxY, minZ);

        bufferBuilder.vertex(minX, minY, minZ);
        bufferBuilder.vertex(minX, maxY, minZ);
        bufferBuilder.vertex(maxX, maxY, minZ);
        bufferBuilder.vertex(maxX, minY, minZ);

        bufferBuilder.vertex(maxX, minY, minZ);
        bufferBuilder.vertex(maxX, maxY, minZ);
        bufferBuilder.vertex(maxX, maxY, maxZ);
        bufferBuilder.vertex(maxX, minY, maxZ);

        bufferBuilder.vertex(minX, minY, maxZ);
        bufferBuilder.vertex(maxX, minY, maxZ);
        bufferBuilder.vertex(maxX, maxY, maxZ);
        bufferBuilder.vertex(minX, maxY, maxZ);

        bufferBuilder.vertex(minX, minY, minZ);
        bufferBuilder.vertex(minX, minY, maxZ);
        bufferBuilder.vertex(minX, maxY, maxZ);
        bufferBuilder.vertex(minX, maxY, minZ);
    }
}
