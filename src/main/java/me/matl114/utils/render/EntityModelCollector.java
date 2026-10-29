package me.matl114.utils.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.matl114.accessors.access.RenderLayerAccess;
import me.matl114.events.RenderListener;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public abstract class EntityModelCollector implements RenderCollector<Entity> {
    protected final List<IndexEntry<Entity>> entities = new ArrayList<>();
    private final List<IndexEntry<CapturedQuad>> capturedQuads = new ArrayList<>();
    protected final MaskPostRenderer maskRenderer = new MaskPostRenderer();
    private final EntityVertexConsumerProvider vertexConsumers;

    protected EntityModelCollector() {
        this.vertexConsumers = new EntityVertexConsumerProvider(this::captureQuad);
    }

    @Override
    public void submit(Entity entity, int color) {
        entities.add(new IndexEntry<>(color, entity));
    }

    @Override
    public void clear() {
        entities.clear();
        clearCaptured();
    }

    public static void captureItemQuads(
            VertexConsumerProvider provider, MatrixStack matrices, BakedModel model, RenderLayer layer) {
        if (provider instanceof EntityVertexConsumerProvider collector) {
            collector.captureItem(matrices, model, layer);
        }
    }

    public static void finishItemQuads(VertexConsumerProvider provider) {
        if (provider instanceof EntityVertexConsumerProvider collector) {
            collector.finishItem();
        }
    }

    @Override
    public final void render3D(MatrixStack matrices, float partialTick) {
        if (entities.isEmpty()) return;
        EntityRenderDispatcher entityRenderDispatcher =
                MinecraftClient.getInstance().getEntityRenderDispatcher();
        if (entityRenderDispatcher == null) return;

        for (IndexEntry<Entity> entry : entities) {
            clearCaptured();
            try {
                try {
                    vertexConsumers.setColor(entry.index());
                    submitEntity(entityRenderDispatcher, entry.val(), partialTick, entry.index());
                } finally {
                    vertexConsumers.finishCapture();
                }
                renderCollected(matrices);
            } finally {
                clearCaptured();
            }
        }
    }

    @Override
    public void render2D(VDrawContext vDrawContext, float partialTick) {
        renderQuads2D(
                partialTick,
                (quads, camera, projection, color) -> maskRenderer.draw(quads, camera, projection, color, 0));
    }

    protected final void renderQuads2D(float partialTick, MaskConsumer consumer) {
        if (entities.isEmpty()) return;

        EntityRenderDispatcher entityRenderDispatcher =
                MinecraftClient.getInstance().getEntityRenderDispatcher();
        if (entityRenderDispatcher == null) return;
        Matrix4f camera = RenderListener.getWorldModelViewMatrix();
        Matrix4f projection = RenderListener.getWorldBasicProjectionMatrix();
        for (IndexEntry<Entity> entry : entities) {
            clearCaptured();
            try {
                try {
                    vertexConsumers.setColor(entry.index());
                    submitEntity(entityRenderDispatcher, entry.val(), partialTick, entry.index());
                } finally {
                    vertexConsumers.finishCapture();
                }

                List<MaskPostRenderer.Quad> quads = new ArrayList<>();
                for (IndexEntry<CapturedQuad> captured : capturedQuads) {
                    CapturedQuad quad = captured.val();
                    if (quad.vertices().length != 4) continue;
                    Identifier texture = quad.texture();
                    if (texture == null && quad.sprite() != null) {
                        texture = quad.sprite().getAtlasId();
                    }
                    quads.add(new MaskPostRenderer.Quad(quad.vertices(), quad.us(), quad.vs(), texture));
                }
                consumer.accept(quads, camera, projection, entry.index());
            } finally {
                clearCaptured();
            }
        }
    }

    @FunctionalInterface
    protected interface MaskConsumer {
        void accept(List<MaskPostRenderer.Quad> quads, Matrix4f camera, Matrix4f projection, int color);
    }

    private void captureQuad(CapturedQuad quad, int color) {
        capturedQuads.add(new IndexEntry<>(color, quad));
        collectQuad(quad, color);
    }

    private void clearCaptured() {
        capturedQuads.clear();
        clearCollected();
    }

    private void submitEntity(
            EntityRenderDispatcher entityRenderDispatcher, Entity entity, float partialTick, int color) {
        renderEntity(entityRenderDispatcher.getRenderer(entity), entity, partialTick, color);
    }

    private <T extends Entity> void renderEntity(EntityRenderer<? super T> renderer, T entity, float partialTick) {
        S state = renderer.getAndUpdateRenderState(entity, partialTick);
        state.displayName = null;
        state.leashData = null;
        state.onFire = false;
        configureRenderState(state, color);

        Vec3d cameraPos = RenderUtils.getCameraPos();
        Vec3d positionOffset = renderer.getPositionOffset(entity, partialTick);
        MatrixStack entityMatrices = new MatrixStack();
        entityMatrices.push();
        Vec3d lerpedPos = new Vec3d(
                MathHelper.lerp(partialTick, entity.lastRenderX, entity.getX()),
                MathHelper.lerp(partialTick, entity.lastRenderY, entity.getY()),
                MathHelper.lerp(partialTick, entity.lastRenderZ, entity.getZ()));
        entityMatrices.translate(
                lerpedPos.x - cameraPos.x + positionOffset.x,
                lerpedPos.y - cameraPos.y + positionOffset.y,
                lerpedPos.z - cameraPos.z + positionOffset.z);
        float yaw = MathHelper.lerp(partialTick, entity.prevYaw, entity.getYaw());
        renderer.render(
                entity, yaw, partialTick, entityMatrices, vertexConsumers, renderer.getLight(entity, partialTick));
        entityMatrices.pop();
        try {
            renderer.render(state, entityMatrices, vertexConsumers, renderer.getLight(entity, partialTick));
        } finally {
            entityMatrices.pop();
        }
    }

    protected abstract void collectQuad(CapturedQuad quad, int color);

    protected abstract void renderCollected(MatrixStack matrices);

    protected abstract void clearCollected();

    protected void configureRenderState(EntityRenderState state, int color) {}

    protected record CapturedQuad(Vec3d[] vertices, UV uv, Identifier texture, Sprite sprite, float[] us, float[] vs) {}

    @FunctionalInterface
    private interface QuadConsumer {
        void accept(CapturedQuad quad, int color);
    }

    private static final class EntityVertexConsumerProvider extends VertexConsumerProvider.Immediate {
        private final Map<RenderLayer, VertexConsumer> buffers = new HashMap<>();
        private final QuadConsumer quadConsumer;
        private int color;
        private int itemCaptureDepth;

        private EntityVertexConsumerProvider(QuadConsumer quadConsumer) {
            super(null, new LinkedHashMap<>());
            this.quadConsumer = quadConsumer;
        }

        private void setColor(int color) {
            this.color = color;
        }

        private void finishCapture() {
            itemCaptureDepth = 0;
            for (VertexConsumer buffer : buffers.values()) {
                ((EntityVertexConsumer) buffer).finishCapture();
            }
        }

        private void captureItem(MatrixStack matrices, BakedModel model, RenderLayer layer) {
            itemCaptureDepth++;
            try {
                Random random = Random.create();
                for (Direction direction : Direction.values()) {
                    random.setSeed(42L);
                    captureItem(matrices, model.getQuads(null, direction, random), layer);
                }
                random.setSeed(42L);
                captureItem(matrices, model.getQuads(null, null, random), layer);
            } catch (RuntimeException | Error exception) {
                itemCaptureDepth--;
                throw exception;
            }
        }

        private void captureItem(MatrixStack matrices, List<BakedQuad> quads, RenderLayer layer) {
            Identifier texture = RenderLayerAccess.of(layer).getRenderTexture().orElse(null);
            Matrix4f transform = new Matrix4f(matrices.peek().getPositionMatrix());
            for (BakedQuad baked : quads) {
                int[] data = baked.getVertexData();
                if (data.length % 4 != 0) continue;
                int stride = data.length / 4;
                if (stride < 6) continue;

                Vec3d[] vertices = new Vec3d[4];
                float[] us = new float[4];
                float[] vs = new float[4];
                for (int i = 0; i < 4; i++) {
                    int offset = i * stride;
                    Vector3f position = transform.transformPosition(
                            Float.intBitsToFloat(data[offset]),
                            Float.intBitsToFloat(data[offset + 1]),
                            Float.intBitsToFloat(data[offset + 2]),
                            new Vector3f());
                    vertices[i] = new Vec3d(position.x, position.y, position.z);
                    us[i] = Float.intBitsToFloat(data[offset + 4]);
                    vs[i] = Float.intBitsToFloat(data[offset + 5]);
                }
                quadConsumer.accept(
                        new CapturedQuad(vertices, new UV(us[0], vs[0], us[2], vs[2]), texture, baked.sprite, us, vs),
                        color);
            }
        }

        private void finishItem() {
            if (itemCaptureDepth > 0) {
                itemCaptureDepth--;
            }
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            if (itemCaptureDepth > 0 || layer.getDrawMode() != VertexFormat.DrawMode.QUADS) {
                return NoopVertexConsumer.INSTANCE;
            }
            return buffers.computeIfAbsent(
                    layer,
                    ignored -> new EntityVertexConsumer(
                            quadConsumer,
                            () -> color,
                            RenderLayerAccess.of(layer).getRenderTexture().orElse(null)));
        }

        @Override
        public void draw() {}

        @Override
        public void drawCurrentLayer() {}

        @Override
        public void draw(RenderLayer layer) {}
    }

    private static final class EntityVertexConsumer implements VertexConsumer {
        private final QuadConsumer quadConsumer;
        private final java.util.function.IntSupplier colorSupplier;
        private final Vec3d[] vertices = new Vec3d[4];
        private final float[] us = new float[4];
        private final float[] vs = new float[4];
        private int count;
        private int currentVertex;
        private final Identifier texture;

        private EntityVertexConsumer(
                QuadConsumer quadConsumer, java.util.function.IntSupplier colorSupplier, Identifier texture) {
            this.quadConsumer = quadConsumer;
            this.colorSupplier = colorSupplier;
            this.texture = texture;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            if (count == vertices.length) {
                count = 0;
            }
            if (count == 4) finishCapture();
            currentVertex = count;
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
            us[currentVertex] = u;
            vs[currentVertex] = v;
            if (count == 4) finishCapture();
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
            if (count == 4) finishCapture();
            return this;
        }

        private void finishCapture() {
            if (count == 4) {
                quadConsumer.accept(
                        new CapturedQuad(
                                vertices.clone(),
                                new UV(us[0], vs[0], us[2], vs[2]),
                                texture,
                                null,
                                us.clone(),
                                vs.clone()),
                        colorSupplier.getAsInt());
            }
            count = 0;
            currentVertex = 0;
        }
    }

    private static final class NoopVertexConsumer implements VertexConsumer {
        private static final NoopVertexConsumer INSTANCE = new NoopVertexConsumer();

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
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
            return this;
        }
    }
}
