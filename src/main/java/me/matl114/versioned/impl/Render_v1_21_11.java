package me.matl114.versioned.impl;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import me.matl114.utils.RenderUtils;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Render_v1_21_11 implements VRender {
    // RGBA
    public int[] cacheRenderColor = new int[4];

    {
        Arrays.fill(cacheRenderColor, 255);
    }

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    // mapping MultiBufferSource - VertexConsumerProvider
    // BufferSource - Immediate
    // PoseStack - MatrixStack
    public static VertexConsumerProvider.Immediate getVCP() {
        return mc.getBufferBuilders().getEntityVertexConsumers();
    }

    public static final RenderPipeline DEBUG_LINES =
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.tryParse("slimefunhelper:pipeline/debug_lines"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());

    public static final RenderLayer LINES = RenderLayer.of(
            "slimefunhelper:debug_lines",
            RenderSetup.builder(DEBUG_LINES)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .build());

    public static final RenderPipeline.Snippet DEBUG_LINES_STRIP_SNIPPET = RenderPipeline.builder(
                    new RenderPipeline.Snippet[] {
                        RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET
                    })
            .withVertexShader("core/rendertype_lines")
            .withFragmentShader("core/rendertype_lines")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.DrawMode.LINES)
            .buildSnippet();

    public static final RenderPipeline DEBUG_LINES_STRIP =
            RenderPipelines.register(RenderPipeline.builder(DEBUG_LINES_STRIP_SNIPPET)
                    .withLocation(Identifier.tryParse("slimefunhelper:pipeline/debug_lines_strip"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());

    @ApiStatus.Experimental
    public static final RenderLayer LINES_STRIP = RenderLayer.of(
            "slimefunhelper:debug_lines_strip",
            RenderSetup.builder(DEBUG_LINES_STRIP)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .build());

    public static final RenderPipeline DEBUG_QUADS =
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.tryParse("slimefunhelper:pipeline/debug_quads"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());

    public static final RenderLayer QUADS = RenderLayer.of(
            "slimefunhelper:debug_quads",
            RenderSetup.builder(DEBUG_QUADS).translucent().build());

    @Override
    public void drawStripLineVirtual(MatrixStack matrixStack, List<Vec3d> path, Color color) {
        if (path.size() < 2) return;
        VertexConsumerProvider.Immediate vcp = getVCP();
        VertexConsumer consumer = vcp.getBuffer(LINES);
        Vec3d offset = RenderUtils.getCameraPos();
        List<Vec3d> points = path.stream().map(v -> v.subtract(offset)).toList();
        MatrixStack.Entry entry = matrixStack.peek();
        for (var i = 1; i < points.size(); i++) {
            Vector3f prev = points.get(i - 1).toVector3f();
            Vector3f next = points.get(i).toVector3f();
            Vector3f normal = new Vector3f(next).sub(prev).normalize();
            consumer.vertex(entry, prev)
                    .color(color.getRed(), color.getGreen(), color.getBlue(), 255)
                    .normal(entry, normal)
                    .lineWidth(2);
            consumer.vertex(entry, next)
                    .color(color.getRed(), color.getGreen(), color.getBlue(), 255)
                    .normal(entry, normal)
                    .lineWidth(2);
        }
        vcp.draw(LINES);
    }

    @Override
    public void drawLineVirtual(MatrixStack matrixStack, List<Vec3d> path, Color color) {
        if (path.size() < 2) return;
        VertexConsumerProvider.Immediate vcp = getVCP();
        VertexConsumer consumer = vcp.getBuffer(LINES);
        Vec3d offset = RenderUtils.getCameraPos();
        List<Vec3d> points = path.stream().map(v -> v.subtract(offset)).toList();
        MatrixStack.Entry entry = matrixStack.peek();
        for (var i = 1; i < points.size(); i += 2) {
            Vector3f prev = points.get(i - 1).toVector3f();
            Vector3f next = points.get(i).toVector3f();
            Vector3f normal = new Vector3f(next).sub(prev).normalize();
            consumer.vertex(entry, prev)
                    .color(color.getRed(), color.getGreen(), color.getBlue(), 255)
                    .normal(entry, normal)
                    .lineWidth(1);
            consumer.vertex(entry, next)
                    .color(color.getRed(), color.getGreen(), color.getBlue(), 255)
                    .normal(entry, normal)
                    .lineWidth(1);
        }
        vcp.draw(LINES);
    }

    @Override
    public void drawLineVirtualCameraCoord(MatrixStack matrixStack, List<Vec3d> points, Color color) {
        if (points.size() < 2) return;
        VertexConsumerProvider.Immediate vcp = getVCP();
        VertexConsumer consumer = vcp.getBuffer(LINES);
        MatrixStack.Entry entry = matrixStack.peek();
        for (var i = 1; i < points.size(); i += 2) {
            Vector3f prev = points.get(i - 1).toVector3f();
            Vector3f next = points.get(i).toVector3f();
            Vector3f normal = new Vector3f(next).sub(prev).normalize();
            consumer.vertex(entry, prev)
                    .color(color.getRed(), color.getGreen(), color.getBlue(), 255)
                    .normal(entry, normal)
                    .lineWidth(1);
            consumer.vertex(entry, next)
                    .color(color.getRed(), color.getGreen(), color.getBlue(), 255)
                    .normal(entry, normal)
                    .lineWidth(1);
        }
        vcp.draw(LINES);
    }

    @Override
    public void drawOutlinedBoxCameraCoord(Matrix4f matrix, Vec3d from, Vec3d to) {
        VertexConsumerProvider.Immediate vcp = getVCP();
        VertexConsumer consumer = vcp.getBuffer(LINES);
        drawOutlinedBox(matrix, consumer, from, to);
        vcp.draw(LINES);
    }

    public void drawOutlinedBox(Matrix4f matrix4f, VertexConsumer bufferBuilder, Vec3d from, Vec3d to) {
        float minX = (float) from.getX();
        float minY = (float) from.getY();
        float minZ = (float) from.getZ();
        float maxX = (float) to.getX();
        float maxY = (float) to.getY();
        float maxZ = (float) to.getZ();

        bufferBuilder
                .vertex(matrix4f, minX, minY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(1, 0, 0)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, maxX, minY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(1, 0, 0)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, maxX, minY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(0, 0, 1)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, maxX, minY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(0, 0, 1)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, minX, minY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(1, 0, 0)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, maxX, minY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(1, 0, 0)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, minX, minY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(0, 0, 1)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, minX, minY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(0, 0, 1)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, minX, minY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(0, 1, 0)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, minX, maxY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(0, 1, 0)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, maxX, minY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(0, 1, 0)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, maxX, maxY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(0, 1, 0)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, maxX, minY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(0, 1, 0)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, maxX, maxY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(0, 1, 0)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, minX, minY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(0, 1, 0)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, minX, maxY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(0, 1, 0)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, minX, maxY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(1, 0, 0)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, maxX, maxY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(1, 0, 0)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, maxX, maxY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(0, 0, 1)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, maxX, maxY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(0, 0, 1)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, minX, maxY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(1, 0, 0)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, maxX, maxY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(1, 0, 0)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, minX, maxY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(0, 0, 1)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, minX, maxY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3])
                .normal(0, 0, 1)
                .lineWidth(2);
    }

    @Override
    public void drawSolidBoxCameraCoord(Matrix4f matrix, Vec3d from, Vec3d to) {
        VertexConsumerProvider.Immediate vcp = getVCP();
        VertexConsumer consumer = vcp.getBuffer(QUADS);
        drawSolidBox(matrix, consumer, from, to);
        vcp.draw(QUADS);
    }

    public void drawSolidBox(Matrix4f matrix, VertexConsumer bufferBuilder, Vec3d from, Vec3d to) {
        float minX = (float) from.x;
        float minY = (float) from.y;
        float minZ = (float) from.z;
        float maxX = (float) to.x;
        float maxY = (float) to.y;
        float maxZ = (float) to.z;

        bufferBuilder
                .vertex(matrix, minX, minY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, maxX, minY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, maxX, minY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, minX, minY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);

        bufferBuilder
                .vertex(matrix, minX, maxY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, minX, maxY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, maxX, maxY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, maxX, maxY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);

        bufferBuilder
                .vertex(matrix, minX, minY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, minX, maxY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, maxX, maxY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, maxX, minY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);

        bufferBuilder
                .vertex(matrix, maxX, minY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, maxX, maxY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, maxX, maxY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, maxX, minY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);

        bufferBuilder
                .vertex(matrix, minX, minY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, maxX, minY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, maxX, maxY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, minX, maxY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);

        bufferBuilder
                .vertex(matrix, minX, minY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, minX, minY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, minX, maxY, maxZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix, minX, maxY, minZ)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
    }

    @Override
    public void drawQuadCameraCoord(Matrix4f matrix4f, Vec3d a, Vec3d b, Vec3d c, Vec3d d) {
        VertexConsumerProvider.Immediate vcp = getVCP();
        VertexConsumer bufferBuilder = vcp.getBuffer(QUADS);
        bufferBuilder
                .vertex(matrix4f, (float) a.x, (float) a.y, (float) a.z)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix4f, (float) b.x, (float) b.y, (float) b.z)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix4f, (float) c.x, (float) c.y, (float) c.z)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        bufferBuilder
                .vertex(matrix4f, (float) d.x, (float) d.y, (float) d.z)
                .color(cacheRenderColor[0], cacheRenderColor[1], cacheRenderColor[2], cacheRenderColor[3]);
        vcp.draw(QUADS);
    }

    @Override
    public void setAsShaderColor(Color color, float opacity) {
        cacheRenderColor[0] = color.getRed();
        cacheRenderColor[1] = color.getGreen();
        cacheRenderColor[2] = color.getBlue();
        cacheRenderColor[3] = (int) (opacity * 255.0F);
    }
}
