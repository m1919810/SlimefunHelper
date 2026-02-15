package me.matl114.versioned.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.render.ColorQuad;
import me.matl114.utils.render.Quad;
import me.matl114.utils.render.UV;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class Render_v1_21_1 implements VRender {
    @Override
    public void drawStripLineVirtualCameraCoord(MatrixStack matrixStack, List<Vec3d> path, Color color) {
        drawVertexFormatPositionCameraCoord(matrixStack, VertexFormat.DrawMode.DEBUG_LINE_STRIP, path, color);
    }

    @Override
    public void drawLineVirtualCameraCoord(MatrixStack matrixStack, List<Vec3d> pairs, Color color) {
        drawVertexFormatPositionCameraCoord(matrixStack, VertexFormat.DrawMode.DEBUG_LINES, pairs, color);
    }

    @Override
    public void drawOutlinedBoxCameraCoord(MatrixStack matrix, Vec3d from, Vec3d to, Color color) {
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder =
                tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        drawOutlinedBox(matrix.peek(), bufferBuilder, from, to, color);
        //        Vec3d vec3d = new Vec3d(matrix.transformPosition((float) from.x, (float) from.y, (float) from.z, new
        // Vector3f()));
        //        Vec3d vec3d1 = new Vec3d(matrix.transformPosition((float) to.x, (float) to.y, (float) to.z, new
        // Vector3f()));
        //        drawOutlinedBox(bufferBuilder, vec3d, vec3d1);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }

    @Override
    public void drawSolidBoxCameraCoord(MatrixStack matrix, Vec3d from, Vec3d to, Color color) {
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        drawSolidBox(matrix.peek(), bufferBuilder, from, to, color);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }

    @Override
    public void drawQuadCameraCoord(MatrixStack matrix4f, Quad quad, ColorQuad colorQuad) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        var matrix4 = matrix4f.peek();
        for (var i = 0; i < 4; ++i) {
            Vec3d vec3d = quad.get(i);
            int color = colorQuad.get(i);
            bufferBuilder
                    .vertex(matrix4, (float) vec3d.x, (float) vec3d.y, (float) vec3d.z)
                    .color(color);
        }
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }

    public void setAsShaderColor(Color color, float opacity) {
        RenderSystem.setShaderColor(
                color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, opacity);
    }

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final float TEXT_HEIGHT = 9.0F;

    @Override
    public void drawTextCameraCoord(
            OrderedText orderedText,
            MatrixStack stack,
            Vec3d vec3d,
            int displayPositionFlag,
            Color color,
            TextDisplay displayInfo) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        //        GL11.glEnable(GL11.GL_DEPTH_TEST);
        //        GL11.glDepthMask(true);
        int xAlign = displayPositionFlag % 3;
        int yAlign = displayPositionFlag / 3;
        int width = mc.textRenderer.getWidth(orderedText);
        float xStart = -((width * xAlign) / 2.0F);
        float yStart = -((TEXT_HEIGHT * yAlign) / 2.0F);
        stack.push();
        stack.translate(vec3d.x, vec3d.y, vec3d.z);
        stack.scale(1, -1, 1);
        stack.translate(xStart, yStart, 0);
        mc.textRenderer.draw(
                orderedText,
                0,
                0,
                color.getRGB(),
                displayInfo.shadow(),
                stack.peek().getPositionMatrix(),
                mc.getBufferBuilders().getEntityVertexConsumers(),
                displayInfo.layerType(),
                displayInfo.backgroundColor(),
                displayInfo.light());
        //        GL11.glDisable(GL11.GL_DEPTH_TEST);
        //        GL11.glDepthMask(false);
        stack.pop();
    }

    @Override
    public void drawTexturedQuadCameraCoord(Identifier path, MatrixStack stack, Quad quad, UV uv, ColorQuad colorQuad) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableCull();
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShaderTexture(0, path);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        var vertex = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        var entry = stack.peek();
        for (var i = 0; i < 4; ++i) {
            Vec3d vec3d = quad.get(i);
            float u = uv.getU(i);
            float v = uv.getV(i);
            int color = colorQuad.get(i);
            vertex.vertex(entry, (float) vec3d.x, (float) vec3d.y, (float) vec3d.z)
                    .texture(u, v)
                    .color(color);
        }
        BufferRenderer.drawWithGlobalProgram(vertex.end());
        RenderSystem.enableCull();
    }

    @Override
    public void drawSpriteQuadCameraCoord(Sprite sprite, MatrixStack stack, Quad quad, UV uv, ColorQuad colorQuad) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableCull();
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShaderTexture(0, sprite.getAtlasId());
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        var delegate = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        var vertex = RenderUtils.getSpriteVertexConsumer(delegate, sprite);
        var entry = stack.peek();
        for (var i = 0; i < 4; ++i) {
            Vec3d vec3d = quad.get(i);
            float u = uv.getU(i);
            float v = uv.getV(i);
            int color = colorQuad.get(i);
            vertex.vertex(entry, (float) vec3d.x, (float) vec3d.y, (float) vec3d.z)
                    .texture(u, v)
                    .color(color);
        }
        BufferRenderer.drawWithGlobalProgram(delegate.end());
        RenderSystem.enableCull();
    }

    @Override
    public void drawGuiQuadCameraCoord(MatrixStack stack, Quad quad, ColorQuad colorQuad) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableCull();
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        var vertex = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        var entry = stack.peek();
        for (var i = 0; i < 4; ++i) {
            Vec3d vec3d = quad.get(i);
            int color = colorQuad.get(i);
            vertex.vertex(entry, (float) vec3d.x, (float) vec3d.y, (float) vec3d.z)
                    .color(color);
        }
        BufferRenderer.drawWithGlobalProgram(vertex.end());
        RenderSystem.enableCull();
    }

    @Override
    public void drawItemCameraCoord(
            ItemStack itemStack, MatrixStack stack, Vec3d vec3d, ItemDisplayContext context, ItemDisplay displayInfo) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        boolean bl = Vec3d.ZERO.equals(vec3d);
        if (!bl) {
            stack.translate(vec3d.x, vec3d.y, vec3d.z);
        }
        mc.getItemRenderer()
                .renderItem(
                        itemStack,
                        context,
                        displayInfo.light(),
                        displayInfo.overlay(),
                        stack,
                        mc.gameRenderer.buffers.getEntityVertexConsumers(),
                        mc.world,
                        -999);
        if (!bl) {
            stack.translate(-vec3d.x, -vec3d.y, -vec3d.z);
        }
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

    public static void drawOutlinedBox(
            MatrixStack.Entry matrix, BufferBuilder bufferBuilder, Vec3d from, Vec3d to, Color colorObj) {
        float minX = (float) from.getX();
        float minY = (float) from.getY();
        float minZ = (float) from.getZ();
        float maxX = (float) to.getX();
        float maxY = (float) to.getY();
        float maxZ = (float) to.getZ();
        int color = colorObj.getRGB();
        bufferBuilder.vertex(matrix, minX, minY, minZ).color(color);
        bufferBuilder.vertex(matrix, maxX, minY, minZ).color(color);

        bufferBuilder.vertex(matrix, maxX, minY, minZ).color(color);
        bufferBuilder.vertex(matrix, maxX, minY, maxZ).color(color);

        bufferBuilder.vertex(matrix, maxX, minY, maxZ).color(color);
        bufferBuilder.vertex(matrix, minX, minY, maxZ).color(color);

        bufferBuilder.vertex(matrix, minX, minY, maxZ).color(color);
        bufferBuilder.vertex(matrix, minX, minY, minZ).color(color);

        bufferBuilder.vertex(matrix, minX, minY, minZ).color(color);
        bufferBuilder.vertex(matrix, minX, maxY, minZ).color(color);

        bufferBuilder.vertex(matrix, maxX, minY, minZ).color(color);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ).color(color);

        bufferBuilder.vertex(matrix, maxX, minY, maxZ).color(color);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ).color(color);

        bufferBuilder.vertex(matrix, minX, minY, maxZ).color(color);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ).color(color);

        bufferBuilder.vertex(matrix, minX, maxY, minZ).color(color);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ).color(color);

        bufferBuilder.vertex(matrix, maxX, maxY, minZ).color(color);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ).color(color);

        bufferBuilder.vertex(matrix, maxX, maxY, maxZ).color(color);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ).color(color);

        bufferBuilder.vertex(matrix, minX, maxY, maxZ).color(color);
        bufferBuilder.vertex(matrix, minX, maxY, minZ).color(color);
    }

    public static void drawSolidBox(
            MatrixStack.Entry matrix, BufferBuilder bufferBuilder, Vec3d from, Vec3d to, Color colorObj) {
        float minX = (float) from.x;
        float minY = (float) from.y;
        float minZ = (float) from.z;
        float maxX = (float) to.x;
        float maxY = (float) to.y;
        float maxZ = (float) to.z;
        int color = colorObj.getRGB();

        bufferBuilder.vertex(matrix, minX, minY, minZ).color(color);
        bufferBuilder.vertex(matrix, maxX, minY, minZ).color(color);
        bufferBuilder.vertex(matrix, maxX, minY, maxZ).color(color);
        bufferBuilder.vertex(matrix, minX, minY, maxZ).color(color);

        bufferBuilder.vertex(matrix, minX, maxY, minZ).color(color);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ).color(color);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ).color(color);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ).color(color);

        bufferBuilder.vertex(matrix, minX, minY, minZ).color(color);
        bufferBuilder.vertex(matrix, minX, maxY, minZ).color(color);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ).color(color);
        bufferBuilder.vertex(matrix, maxX, minY, minZ).color(color);

        bufferBuilder.vertex(matrix, maxX, minY, minZ).color(color);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ).color(color);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ).color(color);
        bufferBuilder.vertex(matrix, maxX, minY, maxZ).color(color);

        bufferBuilder.vertex(matrix, minX, minY, maxZ).color(color);
        bufferBuilder.vertex(matrix, maxX, minY, maxZ).color(color);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ).color(color);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ).color(color);

        bufferBuilder.vertex(matrix, minX, minY, minZ).color(color);
        bufferBuilder.vertex(matrix, minX, minY, maxZ).color(color);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ).color(color);
        bufferBuilder.vertex(matrix, minX, maxY, minZ).color(color);
    }
}
