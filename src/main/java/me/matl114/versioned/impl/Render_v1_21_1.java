package me.matl114.versioned.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.*;
import java.util.List;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.render.ColorQuad;
import me.matl114.utils.render.Quad;
import me.matl114.utils.render.UV;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public class Render_v1_21_1 implements VRender, VRender.WrapRenderOperation {

    @Override
    public void createLinesLayer(RenderCallback callback) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder =
                tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        callback.draw(this, bufferBuilder);
        var buffer = bufferBuilder.endNullable();
        if (buffer != null) {
            BufferRenderer.drawWithGlobalProgram(buffer);
        }
    }

    @Override
    public void createLineStripLayer(RenderCallback callback) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder =
                tessellator.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        callback.draw(this, bufferBuilder);
        var buffer = bufferBuilder.endNullable();
        if (buffer != null) {
            BufferRenderer.drawWithGlobalProgram(buffer);
        }
    }

    @Override
    public void createQuadsLayer(RenderCallback callback, boolean hasCulling) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        if (!hasCulling) {
            RenderSystem.disableCull();
        }
        callback.draw(this, bufferBuilder);
        if (!hasCulling) {
            RenderSystem.enableCull();
        }
        var buffer = bufferBuilder.endNullable();
        if (buffer != null) {
            BufferRenderer.drawWithGlobalProgram(buffer);
        }
    }

    @Override
    public void createTrianglesLayer(RenderCallback callback, boolean hasCulling) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder = tessellator.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        if (!hasCulling) {
            RenderSystem.disableCull();
        }
        callback.draw(this, bufferBuilder);
        if (!hasCulling) {
            RenderSystem.enableCull();
        }
        var buffer = bufferBuilder.endNullable();
        if (buffer != null) {
            BufferRenderer.drawWithGlobalProgram(buffer);
        }
    }

    @Override
    public void createTriangleStripLayer(RenderCallback callback, boolean hasCulling) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder =
                tessellator.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        if (!hasCulling) {
            RenderSystem.disableCull();
        }
        callback.draw(this, bufferBuilder);
        if (!hasCulling) {
            RenderSystem.enableCull();
        }
        var buffer = bufferBuilder.endNullable();
        if (buffer != null) {
            BufferRenderer.drawWithGlobalProgram(buffer);
        }
    }

    @Override
    public void createGuiTexturedLayer(Identifier path, RenderCallback callback) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableCull();
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShaderTexture(0, path);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        var vertex = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        callback.draw(this, vertex);
        var buffer = vertex.endNullable();
        if (buffer != null) {
            BufferRenderer.drawWithGlobalProgram(buffer);
        }
        RenderSystem.enableCull();
    }

    @Override
    public void createSpriteTexturedLayer(Sprite sprite, RenderCallback callback) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableCull();
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShaderTexture(0, sprite.getAtlasId());
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        var delegate = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        var vertex = RenderUtils.getSpriteVertexConsumer(delegate, sprite);
        callback.draw(this, vertex);
        var buffer = delegate.endNullable();
        if (buffer != null) {
            BufferRenderer.drawWithGlobalProgram(buffer);
        }
        RenderSystem.enableCull();
    }

    @Override
    public void createGuiLayer(RenderCallback callback) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableCull();
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        var vertex = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        callback.draw(this, vertex);
        var buffer = vertex.endNullable();
        if (buffer != null) {
            BufferRenderer.drawWithGlobalProgram(buffer);
        }
        RenderSystem.enableCull();
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
        mc.getBufferBuilders().getEntityVertexConsumers().draw();
        //        GL11.glDisable(GL11.GL_DEPTH_TEST);
        //        GL11.glDepthMask(false);
        stack.pop();
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

    @Override
    public void drawOutlinedBox(
            MatrixStack matrix4f, VertexConsumer bufferBuilder, Vec3d from, Vec3d to, int cachedRenderColor) {
        MatrixStack.Entry matrix = matrix4f.peek();
        float minX = (float) from.getX();
        float minY = (float) from.getY();
        float minZ = (float) from.getZ();
        float maxX = (float) to.getX();
        float maxY = (float) to.getY();
        float maxZ = (float) to.getZ();
        int color = cachedRenderColor;
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

    @Override
    public void drawSolidBoxQuad(
            MatrixStack matrixStack, VertexConsumer bufferBuilder, Vec3d from, Vec3d to, int cachedRenderColor) {
        var matrix = matrixStack.peek();
        float minX = (float) from.x;
        float minY = (float) from.y;
        float minZ = (float) from.z;
        float maxX = (float) to.x;
        float maxY = (float) to.y;
        float maxZ = (float) to.z;
        int color = cachedRenderColor;

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

    @Override
    public void drawQuad(MatrixStack matrixStack, VertexConsumer bufferBuilder, Quad uv, ColorQuad colorQuad) {
        var matrix4 = matrixStack.peek();
        for (int idx = 0; idx < 4; idx++) {
            var vec3d = uv.get(idx);
            int color = colorQuad.get(idx);
            bufferBuilder
                    .vertex(matrix4, (float) vec3d.x, (float) vec3d.y, (float) vec3d.z)
                    .color(color);
        }
    }

    @Override
    public void drawLines(MatrixStack matrixStack, VertexConsumer consumer, List<Vec3d> points, int color) {
        MatrixStack.Entry entry = matrixStack.peek();
        for (var i = 1; i < points.size(); i++) {
            Vector3f prev = points.get(i - 1).toVector3f();
            Vector3f next = points.get(i).toVector3f();
            consumer.vertex(entry, prev).color(color);
            consumer.vertex(entry, next).color(color);
        }
    }

    @Override
    public void drawLine(MatrixStack matrixStack, VertexConsumer consumer, Vec3d prevV, Vec3d nextV, int color) {
        Vector3f prev = prevV.toVector3f();
        Vector3f next = nextV.toVector3f();
        MatrixStack.Entry entry = matrixStack.peek();
        consumer.vertex(entry, prev).color(color);
        consumer.vertex(entry, next).color(color);
    }

    @Override
    public void drawTexturedQuad(MatrixStack stack, VertexConsumer vertex, Quad quad, UV uv, ColorQuad colorQuad) {
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
    }
}
