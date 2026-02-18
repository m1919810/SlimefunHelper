package me.matl114.versioned.impl;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.awt.*;
import java.util.List;
import java.util.function.Function;
import me.matl114.utils.RenderUtils;
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

public class Render_v1_21_11 implements VRender {

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
                    .build());

    public static final RenderPipeline DEBUG_LINES_STRIP =
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.tryParse("slimefunhelper:pipeline/debug_lines_strip"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.DrawMode.LINES)
                    .withCull(false)
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

    @Override
    public void drawStripLineVirtualCameraCoord(MatrixStack matrixStack, List<Vec3d> points, Color color) {
        if (points.size() < 2) return;
        VertexConsumerProvider.Immediate vcp = getVCP();
        VertexConsumer consumer = vcp.getBuffer(LINES);
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
    public void drawOutlinedBoxCameraCoord(MatrixStack matrix, Vec3d from, Vec3d to, Color color) {
        VertexConsumerProvider.Immediate vcp = getVCP();
        VertexConsumer consumer = vcp.getBuffer(LINES);
        drawOutlinedBox(matrix.peek(), consumer, from, to, color.getRGB());
        vcp.draw(LINES);
    }

    public void drawOutlinedBox(
            MatrixStack.Entry matrix4f, VertexConsumer bufferBuilder, Vec3d from, Vec3d to, int cachedRenderColor) {
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

    @Override
    public void drawSolidBoxCameraCoord(MatrixStack matrix, Vec3d from, Vec3d to, Color color) {
        VertexConsumerProvider.Immediate vcp = getVCP();
        VertexConsumer consumer = vcp.getBuffer(QUADS);
        drawSolidBox(matrix.peek(), consumer, from, to, color.getRGB());
        vcp.draw(QUADS);
    }

    public void drawSolidBox(
            MatrixStack.Entry matrix, VertexConsumer bufferBuilder, Vec3d from, Vec3d to, int cachedRenderColor) {
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
    public void drawQuadCameraCoord(MatrixStack matrix4f, Quad quad, ColorQuad colorQuad) {
        VertexConsumerProvider.Immediate vcp = getVCP();
        VertexConsumer bufferBuilder = vcp.getBuffer(QUADS_NO_CULL);
        var matrix4 = matrix4f.peek();
        for (int idx = 0; idx < 4; idx++) {
            var vec3d = quad.get(idx);
            int color = colorQuad.get(idx);
            bufferBuilder
                    .vertex(matrix4, (float) vec3d.x, (float) vec3d.y, (float) vec3d.z)
                    .color(color);
        }
        vcp.draw(QUADS);
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

    @Override
    public void drawTexturedQuadCameraCoord(Identifier path, MatrixStack stack, Quad quad, UV uv, ColorQuad colorQuad) {
        var vcp = getVCP();
        var layer = GUI_TEXTURE_3D_FACTORY.apply(path);
        var vertex = vcp.getBuffer(layer);
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
        vcp.draw(layer);
    }

    @Override
    public void drawSpriteQuadCameraCoord(Sprite sprite, MatrixStack stack, Quad quad, UV uv, ColorQuad colorQuad) {
        var vcp = getVCP();
        var layer = GUI_SPRITE_TEXTURE_3D_FACTORY.apply(sprite.getAtlasId());
        var vertex = RenderUtils.getSpriteVertexConsumer(vcp.getBuffer(layer), sprite);
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
        vcp.draw(layer);
    }

    @Override
    public void drawGuiQuadCameraCoord(MatrixStack stack, Quad quad, ColorQuad colorQuad) {
        var vcp = getVCP();
        var vertex = vcp.getBuffer(GUI_3D);
        var entry = stack.peek();
        for (var i = 0; i < 4; ++i) {
            Vec3d vec3d = quad.get(i);
            int color = colorQuad.get(i);
            vertex.vertex(entry, (float) vec3d.x, (float) vec3d.y, (float) vec3d.z)
                    .color(color);
        }
        vcp.draw(GUI_3D);
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
