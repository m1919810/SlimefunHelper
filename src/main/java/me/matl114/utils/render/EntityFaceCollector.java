package me.matl114.utils.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.matl114.events.RenderListener;
import me.matl114.utils.RenderUtils;
import me.matl114.utils.collections.IndexEntry;
import me.matl114.versioned.api.VDrawContext;
import me.matl114.versioned.api.VRender;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector2d;

public final class EntityFaceCollector extends EntityModelCollector {
    private final List<IndexEntry<CapturedQuad>> quads = new ArrayList<>();

    public EntityFaceCollector() {
        super();
    }

    @Override
    protected void collectQuad(CapturedQuad capturedQuad, int color) {

        quads.add(new IndexEntry<>(color, capturedQuad));
    }

    @Override
    protected void renderCollected(MatrixStack matrices) {
        if (!quads.isEmpty()) {
            Map<Identifier, List<IndexEntry<CapturedQuad>>> texturedQuads = new LinkedHashMap<>();
            List<IndexEntry<CapturedQuad>> solidQuads = new ArrayList<>();
            for (IndexEntry<CapturedQuad> entry : quads) {
                if (entry.val().texture() == null) {
                    solidQuads.add(entry);
                } else {
                    texturedQuads
                            .computeIfAbsent(entry.val().texture(), ignored -> new ArrayList<>())
                            .add(entry);
                }
            }

            for (Map.Entry<Identifier, List<IndexEntry<CapturedQuad>>> group : texturedQuads.entrySet()) {
                VRender.getInstance().createEntityHighlightLayer(group.getKey(), (operation, vertexConsumer) -> {
                    for (IndexEntry<CapturedQuad> entry : group.getValue()) {
                        CapturedQuad capturedQuad = entry.val();
                        Vec3d[] vertices = capturedQuad.vertices();
                        Quad quad = new Quad(vertices[0], vertices[1], vertices[2], vertices[3]);
                        operation.drawTexturedQuad(
                                matrices, vertexConsumer, quad, capturedQuad.uv(), ColorQuad.of(entry.index()));
                    }
                });
            }

            if (!solidQuads.isEmpty()) {
                VRender.getInstance()
                        .createQuadsLayer(
                                (operation, vertexConsumer) -> {
                                    for (IndexEntry<CapturedQuad> entry : solidQuads) {
                                        CapturedQuad capturedQuad = entry.val();
                                        Vec3d[] vertices = capturedQuad.vertices();
                                        operation.drawQuad(
                                                matrices,
                                                vertexConsumer,
                                                new Quad(vertices[0], vertices[1], vertices[2], vertices[3]),
                                                ColorQuad.of(entry.index()));
                                    }
                                },
                                false);
            }
        }
    }

    @Override
    public void render2D(VDrawContext context, float partialTick) {
        if (entities.isEmpty()) return;
        Matrix4f camera = RenderListener.getWorldModelViewMatrix();
        Matrix4f projection = RenderListener.getWorldBasicProjectionMatrix();
        var projector = RenderUtils.createProjector(camera, projection, true);
        for (IndexEntry<Entity> entry : entities) {
            Box box = RenderUtils.getLerpedBox(entry.val(), partialTick);
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            boolean visible = true;
            for (double x : new double[] {box.minX, box.maxX}) {
                for (double y : new double[] {box.minY, box.maxY}) {
                    for (double z : new double[] {box.minZ, box.maxZ}) {
                        Vector2d screen = projector.apply(new Vec3d(x, y, z));
                        if (screen == null) {
                            visible = false;
                            break;
                        }
                        minX = Math.min(minX, screen.x);
                        minY = Math.min(minY, screen.y);
                        maxX = Math.max(maxX, screen.x);
                        maxY = Math.max(maxY, screen.y);
                    }
                    if (!visible) break;
                }
                if (!visible) break;
            }
            if (visible) {
                context.fill((int) minX, (int) minY, (int) maxX, (int) maxY, entry.index());
            }
        }
    }

    @Override
    protected void clearCollected() {
        quads.clear();
    }
}
