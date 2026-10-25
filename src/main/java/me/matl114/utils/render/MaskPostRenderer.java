package me.matl114.utils.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import me.matl114.plugins.MainPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class MaskPostRenderer {
    private static final RenderPipeline CAPTURE =
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(MainPlugin.identifier("pipeline/mask_capture"))
                    .withFragmentShader(MainPlugin.identifier("core/mask_capture"))
                    .withCull(false)
                    .withDepthWrite(false)
                    .build());
    private static final RenderPipeline SOLID =
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                    .withLocation(MainPlugin.identifier("pipeline/mask_solid"))
                    .withCull(false)
                    .withDepthWrite(false)
                    .build());
    private static final RenderPipeline FACE = postPipeline("pipeline/mask_face", "core/mask_face");
    private static final RenderPipeline OUTLINE = postPipeline("pipeline/mask_outline", "core/mask_outline");

    private final Map<Identifier, RenderLayer> texturedLayers = new HashMap<>();
    private SimpleFramebuffer framebuffer;
    private final OutputTarget target = new OutputTarget("model_mask", () -> framebuffer);
    private RenderLayer solidLayer;
    private RawProjectionMatrix projection;

    public record Quad(Vec3d[] vertices, float[] u, float[] v, Identifier texture) {}

    private static RenderPipeline postPipeline(String location, String fragment) {
        return RenderPipelines.register(RenderPipeline.builder()
                .withLocation(MainPlugin.identifier(location))
                .withVertexShader("core/screenquad")
                .withFragmentShader(MainPlugin.identifier(fragment))
                .withSampler("InSampler")
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                .build());
    }

    public void draw(
            List<Quad> quads, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, int faceColor, int outlineColor) {
        if (quads.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) return;
        ensureFramebuffer(width, height);

        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(framebuffer.getColorAttachment(), 0);
        RenderSystem.backupProjectionMatrix();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(modelViewMatrix);
        try {
            RenderSystem.setProjectionMatrix(projection.set(projectionMatrix), ProjectionType.PERSPECTIVE);
            try (BufferAllocator allocator = new BufferAllocator(4096)) {
                VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(allocator);
                for (Quad quad : quads) {
                    RenderLayer layer = quad.texture() == null ? solidLayer : layer(quad.texture());
                    VertexConsumer consumer = consumers.getBuffer(layer);
                    for (int i = 0; i < 4; i++) {
                        Vec3d vertex = quad.vertices()[i];
                        consumer.vertex((float) vertex.x, (float) vertex.y, (float) vertex.z);
                        if (quad.texture() != null) consumer.texture(quad.u()[i], quad.v()[i]);
                        consumer.color(-1);
                    }
                }
                consumers.draw();
            }
        } finally {
            modelView.popMatrix();
            RenderSystem.restoreProjectionMatrix();
        }

        if (ColorHelper.getAlpha(faceColor) > 0) composite(mc, FACE, faceColor);
        if (ColorHelper.getAlpha(outlineColor) > 0) composite(mc, OUTLINE, outlineColor);
    }

    private void ensureFramebuffer(int width, int height) {
        if (framebuffer == null) {
            framebuffer = new SimpleFramebuffer("Model mask", width, height, false);
            projection = new RawProjectionMatrix("model mask");
            solidLayer = RenderLayer.of(
                    "model_mask_solid",
                    RenderSetup.builder(SOLID).outputTarget(target).build());
        } else if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
            framebuffer.resize(width, height);
        }
    }

    private RenderLayer layer(Identifier texture) {
        return texturedLayers.computeIfAbsent(
                texture,
                id -> RenderLayer.of(
                        "model_mask/" + id,
                        RenderSetup.builder(CAPTURE)
                                .texture("Sampler0", id)
                                .outputTarget(target)
                                .build()));
    }

    private void composite(MinecraftClient mc, RenderPipeline pipeline, int color) {
        var slice = RenderSystem.getDynamicUniforms()
                .write(
                        new Matrix4f(),
                        new Vector4f(
                                ColorHelper.getRed(color) / 255.0F,
                                ColorHelper.getGreen(color) / 255.0F,
                                ColorHelper.getBlue(color) / 255.0F,
                                ColorHelper.getAlpha(color) / 255.0F),
                        new Vector3f(),
                        new Matrix4f());
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "Model mask composite",
                        mc.getFramebuffer().getColorAttachmentView(),
                        OptionalInt.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", slice);
            pass.bindTexture(
                    "InSampler",
                    framebuffer.getColorAttachmentView(),
                    RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
            pass.draw(0, 3);
        }
    }
}
