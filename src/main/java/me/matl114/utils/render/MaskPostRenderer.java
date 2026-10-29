package me.matl114.utils.render;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.matl114.plugins.MainPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public final class MaskPostRenderer {
    private static final ShaderProgramKey CAPTURE = new ShaderProgramKey(
            MainPlugin.identifier("core/mask_capture"), VertexFormats.POSITION_TEXTURE_COLOR, Defines.EMPTY);
    private static final ShaderProgramKey FACE = new ShaderProgramKey(
            MainPlugin.identifier("core/mask_face"), VertexFormats.POSITION_TEXTURE, Defines.EMPTY);
    private static final ShaderProgramKey OUTLINE = new ShaderProgramKey(
            MainPlugin.identifier("core/mask_outline"), VertexFormats.POSITION_TEXTURE, Defines.EMPTY);

    private SimpleFramebuffer framebuffer;

    public record Quad(Vec3d[] vertices, float[] u, float[] v, Identifier texture) {}

    public void draw(
            List<Quad> quads, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, int faceColor, int outlineColor) {
        if (quads.isEmpty() || (ColorHelper.getAlpha(faceColor) == 0 && ColorHelper.getAlpha(outlineColor) == 0))
            return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) return;
        ensureFramebuffer(width, height);

        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] scissorBox = new int[4];
        if (scissor) GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        int texture = RenderSystem.getShaderTexture(0);
        ShaderProgram shader = RenderSystem.getShader();
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        var modelView = RenderSystem.getModelViewStack();
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix();
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableScissor();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setProjectionMatrix(projectionMatrix, ProjectionType.PERSPECTIVE);
            modelView.set(modelViewMatrix);
            framebuffer.clear();
            framebuffer.beginWrite(true);
            capture(quads);

            mc.getFramebuffer().beginWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f(), ProjectionType.ORTHOGRAPHIC);
            modelView.identity();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderTexture(0, framebuffer.getColorAttachment());
            if (ColorHelper.getAlpha(faceColor) > 0) composite(FACE, faceColor);
            if (ColorHelper.getAlpha(outlineColor) > 0) composite(OUTLINE, outlineColor);
        } finally {
            mc.getFramebuffer().beginWrite(true);
            modelView.popMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            RenderSystem.setShaderTexture(0, texture);
            if (shader != null) RenderSystem.setShader(shader);
            else RenderSystem.clearShader();
            if (depth) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(depthWrite);
            if (cull) RenderSystem.enableCull();
            else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
            if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        }
    }

    private void ensureFramebuffer(int width, int height) {
        if (framebuffer == null) {
            framebuffer = new SimpleFramebuffer(width, height, false);
            framebuffer.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        } else if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
            framebuffer.resize(width, height);
        }
    }

    private void capture(List<Quad> quads) {
        Map<Identifier, List<Quad>> groups = new LinkedHashMap<>();
        for (Quad quad : quads)
            groups.computeIfAbsent(quad.texture(), ignored -> new ArrayList<>()).add(quad);
        for (Map.Entry<Identifier, List<Quad>> group : groups.entrySet()) {
            boolean textured = group.getKey() != null;
            RenderSystem.setShader(textured ? CAPTURE : ShaderProgramKeys.POSITION_COLOR);
            if (textured) RenderSystem.setShaderTexture(0, group.getKey());
            BufferBuilder buffer = Tessellator.getInstance()
                    .begin(
                            VertexFormat.DrawMode.QUADS,
                            textured ? VertexFormats.POSITION_TEXTURE_COLOR : VertexFormats.POSITION_COLOR);
            for (Quad quad : group.getValue()) {
                for (int i = 0; i < 4; i++) {
                    Vec3d vertex = quad.vertices()[i];
                    var consumer = buffer.vertex((float) vertex.x, (float) vertex.y, (float) vertex.z);
                    if (textured) consumer.texture(quad.u()[i], quad.v()[i]);
                    consumer.color(-1);
                }
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
    }

    private void composite(ShaderProgramKey program, int color) {
        RenderSystem.setShader(program);
        RenderSystem.setShaderColor(
                ColorHelper.getRed(color) / 255.0F,
                ColorHelper.getGreen(color) / 255.0F,
                ColorHelper.getBlue(color) / 255.0F,
                ColorHelper.getAlpha(color) / 255.0F);
        BufferBuilder buffer =
                Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(-1.0F, -1.0F, 0.0F).texture(0.0F, 0.0F);
        buffer.vertex(1.0F, -1.0F, 0.0F).texture(1.0F, 0.0F);
        buffer.vertex(1.0F, 1.0F, 0.0F).texture(1.0F, 1.0F);
        buffer.vertex(-1.0F, 1.0F, 0.0F).texture(0.0F, 1.0F);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
