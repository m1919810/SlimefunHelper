package me.matl114.utils.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2ObjectSortedMaps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.matl114.accessors.access.RenderLayerAccess;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VDrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public abstract class EntityModelCollector implements RenderCollector<Entity> {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    protected final List<IndexEntry<Entity>> entities = new ArrayList<>();
    private final OrderedRenderCommandQueueImpl commandQueue = new OrderedRenderCommandQueueImpl();
    private final EntityVertexConsumerProvider vertexConsumers;
    private final RenderDispatcher renderDispatcher;

    protected EntityModelCollector() {
        this.vertexConsumers = new EntityVertexConsumerProvider(this::collectQuad);
        this.renderDispatcher = new RenderDispatcher(
                commandQueue,
                mc.getBlockRenderManager(),
                vertexConsumers,
                mc.getAtlasManager(),
                NoopOutlineVertexConsumerProvider.INSTANCE,
                NoopImmediateVertexConsumerProvider.INSTANCE,
                mc.textRenderer);
    }

    @Override
    public void submit(Entity entity, int color) {
        entities.add(new IndexEntry<>(color, entity));
    }

    @Override
    public void clear() {
        entities.clear();
        clearCollected();
    }

    @Override
    public final void render3D(MatrixStack matrices, float partialTick) {
        if (entities.isEmpty()) return;
        EntityRenderManager entityRenderManager = mc.getEntityRenderDispatcher();
        if (entityRenderManager == null) return;
        for (IndexEntry<Entity> entry : entities) {
            clearCollected();
            vertexConsumers.setColor(entry.index());
            submitEntity(entityRenderManager, entry.val(), partialTick);
            renderDispatcher.render();
            renderCollected(matrices);
        }
        clearCollected();
    }

    @Override
    public void render2D(VDrawContext vDrawContext, float partialTick) {}

    private void submitEntity(EntityRenderManager entityRenderManager, Entity entity, float partialTick) {
        EntityRenderState state = entityRenderManager.getAndUpdateRenderState(entity, partialTick);
        state.displayName = null;
        state.leashDatas = null;
        state.onFire = false;
        state.shadowPieces.clear();

        EntityRenderer renderer = entityRenderManager.getRenderer(state);
        Vec3d cameraPos = RenderUtils.getCameraPos();
        Vec3d positionOffset = renderer.getPositionOffset(state);
        MatrixStack entityMatrices = new MatrixStack();
        entityMatrices.push();
        Vec3d lerpedPos = entity.getLerpedPos(partialTick);
        entityMatrices.translate(
                lerpedPos.x - cameraPos.x + positionOffset.x,
                lerpedPos.y - cameraPos.y + positionOffset.y,
                lerpedPos.z - cameraPos.z + positionOffset.z);
        renderer.render(state, entityMatrices, commandQueue, mc.gameRenderer.getEntityRenderStates().cameraRenderState);
        entityMatrices.pop();
    }

    protected abstract void collectQuad(CapturedQuad quad, int color);

    protected abstract void renderCollected(MatrixStack matrices);

    protected abstract void clearCollected();

    protected record CapturedQuad(Vec3d[] vertices, UV uv, Identifier texture) {}

    @FunctionalInterface
    private interface QuadConsumer {
        void accept(CapturedQuad quad, int color);
    }

    private static final class EntityVertexConsumerProvider extends VertexConsumerProvider.Immediate {
        private final Map<RenderLayer, VertexConsumer> buffers = new HashMap<>();
        private final QuadConsumer quadConsumer;
        private int color;

        private EntityVertexConsumerProvider(QuadConsumer quadConsumer) {
            super(null, Object2ObjectSortedMaps.emptyMap());
            this.quadConsumer = quadConsumer;
        }

        private void setColor(int color) {
            this.color = color;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
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
            if (count == 4 && currentVertex == 3) {
                quadConsumer.accept(
                        new CapturedQuad(vertices.clone(), new UV(us[0], vs[0], us[2], vs[2]), texture),
                        colorSupplier.getAsInt());
                count = 0;
                currentVertex = 0;
            }
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            return this;
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

        @Override
        public VertexConsumer lineWidth(float width) {
            return this;
        }
    }

    private static final class NoopImmediateVertexConsumerProvider extends VertexConsumerProvider.Immediate {
        private static final NoopImmediateVertexConsumerProvider INSTANCE = new NoopImmediateVertexConsumerProvider();

        private NoopImmediateVertexConsumerProvider() {
            super(null, Object2ObjectSortedMaps.emptyMap());
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return NoopVertexConsumer.INSTANCE;
        }

        @Override
        public void draw() {}

        @Override
        public void drawCurrentLayer() {}

        @Override
        public void draw(RenderLayer layer) {}
    }

    private static final class NoopOutlineVertexConsumerProvider extends OutlineVertexConsumerProvider {
        private static final NoopOutlineVertexConsumerProvider INSTANCE = new NoopOutlineVertexConsumerProvider();

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return NoopVertexConsumer.INSTANCE;
        }

        @Override
        public void draw() {}
    }
}
