package me.matl114.versioned.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.matl114.accessors.access.RenderLayerAccess;
import me.matl114.utils.render.ColorQuad;
import me.matl114.utils.render.Quad;
import me.matl114.utils.render.UV;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class HandFaceRenderer_v1_21_11 {
    private final List<TexturedQuad> quads = new ArrayList<>();
    private boolean collecting;

    public HandFaceRenderer_v1_21_11() {}

    public void begin() {
        quads.clear();
        collecting = true;
    }

    public void submitArm(ModelPart part, MatrixStack matrices, Identifier texture, int light) {
        if (!collecting || texture == null) return;
        part.render(matrices, new QuadCapture(texture, -1), light, 0, -1);
    }

    public void submitItem(MatrixStack matrices, List<BakedQuad> bakedQuads, RenderLayer layer, int color) {
        if (!collecting || bakedQuads.isEmpty()) return;
        Identifier texture = RenderLayerAccess.of(layer).getRenderTexture().orElse(null);
        if (texture == null) return;
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
            quads.add(new TexturedQuad(texture, vertices, us, vs, color));
        }
    }

    public void render(int color) {
        collecting = false;
        if (quads.isEmpty()) return;

        Map<Identifier, List<TexturedQuad>> byTexture = new LinkedHashMap<>();
        for (TexturedQuad quad : quads) {
            byTexture
                    .computeIfAbsent(quad.texture(), ignored -> new ArrayList<>())
                    .add(quad.withColor(color));
        }
        MatrixStack identity = new MatrixStack();
        for (Map.Entry<Identifier, List<TexturedQuad>> group : byTexture.entrySet()) {
            VRender.getInstance().createEntityHighlightLayer(group.getKey(), (operation, vertexConsumer) -> {
                for (TexturedQuad captured : group.getValue()) {
                    operation.drawTexturedQuad(
                            identity,
                            vertexConsumer,
                            new Quad(
                                    captured.vertices()[0],
                                    captured.vertices()[1],
                                    captured.vertices()[2],
                                    captured.vertices()[3]),
                            new UV(captured.us()[0], captured.vs()[0], captured.us()[2], captured.vs()[2]),
                            ColorQuad.of(captured.color()));
                }
            });
        }
        quads.clear();
    }

    public void renderDeferred(OrderedRenderCommandQueue queue, int color) {
        collecting = false;
        if (quads.isEmpty()) return;

        Map<Identifier, List<TexturedQuad>> byTexture = new LinkedHashMap<>();
        for (TexturedQuad quad : quads) {
            byTexture
                    .computeIfAbsent(quad.texture(), ignored -> new ArrayList<>())
                    .add(quad.withColor(color));
        }
        for (Map.Entry<Identifier, List<TexturedQuad>> group : byTexture.entrySet()) {
            List<TexturedQuad> texturedQuads = List.copyOf(group.getValue());
            queue.submitCustom(
                    new MatrixStack(),
                    Render_v1_21_11.ENTITY_HIGHLIGHT_FACTORY.apply(group.getKey()),
                    (entry, vertexConsumer) -> drawTexturedQuads(texturedQuads, vertexConsumer));
        }
        quads.clear();
    }

    private void drawTexturedQuads(List<TexturedQuad> texturedQuads, VertexConsumer vertexConsumer) {
        var operation = (VRender.WrapRenderOperation) VRender.getInstance();
        MatrixStack identity = new MatrixStack();
        for (TexturedQuad captured : texturedQuads) {
            operation.drawTexturedQuad(
                    identity,
                    vertexConsumer,
                    new Quad(
                            captured.vertices()[0],
                            captured.vertices()[1],
                            captured.vertices()[2],
                            captured.vertices()[3]),
                    new UV(captured.us()[0], captured.vs()[0], captured.us()[2], captured.vs()[2]),
                    ColorQuad.of(captured.color()));
        }
    }

    public void clear() {
        collecting = false;
        quads.clear();
    }

    private record TexturedQuad(Identifier texture, Vec3d[] vertices, float[] us, float[] vs, int color) {
        private TexturedQuad withColor(int newColor) {
            return new TexturedQuad(texture, vertices, us, vs, newColor);
        }
    }

    private final class QuadCapture implements VertexConsumer {
        private final Identifier texture;
        private final int color;
        private final Vec3d[] vertices = new Vec3d[4];
        private final float[] us = new float[4];
        private final float[] vs = new float[4];
        private int count;
        private int current;

        private QuadCapture(Identifier texture, int color) {
            this.texture = texture;
            this.color = color;
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
                quads.add(new TexturedQuad(texture, vertices.clone(), us.clone(), vs.clone(), color));
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
