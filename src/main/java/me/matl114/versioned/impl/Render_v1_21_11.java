package me.matl114.versioned.impl;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.awt.*;
import java.util.List;
import java.util.function.Function;
import me.matl114.utils.render.ColorQuad;
import me.matl114.utils.render.Quad;
import me.matl114.utils.render.UV;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.ItemCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector3f;

public class Render_v1_21_11 implements VRender, VRender.WrapRenderOperation {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    // mapping MultiBufferSource - VertexConsumerProvider
    // BufferSource - Immediate
    // PoseStack - MatrixStack
    public static VertexConsumerProvider.Immediate getVCP() {
        return mc.getBufferBuilders().getEntityVertexConsumers();
    }

    public static OutlineVertexConsumerProvider getOutlineVCP() {
        return mc.getBufferBuilders().getOutlineVertexConsumers();
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
                    .translucent()
                    .build());

    public static final RenderPipeline DEBUG_LINES_STRIP = RenderPipelines.register(RenderPipeline.builder(
                    RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.tryParse("slimefunhelper:pipeline/debug_lines_strip"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.DrawMode.DEBUG_LINE_STRIP)
            .withCull(false)
            .build());

    @ApiStatus.Experimental
    public static final RenderLayer LINES_STRIP = RenderLayer.of(
            "slimefunhelper:debug_lines_strip",
            RenderSetup.builder(DEBUG_LINES_STRIP)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .translucent()
                    .build());

    public static final RenderPipeline DEBUG_QUADS =
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.tryParse("slimefunhelper:pipeline/debug_quads"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());

    //    public static final RenderPipeline.Snippet POSITION_COLOR_RECT_SNIPPET = RenderPipeline.builder(new
    // RenderPipeline.Snippet[]{RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET}).withVertexShader("core/position_color").withFragmentShader("core/position_color").withBlend(BlendFunction.TRANSLUCENT).withDepthWrite(false).withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES).buildSnippet();

    public static final RenderPipeline DEBUG_RECTS =
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.tryParse("slimefunhelper:pipeline/debug_rects"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
                    .build());

    public static final RenderLayer RECTS = RenderLayer.of(
            "slimefunhelper:debug_rects",
            RenderSetup.builder(DEBUG_RECTS).translucent().build());

    public static final RenderPipeline DEBUG_RECTS_STRIP =
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.tryParse("slimefunhelper:pipeline/debug_rects"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_STRIP)
                    .build());

    public static final RenderLayer RECTS_STRIP = RenderLayer.of(
            "slimefunhelper:debug_rects",
            RenderSetup.builder(DEBUG_RECTS_STRIP).translucent().build());

    public static final RenderLayer QUADS = RenderLayer.of(
            "slimefunhelper:debug_quads",
            RenderSetup.builder(DEBUG_QUADS).translucent().build());

    public static final RenderPipeline DEBUG_QUADS_NO_CULL =
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.tryParse("slimefunhelper:pipeline/debug_quads"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .build());

    public static final RenderLayer QUADS_NO_CULL = RenderLayer.of(
            "slimefunhelper:debug_quads",
            RenderSetup.builder(DEBUG_QUADS_NO_CULL).translucent().build());

