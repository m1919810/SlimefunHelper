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
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public abstract class EntityModelCollector implements RenderCollector<Entity> {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    protected final List<IndexEntry<Entity>> entities = new ArrayList<>();
    private final EntityVertexConsumerProvider vertexConsumers;

    protected EntityModelCollector() {
        this.vertexConsumers = new EntityVertexConsumerProvider(this::collectQuad);
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
            VertexConsumerProvider provider, MatrixStack matrices, List<BakedQuad> quads, RenderLayer layer) {
        if (provider instanceof EntityVertexConsumerProvider collector) {
            collector.captureItem(matrices, quads, layer);
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
        EntityRenderDispatcher entityRenderDispatcher = mc.getEntityRenderDispatcher();
        if (entityRenderDispatcher == null) return;
        for (IndexEntry<Entity> entry : entities) {
            clearCaptured();
            vertexConsumers.setColor(entry.index());
            submitEntity(entityRenderDispatcher, entry.val(), partialTick);
            renderCollected(matrices);
        }
        clearCaptured();
    }

    @Override
    public void render2D(VDrawContext vDrawContext, float partialTick) {
        renderQuads2D(
                partialTick,
                (quads, camera, projection, color) -> maskRenderer.draw(quads, camera, projection, color, 0));
    }

    protected final void renderQuads2D(float partialTick, MaskConsumer consumer) {
        if (entities.isEmpty()) return;

        EntityRenderManager entityRenderManager = mc.getEntityRenderDispatcher();
        if (entityRenderManager == null) return;
        Matrix4f camera = RenderListener.getWorldModelViewMatrix();
        Matrix4f projection = RenderListener.getWorldBasicProjectionMatrix();
        for (IndexEntry<Entity> entry : entities) {
            clearCaptured();
            try {
                vertexConsumers.setColor(entry.index());
                submitEntity(entityRenderManager, entry.val(), partialTick, entry.index());
                clearTextCommands();
                renderDispatcher.render();
                vertexConsumers.finishCapture();

                List<MaskPostRenderer.Quad> quads = new ArrayList<>();
                for (IndexEntry<CapturedQuad> captured : capturedQuads) {
                    Vec3d[] vertices = captured.val().vertices();
                    if (vertices.length == 4) {
                        Identifier texture = captured.val().texture();
                        if (texture == null && captured.val().sprite() != null) {
                            texture = captured.val().sprite().getAtlasId();
                        }
                        quads.add(new MaskPostRenderer.Quad(
                                vertices, captured.val().us(), captured.val().vs(), texture));
                    }
                }
                consumer.accept(quads, camera, projection, entry.index());
            } finally {
                clearCaptured();
            }
        }
    }

    private void submitEntity(EntityRenderDispatcher entityRenderDispatcher, Entity entity, float partialTick) {
        renderEntity(entityRenderDispatcher.getRenderer(entity), entity, partialTick);
    }

    private <T extends Entity> void renderEntity(EntityRenderer<? super T> renderer, T entity, float partialTick) {
        S state = renderer.getAndUpdateRenderState(entity, partialTick);
        state.displayName = null;
        state.leashData = null;
        state.onFire = false;
        state.shadowPieces.clear();
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
            for (VertexConsumer buffer : buffers.values()) {
                ((EntityVertexConsumer) buffer).finishCapture();
            }
        }

        private void captureItem(MatrixStack matrices, List<BakedQuad> quads, RenderLayer layer) {
            itemCaptureDepth++;
            Identifier texture = RenderLayerAccess.of(layer).getRenderTexture().orElse(null);
            Matrix4f transform = matrices.peek().getPositionMatrix();
            for (BakedQuad baked : quads) {
                Vec3d[] vertices = new Vec3d[4];
                float[] us = new float[4];
                float[] vs = new float[4];
                for (int i = 0; i < 4; i++) {
                    Vector3f position = transform.transformPosition(baked.getPosition(i), new Vector3f());
                    vertices[i] = new Vec3d(position.x, position.y, position.z);
                    long packedUV = baked.getTexcoords(i);
                    us[i] = Vector2f.getX(packedUV);
                    vs[i] = Vector2f.getY(packedUV);
                }
                quadConsumer.accept(
                        new CapturedQuad(vertices, new UV(us[0], vs[0], us[2], vs[2]), texture, baked.sprite(), us, vs),
                        color);
            }
        }

        private void finishItem() {
            itemCaptureDepth--;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            if (itemCaptureDepth > 0) return NoopVertexConsumer.INSTANCE;
            if (layer.getDrawMode() != VertexFormat.DrawMode.QUADS) return NoopVertexConsumer.INSTANCE;
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
        private Identifier texture;

        private EntityVertexConsumer(
                QuadConsumer quadConsumer, java.util.function.IntSupplier colorSupplier, Identifier identifier) {
            this.quadConsumer = quadConsumer;
            this.colorSupplier = colorSupplier;
            this.texture = identifier;
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
