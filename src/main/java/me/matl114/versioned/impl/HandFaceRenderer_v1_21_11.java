package me.matl114.versioned.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.matl114.accessors.access.RenderLayerAccess;
import me.matl114.events.RenderListener;
import me.matl114.utils.render.MaskPostRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class HandFaceRenderer_v1_21_11 {
    private final List<TexturedQuad> quads = new ArrayList<>();
    private final List<ColoredQuad> pending = new ArrayList<>();
    private final MaskPostRenderer maskRenderer = new MaskPostRenderer();
    private boolean collecting;

    public HandFaceRenderer_v1_21_11() {}

    public void begin() {
        quads.clear();
        collecting = true;
    }

    public void submitArm(ModelPart part, MatrixStack matrices, Identifier texture, int light) {
        if (!collecting || texture == null) return;
        part.render(matrices, new QuadCapture(texture), light, 0, -1);
    }

    public void submitItem(MatrixStack matrices, List<BakedQuad> bakedQuads, RenderLayer layer, int color) {
        if (!collecting || bakedQuads.isEmpty()) return;
        Identifier texture = RenderLayerAccess.of(layer).getRenderTexture().orElse(null);
        Matrix4f transform = new Matrix4f(matrices.peek().getPositionMatrix());
        for (BakedQuad baked : bakedQuads) {
            Vec3d[] vertices = new Vec3d[4];
            float[] us = new float[4];
            float[] vs = new float[4];
            for (int i = 0; i < 4; i++) {
                Vector3f pos = transform.transformPosition(baked.getPosition(i), new Vector3f());
                vertices[i] = new Vec3d(pos.x, pos.y, pos.z);
                long uv = baked.getTexcoords(i);
                us[i] = Vector2f.getX(uv);
                vs[i] = Vector2f.getY(uv);
            }
            quads.add(new TexturedQuad(texture, baked.sprite(), vertices, us, vs));
        }
    }

    public void render(int color) {
        finish(color);
    }

    public void renderDeferred(OrderedRenderCommandQueue queue, int color) {
        finish(color);
    }

    private void finish(int color) {
        collecting = false;
        for (TexturedQuad quad : quads) {
            pending.add(new ColoredQuad(quad, color));
        }
        quads.clear();
    }

    public void clear() {
        collecting = false;
        quads.clear();
        pending.clear();
    }

    private record TexturedQuad(Identifier texture, Sprite sprite, Vec3d[] vertices, float[] us, float[] vs) {}

    private record ColoredQuad(TexturedQuad quad, int color) {}

    public void render2D(int boundaryColor) {
        if (pending.isEmpty()) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return;
            int framebufferWidth = mc.getWindow().getFramebufferWidth();
            int framebufferHeight = mc.getWindow().getFramebufferHeight();
            if (framebufferWidth <= 0 || framebufferHeight <= 0) return;

            float fov = RenderListener.getHandFov();
            if (!Float.isFinite(fov) || fov <= 0.0F) return;
            Matrix4f projection = new Matrix4f()
                    .perspective(
                            (float) Math.toRadians(fov), (float) framebufferWidth / framebufferHeight, 0.05F, 100.0F);
            Matrix4f modelView = RenderListener.getWorldModelViewMatrix();
            Map<Integer, List<MaskPostRenderer.Quad>> masks = new LinkedHashMap<>();
            for (ColoredQuad colored : pending) {
                TexturedQuad quad = colored.quad();
                Identifier texture = quad.texture() != null
                        ? quad.texture()
                        : quad.sprite() != null ? quad.sprite().getAtlasId() : null;
                masks.computeIfAbsent(colored.color(), ignored -> new ArrayList<>())
                        .add(new MaskPostRenderer.Quad(quad.vertices(), quad.us(), quad.vs(), texture));
            }
            for (Map.Entry<Integer, List<MaskPostRenderer.Quad>> entry : masks.entrySet()) {
                maskRenderer.draw(entry.getValue(), modelView, projection, entry.getKey(), boundaryColor);
            }
        } finally {
            pending.clear();
        }
    }

    private final class QuadCapture implements VertexConsumer {
        private final Identifier texture;
        private final Vec3d[] vertices = new Vec3d[4];
        private final float[] us = new float[4];
        private final float[] vs = new float[4];
        private int count;
        private int current;

        private QuadCapture(Identifier texture) {
            this.texture = texture;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            current = count;
            vertices[count++] = new Vec3d(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer color(int argb) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            us[current] = u;
            vs[current] = v;
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            if (count == 4 && current == 3) {
                quads.add(new TexturedQuad(texture, null, vertices.clone(), us.clone(), vs.clone()));
                count = 0;
                current = 0;
            }
            return this;
        }

        @Override
        public VertexConsumer vertex(MatrixStack.Entry matrix, float x, float y, float z) {
            return vertex(matrix.getPositionMatrix(), x, y, z);
        }

        @Override
        public VertexConsumer vertex(Matrix4fc matrix, float x, float y, float z) {
            Vector3f pos = matrix.transformPosition(x, y, z, new Vector3f());
            return vertex(pos.x, pos.y, pos.z);
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            return this;
        }
    }
}