    public static final RenderPipeline DEBUG_GUI_3D =
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                    .withLocation(Identifier.tryParse("slimefunhelper:pipeline/gui_3d"))
                    .withCull(false)
                    .build());

    public static final RenderLayer GUI_3D = RenderLayer.of(
            "slimefunhelper:gui_3d",
            RenderSetup.builder(DEBUG_GUI_3D).translucent().build());

    public static RenderPipeline DEBUG_GUI_TEXTURE_3D =
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.tryParse("slimefun:pipeline/gui_textured_3d"))
                    .withCull(false)
                    .build());

    public static Function<Identifier, RenderLayer> GUI_TEXTURE_3D_FACTORY = Util.memoize((identifier -> {
        return RenderLayer.of(
                "slimefunhelper:gui_textured_3d/" + identifier.toString(),
                RenderSetup.builder(DEBUG_GUI_TEXTURE_3D)
                        .texture("Sampler0", identifier)
                        .build());
    }));

    public static Function<Identifier, RenderLayer> GUI_SPRITE_TEXTURE_3D_FACTORY = Util.memoize((identifier -> {
        return RenderLayer.of(
                "slimefunhelper:gui_textured_3d/" + identifier.toString(),
                RenderSetup.builder(DEBUG_GUI_TEXTURE_3D)
                        .texture("Sampler0", identifier, RenderLayers.BLOCK_SAMPLER)
                        .build());
    }));

    public static final OrderedRenderCommandQueueImpl QUEUE = new OrderedRenderCommandQueueImpl();
    public static final ItemCommandRenderer ITEM_RENDERER = new ItemCommandRenderer();

    private void createLayer(RenderLayer layer, RenderCallback callback) {
        VertexConsumerProvider.Immediate vcp = getVCP();
        VertexConsumer consumer = vcp.getBuffer(layer);
        callback.draw(this, consumer);
        vcp.draw(layer);
    }

    public void createLinesLayer(RenderCallback callback) {
        createLayer(LINES, callback);
    }

    @Override
    public void createLineStripLayer(RenderCallback callback) {
        createLayer(LINES_STRIP, callback);
    }

    public void createQuadsLayer(RenderCallback callback, boolean hasCulling) {
        createLayer(hasCulling ? QUADS : QUADS_NO_CULL, callback);
    }

    public void createTrianglesLayer(RenderCallback callback, boolean hasCulling) {
        createLayer(RECTS, callback);
    }

    @Override
    public void createTriangleStripLayer(RenderCallback callback, boolean hasCulling) {
        createLayer(RECTS_STRIP, callback);
    }

    public void createGuiTexturedLayer(Identifier path, RenderCallback callback) {
        createLayer(GUI_TEXTURE_3D_FACTORY.apply(path), callback);
    }

    public void createSpriteTexturedLayer(Sprite sprite, RenderCallback callback) {
        createLayer(GUI_SPRITE_TEXTURE_3D_FACTORY.apply(sprite.getAtlasId()), callback);
    }

    public void createGuiLayer(RenderCallback callback) {
        createLayer(GUI_3D, callback);
    }

    public void drawLines(MatrixStack matrixStack, VertexConsumer consumer, List<Vec3d> points, int color) {
        MatrixStack.Entry entry = matrixStack.peek();
        for (var i = 1; i < points.size(); i++) {
            Vector3f prev = points.get(i - 1).toVector3f();
            Vector3f next = points.get(i).toVector3f();
            Vector3f normal = new Vector3f(next).sub(prev).normalize();
            consumer.vertex(entry, prev).color(color).normal(entry, normal).lineWidth(2);
            consumer.vertex(entry, next).color(color).normal(entry, normal).lineWidth(2);
        }
    }

    public void drawLine(MatrixStack matrixStack, VertexConsumer consumer, Vec3d prevV, Vec3d nextV, int color) {
        Vector3f prev = prevV.toVector3f();
        Vector3f next = nextV.toVector3f();
        MatrixStack.Entry entry = matrixStack.peek();
        Vector3f normal = new Vector3f(next).sub(prev).normalize();
        consumer.vertex(entry, prev).color(color).normal(entry, normal).lineWidth(1);
        consumer.vertex(entry, next).color(color).normal(entry, normal).lineWidth(1);
    }

    public void drawOutlinedBox(
            MatrixStack matrix4fStack, VertexConsumer bufferBuilder, Vec3d from, Vec3d to, int cachedRenderColor) {
        MatrixStack.Entry matrix4f = matrix4fStack.peek();
        float minX = (float) from.getX();
        float minY = (float) from.getY();
        float minZ = (float) from.getZ();
        float maxX = (float) to.getX();
        float maxY = (float) to.getY();
        float maxZ = (float) to.getZ();

        bufferBuilder
                .vertex(matrix4f, minX, minY, minZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 1, 0, 0)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, maxX, minY, minZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 1, 0, 0)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, maxX, minY, minZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 0, 0, 1)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, maxX, minY, maxZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 0, 0, 1)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, minX, minY, maxZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 1, 0, 0)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, maxX, minY, maxZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 1, 0, 0)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, minX, minY, minZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 0, 0, 1)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, minX, minY, maxZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 0, 0, 1)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, minX, minY, minZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 0, 1, 0)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, minX, maxY, minZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 0, 1, 0)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, maxX, minY, minZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 0, 1, 0)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, maxX, maxY, minZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 0, 1, 0)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, maxX, minY, maxZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 0, 1, 0)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, maxX, maxY, maxZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 0, 1, 0)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, minX, minY, maxZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 0, 1, 0)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, minX, maxY, maxZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 0, 1, 0)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, minX, maxY, minZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 1, 0, 0)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, maxX, maxY, minZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 1, 0, 0)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, maxX, maxY, minZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 0, 0, 1)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, maxX, maxY, maxZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 0, 0, 1)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, minX, maxY, maxZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 1, 0, 0)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, maxX, maxY, maxZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 1, 0, 0)
                .lineWidth(2);

        bufferBuilder
                .vertex(matrix4f, minX, maxY, minZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 0, 0, 1)
                .lineWidth(2);
        bufferBuilder
                .vertex(matrix4f, minX, maxY, maxZ)
                .color(cachedRenderColor)
                .normal(matrix4f, 0, 0, 1)
                .lineWidth(2);
    }

    public void drawSolidBoxQuad(
            MatrixStack matrixStack, VertexConsumer bufferBuilder, Vec3d from, Vec3d to, int cachedRenderColor) {
        MatrixStack.Entry matrix = matrixStack.peek();
        float minX = (float) from.x;
        float minY = (float) from.y;
        float minZ = (float) from.z;
        float maxX = (float) to.x;
        float maxY = (float) to.y;
        float maxZ = (float) to.z;

        bufferBuilder.vertex(matrix, minX, minY, minZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, maxX, minY, minZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, maxX, minY, maxZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, minX, minY, maxZ).color(cachedRenderColor);

        bufferBuilder.vertex(matrix, minX, maxY, minZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ).color(cachedRenderColor);

        bufferBuilder.vertex(matrix, minX, minY, minZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, minX, maxY, minZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, maxX, minY, minZ).color(cachedRenderColor);

        bufferBuilder.vertex(matrix, maxX, minY, minZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, maxX, maxY, minZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, maxX, minY, maxZ).color(cachedRenderColor);

        bufferBuilder.vertex(matrix, minX, minY, maxZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, maxX, minY, maxZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, maxX, maxY, maxZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ).color(cachedRenderColor);

        bufferBuilder.vertex(matrix, minX, minY, minZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, minX, minY, maxZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, minX, maxY, maxZ).color(cachedRenderColor);
        bufferBuilder.vertex(matrix, minX, maxY, minZ).color(cachedRenderColor);
    }

    @Override
    public void drawSolidBoxTriangle(
            MatrixStack matrixStack, VertexConsumer bufferBuilder, Vec3d from, Vec3d to, int cachedRenderColor) {
        MatrixStack.Entry matrix = matrixStack.peek();

        // 计算最小/最大坐标（支持 from 和 to 任意顺序）
        float minX = (float) Math.min(from.x, to.x);
        float minY = (float) Math.min(from.y, to.y);
        float minZ = (float) Math.min(from.z, to.z);
        float maxX = (float) Math.max(from.x, to.x);
        float maxY = (float) Math.max(from.y, to.y);
        float maxZ = (float) Math.max(from.z, to.z);

        // 解析颜色 (ARGB)
        int a = (cachedRenderColor >> 24) & 0xFF;
        int r = (cachedRenderColor >> 16) & 0xFF;
        int g = (cachedRenderColor >> 8) & 0xFF;
        int b = cachedRenderColor & 0xFF;

        // 定义八个顶点
        Vec3d v000 = new Vec3d(minX, minY, minZ);
        Vec3d v100 = new Vec3d(maxX, minY, minZ);
        Vec3d v010 = new Vec3d(minX, maxY, minZ);
        Vec3d v110 = new Vec3d(maxX, maxY, minZ);
        Vec3d v001 = new Vec3d(minX, minY, maxZ);
        Vec3d v101 = new Vec3d(maxX, minY, maxZ);
        Vec3d v011 = new Vec3d(minX, maxY, maxZ);
        Vec3d v111 = new Vec3d(maxX, maxY, maxZ);

        // 辅助函数：添加一个三角形（三个顶点，共用颜色，法线可选）
        // 这里为每个面指定正确的法线（用于光照，可选）
        // 注意：顶点顺序从外部看为逆时针 (CCW)

        // 1. 底面 (-Y) —— 从下方看逆时针: v000 -> v100 -> v101 -> v001? 实际需要拆成两个三角形
        // 底面四顶点: v000, v100, v101, v001 (顺序: 从下向上看，逆时针)
        // 三角形1: v000, v100, v101
        // 三角形2: v000, v101, v001
        addTriangle(matrix, bufferBuilder, v000, v100, v101, cachedRenderColor, 0, -1, 0);
        addTriangle(matrix, bufferBuilder, v000, v101, v001, cachedRenderColor, 0, -1, 0);

        // 2. 顶面 (+Y) —— 从上方看逆时针: v010, v011, v111, v110
        // 三角形1: v010, v011, v111
        // 三角形2: v010, v111, v110
        addTriangle(matrix, bufferBuilder, v010, v011, v111, cachedRenderColor, 0, 1, 0);
        addTriangle(matrix, bufferBuilder, v010, v111, v110, cachedRenderColor, 0, 1, 0);

        // 3. 前面 (+Z) —— 从前方看逆时针: v001, v101, v111, v011
        // 三角形1: v001, v101, v111
        // 三角形2: v001, v111, v011
        addTriangle(matrix, bufferBuilder, v001, v101, v111, cachedRenderColor, 0, 0, 1);
        addTriangle(matrix, bufferBuilder, v001, v111, v011, cachedRenderColor, 0, 0, 1);

        // 4. 后面 (-Z) —— 从后方看逆时针: v000, v010, v110, v100
        // 三角形1: v000, v010, v110
        // 三角形2: v000, v110, v100
        addTriangle(matrix, bufferBuilder, v000, v010, v110, cachedRenderColor, 0, 0, -1);
        addTriangle(matrix, bufferBuilder, v000, v110, v100, cachedRenderColor, 0, 0, -1);

        // 5. 左面 (-X) —— 从左方看逆时针: v000, v001, v011, v010
        // 三角形1: v000, v001, v011
        // 三角形2: v000, v011, v010
        addTriangle(matrix, bufferBuilder, v000, v001, v011, cachedRenderColor, -1, 0, 0);
        addTriangle(matrix, bufferBuilder, v000, v011, v010, cachedRenderColor, -1, 0, 0);

        // 6. 右面 (+X) —— 从右方看逆时针: v100, v110, v111, v101
        // 三角形1: v100, v110, v111
        // 三角形2: v100, v111, v101
        addTriangle(matrix, bufferBuilder, v100, v110, v111, cachedRenderColor, 1, 0, 0);
        addTriangle(matrix, bufferBuilder, v100, v111, v101, cachedRenderColor, 1, 0, 0);
    }

    /**
     * 添加一个三角形到 VertexConsumer，指定法线（用于光照）。
     * 顶点顺序为逆时针（CCW）。
     */
    private void addTriangle(
            MatrixStack.Entry matrix,
            VertexConsumer consumer,
            Vec3d v1,
            Vec3d v2,
            Vec3d v3,
            int color,
            float nx,
            float ny,
            float nz) {
        consumer.vertex(matrix, (float) v1.getX(), (float) v1.getY(), (float) v1.getZ())
                .color(color);

        consumer.vertex(matrix, (float) v2.getX(), (float) v2.getY(), (float) v2.getZ())
                .color(color);
        consumer.vertex(matrix, (float) v3.getX(), (float) v3.getY(), (float) v3.getZ())
                .color(color);
    }

    //    public void drawSolidBoxTriangle(MatrixStack matrixStack, VertexConsumer consumer,
    //                                     Vec3d from, Vec3d to, int color) {
    //        double minX = from.x, minY = from.y, minZ = from.z;
    //        double maxX = to.x,   maxY = to.y,   maxZ = to.z;
    //        MatrixStack.Entry entry = matrixStack.peek();
    //
    //        // 8 个顶点，索引顺序（下底面四个逆时针，上底面四个逆时针且与下面对应）
    //        Vec3d[] corners = {
    //            new Vec3d(minX, minY, minZ), // 0
    //            new Vec3d(maxX, minY, minZ), // 1
    //            new Vec3d(maxX, minY, maxZ), // 2
    //            new Vec3d(minX, minY, maxZ), // 3
    //            new Vec3d(minX, maxY, minZ), // 4
    //            new Vec3d(maxX, maxY, minZ), // 5
    //            new Vec3d(maxX, maxY, maxZ), // 6
    //            new Vec3d(minX, maxY, maxZ)  // 7
    //        };
    //
    //        // 每个面四个顶点的索引（逆时针从外部看），然后拆成两个三角形 (v0,v1,v2) 和 (v0,v2,v3)
    //        int[][] faces = {
    //            // 底面 (y=minY) : 0,1,2,3
    //            {0,1,2, 0,2,3},
    //            // 顶面 (y=maxY) : 4,5,6,7
    //            {4,5,6, 4,6,7},
    //            // 前面 (z=minZ) : 0,4,5,1
    //            {0,4,5, 0,5,1},
    //            // 后面 (z=maxZ) : 3,7,6,2
    //            {3,7,6, 3,6,2},
    //            // 左面 (x=minX) : 0,3,7,4
    //            {0,3,7, 0,7,4},
    //            // 右面 (x=maxX) : 1,5,6,2
    //            {1,5,6, 1,6,2}
    //        };
    //
    //        for (int[] tri : faces) {
    //            for (int idx : tri) {
    //                Vec3d v = corners[idx];
    //                consumer.vertex(entry, (float)v.x, (float)v.y, (float)v.z).color(color);
    //            }
    //        }
    //    }

    public void drawQuad(MatrixStack matrix4f, VertexConsumer bufferBuilder, Quad quad, ColorQuad colorQuad) {
        var matrix4 = matrix4f.peek();
        for (int idx = 0; idx < 4; idx++) {
            var vec3d = quad.get(idx);
            int color = colorQuad.get(idx);
            bufferBuilder
                    .vertex(matrix4, (float) vec3d.x, (float) vec3d.y, (float) vec3d.z)
                    .color(color);
        }
    }

    private static final float TEXT_HEIGHT = 9.0f;

    @Override
    public void drawTextCameraCoord(
            OrderedText orderedText,
            MatrixStack stack,
            Vec3d vec3d,
            int displayPositionFlag,
            Color color,
            TextDisplay displayInfo) {
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
                getVCP(),
                displayInfo.layerType(),
                displayInfo.backgroundColor(),
                displayInfo.light());
        getVCP().draw();
        stack.pop();
    }

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

    @Override
    public void drawItemCameraCoord(
            ItemStack itemStack, MatrixStack stack, Vec3d vec3d, ItemDisplayContext context, ItemDisplay displayInfo) {
        boolean bl = Vec3d.ZERO.equals(vec3d);
        if (!bl) {
            stack.translate(vec3d.x, vec3d.y, vec3d.z);
        }
        ItemRenderState state = new ItemRenderState();
        mc.getItemModelManager().clearAndUpdate(state, itemStack, context, mc.world, null, -999);
        state.render(stack, QUEUE, displayInfo.light(), displayInfo.overlay(), displayInfo.outlineColor());
        var vcp = getVCP();
        var outlineVcp = getOutlineVCP();
        for (var entry : QUEUE.getBatchingQueues().values()) {
            ITEM_RENDERER.render(entry, vcp, outlineVcp);
            // clear after render
            entry.clear();
        }
        if (!bl) {
            stack.translate(-vec3d.x, -vec3d.y, -vec3d.z);
        }
    }
}
